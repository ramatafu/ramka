package com.ramka.domain.relay

/** Насколько ошибка говорит о неисправности самого релея. */
enum class RelayFailureKind {
    /** Релей исправен (получатель не в сети, регистрацию заняло другое устройство): штрафа нет. */
    NEUTRAL,

    /** Сеть, TLS, тайм-аут, перегрузка, внутренняя ошибка: релей может ожить сам, штраф нарастает. */
    INFRASTRUCTURE,

    /** Неверный токен, нет TLS 1.3: повтор не поможет без правки настроек, сразу длинная пауза. */
    CONFIGURATION
}

/** Параметры [RelayHealth]; значения по умолчанию — из плана шага 2. */
data class RelayHealthConfig(
    /** Первая пауза после ошибки инфраструктуры; дальше удваивается (30 с → 60 с → 120 с → …). */
    val cooldownBaseMillis: Long = 30_000,
    /** Потолок паузы (5 минут). */
    val cooldownCapMillis: Long = 5 * 60_000,
    /** Пауза после ошибки настройки (AUTH, TLS13_UNSUPPORTED): сразу максимальная. */
    val configCooldownMillis: Long = 5 * 60_000,
    /** Сколько «залипание» держит релей первым для контакта после успешной доставки (~10 минут). */
    val stickyMillis: Long = 10 * 60_000
)

/**
 * Метрики одного релея за время работы приложения (в памяти, при перезапуске обнуляются).
 *
 * @property successCount доставок, завершённых через этот релей.
 * @property failureCount обращений, закончившихся ошибкой релея (нейтральные ошибки не считаются).
 * @property fallbackCount доставок, которые прошли через этот релей, хотя первым в очереди был другой
 *   (то есть сработал запасной вариант).
 * @property avgLatencyMs среднее время доставки через релей (от начала попытки на нём до конца отправки);
 *   null, пока не было ни одной доставки.
 * @property consecutiveFailures ошибок подряд сейчас (0 — релей исправен).
 */
data class RelayMetrics(
    val successCount: Long = 0,
    val failureCount: Long = 0,
    val fallbackCount: Long = 0,
    val avgLatencyMs: Long? = null,
    val consecutiveFailures: Int = 0
)

/**
 * Здоровье релеёв и «залипание» (шаг 2 пула). Чистая логика: время приходит из [RelayClock], состояние
 * только в памяти, потокобезопасно.
 *
 * Правила:
 * - ошибка инфраструктуры → пауза (cooldown) 30 с, при повторах ×2 до 5 минут;
 * - ошибка настройки (AUTH, TLS13_UNSUPPORTED) → сразу пауза 5 минут;
 * - нейтральная ошибка (TARGET_OFFLINE, REPLACED) → штрафа нет, релей считается исправным;
 * - любой признак исправности ([recordHealthy], [recordDelivery]) снимает паузу и обнуляет счётчик ошибок;
 * - релей в паузе не удаляется: [order] ставит его в конец очереди, и если «мёртвыми» оказались все,
 *   порядок остаётся приоритетным и пробуются все;
 * - «залипание»: после доставки релей запоминается для КОНКРЕТНОГО контакта на [RelayHealthConfig.stickyMillis]
 *   и идёт первым, пока не истечёт срок или релей не получит ошибку; потом порядок возвращается к настройкам.
 */
