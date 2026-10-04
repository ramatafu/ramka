package com.ramka.domain.relay

import com.ramka.domain.model.Contact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

/**
 * Пул релеёв как [RelayProvider] (шаг 2). Частный случай — пул из одного релея, поэтому прежний
 * провайдер одного релея не нужен.
 *
 * - Приём ([ownRelays]): все рабочие записи включённого пула, регистрация держится на каждой. С режимом
 *   экономии батареи ([RelayPool.batterySaver]) — только на одном: первом по порядку, который сейчас не в
 *   паузе (если в паузе все — на первом по порядку). Набор пересчитывается при каждом отчёте о здоровье.
 * - Отправка ([relaysFor]): порядок задаёт [RelayHealth] (залипший → живые → в паузе).
 * - Отчёты ([report], [reportDelivery], [reportLink], [reportCheck]) кормят [RelayHealth] и состояния для экрана.
 */
class PoolRelayProvider(
    private val source: RelayPoolSource,
    clock: RelayClock = RelayClock.System,
    private val health: RelayHealth = RelayHealth(clock)
) : RelayProvider {

    private val tick = MutableStateFlow(0)
    private val linkStates = MutableStateFlow<Map<RelayAddress, RelayLinkState>>(emptyMap())
    private val metricsState = MutableStateFlow<Map<RelayAddress, RelayMetrics>>(emptyMap())
    private val lastFailureState = MutableStateFlow<RelayFailure?>(null)

    /** Состояние связи по адресу релея: для экрана настроек. Только релеи включённого пула. */
    val connectionStates: StateFlow<Map<RelayAddress, RelayLinkState>> = linkStates.asStateFlow()

    /** Метрики релеёв пула (успехи, ошибки, запасные варианты, средняя задержка). */
    val metrics: StateFlow<Map<RelayAddress, RelayMetrics>> = metricsState.asStateFlow()

    /** Последняя ошибка работы с релеем (null — исправен). Переходное: старый экран; новый использует [connectionStates]. */
    val lastFailure: StateFlow<RelayFailure?> = lastFailureState.asStateFlow()

    override val ownRelays: Flow<List<RelayEndpoint>> =
        combine(source.pool, tick) { pool, _ -> receiveSet(pool) }
            .distinctUntilChanged()
            .onEach { pruneStates() }

    override suspend fun relaysFor(contact: Contact): List<RelayEndpoint> {
        val endpoints = usableEndpoints(source.pool.value)
        if (endpoints.size <= 1) return endpoints
        val byAddress = endpoints.associateBy { it.address }
        return health.order(contact.localId, endpoints.map { it.address }).mapNotNull { byAddress[it] }
    }

    override fun report(endpoint: RelayEndpoint, failure: RelayFailure?) {
        if (failure == null) health.recordHealthy(endpoint.address) else health.recordFailure(endpoint.address, failure)
        lastFailureState.value = failure?.takeUnless { it == RelayFailure.TARGET_OFFLINE }
        afterChange()
    }

    override fun reportDelivery(endpoint: RelayEndpoint, contact: Contact, latencyMillis: Long, fallback: Boolean) {
        health.recordDelivery(endpoint.address, contact.localId, latencyMillis, fallback)
        lastFailureState.value = null
        afterChange()
    }

    override fun reportLink(endpoint: RelayEndpoint, state: RelayLinkState) {
        linkStates.update { it + (endpoint.address to state) }
        when (state) {
            RelayLinkState.Connected -> {
                health.recordHealthy(endpoint.address)
                lastFailureState.value = null
            }
            is RelayLinkState.Failed -> {
                health.recordFailure(endpoint.address, state.failure)
                lastFailureState.value = state.failure.takeUnless { it == RelayFailure.TARGET_OFFLINE }
            }
            RelayLinkState.Connecting, is RelayLinkState.Reachable -> Unit
        }
        afterChange()
    }

    /**
     * Итог фоновой проверки ([RelayHealthMonitor]). Учитывается как обычный отчёт о здоровье, а в состояние
     * для экрана попадает только если на этом релее нет живой регистрации: рабочее подключение проверкой
     * не «портится».
     */
    fun reportCheck(endpoint: RelayEndpoint, result: RelayCheckResult, latencyMillis: Long?) {
        val current = linkStates.value[endpoint.address]
        val replaceable = current == null || current is RelayLinkState.Reachable || current is RelayLinkState.Failed
        when (result) {
            RelayCheckResult.Ok -> {
                health.recordHealthy(endpoint.address)
                if (replaceable) linkStates.update { it + (endpoint.address to RelayLinkState.Reachable(latencyMillis)) }
            }
            is RelayCheckResult.Failed -> {
                health.recordFailure(endpoint.address, result.failure)
                if (replaceable) linkStates.update { it + (endpoint.address to RelayLinkState.Failed(result.failure)) }
            }
        }
        afterChange()
    }

    /** Пересчитать приём (режим экономии, истёкшие паузы) и убрать состояния релеёв, которых больше нет. */
    fun refresh() {
        afterChange()
    }

    private fun usableEndpoints(pool: RelayPool): List<RelayEndpoint> = pool.activeEntries.mapNotNull { it.endpointOrNull }

    private fun receiveSet(pool: RelayPool): List<RelayEndpoint> {
        val endpoints = usableEndpoints(pool)
        if (!pool.batterySaver || endpoints.size <= 1) return endpoints
        return listOf(endpoints.firstOrNull { !health.isCoolingDown(it.address) } ?: endpoints.first())
    }

    private fun afterChange() {
        health.retain(source.pool.value.entries.map { it.address }.toSet())
        pruneStates()
        metricsState.value = health.allMetrics()
        tick.update { it + 1 }
    }

    /** Состояния хранятся только для релеёв включённого пула: выключили или удалили — запись исчезает. */
    private fun pruneStates() {
        val keep = source.pool.value.activeEntries.map { it.address }.toSet()
        linkStates.update { states -> if (states.keys.all { it in keep }) states else states.filterKeys { it in keep } }
    }
}