class RelayHealth(
    private val clock: RelayClock,
    private val config: RelayHealthConfig = RelayHealthConfig()
) {
    private class State {
        var successes = 0L
        var failures = 0L
        var fallbacks = 0L
        var latencySum = 0L
        var latencySamples = 0L
        var consecutive = 0
        var cooldownUntil = NO_COOLDOWN
    }

    private class Sticky(val address: RelayAddress, val until: Long)

    private val lock = Any()
    private val states = HashMap<RelayAddress, State>()
    private val sticky = HashMap<String, Sticky>()

    /** Ошибка при обращении к релею. Нейтральные ошибки штрафа не дают и считаются признаком исправности. */
    fun recordFailure(address: RelayAddress, failure: RelayFailure) = synchronized(lock) {
        val kind = kindOf(failure)
        if (kind == RelayFailureKind.NEUTRAL) {
            markHealthy(stateOf(address))
            return@synchronized
        }
        val s = stateOf(address)
        s.failures++
        s.consecutive++
        val pause = if (kind == RelayFailureKind.CONFIGURATION) config.configCooldownMillis else infrastructurePause(s.consecutive)
        s.cooldownUntil = clock.nowMillis() + pause
        sticky.values.removeAll { it.address == address } // залипший релей сломался — залипание снимается
    }

    /** Релей ответил нормально (регистрация, успешная проверка, «получателя нет в сети»): пауза и серия ошибок сбрасываются. */
    fun recordHealthy(address: RelayAddress) = synchronized(lock) {
        markHealthy(stateOf(address))
    }

    /**
     * Доставка завершена через [address]: метрики, снятие паузы и «залипание» для [contactId].
     * @param fallback релей был не первым в очереди.
     */
    fun recordDelivery(address: RelayAddress, contactId: String, latencyMillis: Long?, fallback: Boolean) = synchronized(lock) {
        val s = stateOf(address)
        markHealthy(s)
        s.successes++
        if (fallback) s.fallbacks++
        if (latencyMillis != null && latencyMillis >= 0) {
            s.latencySum += latencyMillis
            s.latencySamples++
        }
        sticky[contactId] = Sticky(address, clock.nowMillis() + config.stickyMillis)
    }

    fun isCoolingDown(address: RelayAddress): Boolean = synchronized(lock) {
        clock.nowMillis() < (states[address]?.cooldownUntil ?: NO_COOLDOWN)
    }

    /** Сколько ещё релей в паузе (0 — не в паузе). */
    fun cooldownRemainingMillis(address: RelayAddress): Long = synchronized(lock) {
        val until = states[address]?.cooldownUntil ?: return@synchronized 0L
        (until - clock.nowMillis()).coerceAtLeast(0L)
    }

    /** Релей, залипший за контактом, или null (нет, истёк срок). */
    fun stickyFor(contactId: String): RelayAddress? = synchronized(lock) {
        val s = sticky[contactId] ?: return@synchronized null
        if (clock.nowMillis() >= s.until) {
            sticky.remove(contactId)
            null
        } else {
            s.address
        }
    }

    /**
     * Порядок попыток для контакта: залипший (если жив) → живые по приоритету → в паузе по приоритету.
     * [priority] — адреса в порядке настроек пользователя. Все адреса сохраняются: ничего не пропадает.
     */
    fun order(contactId: String?, priority: List<RelayAddress>): List<RelayAddress> = synchronized(lock) {
        val now = clock.nowMillis()
        fun cooling(a: RelayAddress) = now < (states[a]?.cooldownUntil ?: NO_COOLDOWN)

        val stuck = contactId?.let { id ->
            sticky[id]?.let { s ->
                when {
                    now >= s.until -> {
                        sticky.remove(id)
                        null
                    }
                    s.address in priority && !cooling(s.address) -> s.address
                    else -> null
                }
            }
        }
        val rest = priority.filter { it != stuck }
        listOfNotNull(stuck) + rest.filter { !cooling(it) } + rest.filter { cooling(it) }
    }

    fun metrics(address: RelayAddress): RelayMetrics = synchronized(lock) {
        states[address]?.let(::snapshot) ?: RelayMetrics()
    }

    fun allMetrics(): Map<RelayAddress, RelayMetrics> = synchronized(lock) {
        states.mapValues { snapshot(it.value) }
    }

    /** Забывает релеи, которых больше нет в пуле (и залипание на них). */
    fun retain(addresses: Set<RelayAddress>) = synchronized(lock) {
        states.keys.retainAll(addresses)
        sticky.values.removeAll { it.address !in addresses }
    }

    private fun stateOf(address: RelayAddress) = states.getOrPut(address) { State() }

    private fun markHealthy(s: State) {
        s.consecutive = 0
        s.cooldownUntil = NO_COOLDOWN
    }

    private fun infrastructurePause(consecutiveFailures: Int): Long {
        val factor = 1L shl (consecutiveFailures - 1).coerceIn(0, 20)
        return minOf(config.cooldownBaseMillis * factor, config.cooldownCapMillis)
    }

    private fun snapshot(s: State) = RelayMetrics(
        successCount = s.successes,
        failureCount = s.failures,
        fallbackCount = s.fallbacks,
        avgLatencyMs = if (s.latencySamples == 0L) null else s.latencySum / s.latencySamples,
        consecutiveFailures = s.consecutive
    )

    companion object {
        private const val NO_COOLDOWN = Long.MIN_VALUE

        /**
         * Классификация ошибок. PROTOCOL отнесён к инфраструктурным: это и неверная версия, и порванный
         * кадр, поэтому пауза растёт, а не сразу максимальная.
         */
        fun kindOf(failure: RelayFailure): RelayFailureKind = when (failure) {
            RelayFailure.TARGET_OFFLINE, RelayFailure.REPLACED -> RelayFailureKind.NEUTRAL
            RelayFailure.AUTH, RelayFailure.TLS13_UNSUPPORTED -> RelayFailureKind.CONFIGURATION
            RelayFailure.UNREACHABLE, RelayFailure.TLS, RelayFailure.TIMEOUT, RelayFailure.BUSY,
            RelayFailure.INTERNAL, RelayFailure.PROTOCOL -> RelayFailureKind.INFRASTRUCTURE
        }
    }
}
