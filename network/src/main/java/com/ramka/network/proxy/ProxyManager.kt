package com.ramka.network.proxy

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.CopyOnWriteArrayList

/** Прокси включён, но настроен неверно: соединение НЕ должно уходить напрямую (иначе адрес устройства утечёт). */
class ProxyMisconfiguredException(message: String) : IOException(message)

/** Настройки прокси ещё не прочитаны с диска: решение «через прокси или напрямую» пока принять нельзя. */
class ProxyNotReadyException : IOException("Настройки прокси ещё не загружены")

/**
 * Единая точка решения «идти ли через прокси». Следит за [ProxySettingsSource], поэтому смена настроек в UI
 * действует на следующее же соединение без пересоздания клиентов: [createProxy] каждый раз отдаёт актуальное.
 *
 * Принцип «fail closed»: если прокси включён, но настроен неверно, или настройки ещё не загружены, бросается
 * [IOException] (соединение просто не удалось), а не «напрямую». Адрес прокси отдаётся НЕразрешённым
 * (`InetSocketAddress.createUnresolved`): имя разрешается при подключении, а не здесь (без блокирующего DNS).
 *
 * Применяется только к соединениям вне локальной сети (relay, HTTP-запросы). Прямые каналы (LAN, Yggdrasil) и
 * UDP не проксируются.
 *
 * TODO(WebRTC): WebRTC идёт по UDP напрямую, и прокси его не пробрасывает. Звонки через прокси возможны только
 * через TURN-сервер (он уже есть в проекте), пока здесь ничего не меняется.
 */
class ProxyManager(source: ProxySettingsSource, scope: CoroutineScope) {

    @Volatile
    private var current: ProxySettings? = null
    private val loaded = CompletableDeferred<Unit>()

    private val generationState = MutableStateFlow(0L)
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    /**
     * Номер «маршрута»: растёт, когда меняется то, КАК устройство выходит в сеть (прокси включили, выключили, сменили
     * адрес, тип, порт, логин или пароль). Правка выключенного прокси маршрут не меняет. Первая загрузка настроек
     * с диска тоже не считается сменой. Порядок: сначала новые настройки, потом растёт номер, потом зовутся слушатели.
     */
    val generation: StateFlow<Long> = generationState.asStateFlow()

    /** Вызывается при смене маршрута (см. [generation]) после того, как новые настройки уже действуют. */
    fun addRouteChangeListener(listener: () -> Unit) {
        listeners += listener
    }

    init {
        scope.launch {
            source.settings.collect { settings ->
                val previous = current
                current = settings
                loaded.complete(Unit)
                if (previous != null && routeOf(previous) != routeOf(settings)) {
                    generationState.update { it + 1 }
                    for (listener in listeners) {
                        try {
                            listener()
                        } catch (_: Exception) { // слушатель не должен останавливать слежение за настройками
                        }
                    }
                }
            }
        }
    }

    /** Выключенный прокси — один и тот же маршрут («напрямую») при любых значениях остальных полей. */
    private fun routeOf(settings: ProxySettings): ProxySettings? = if (settings.enabled) settings else null

    /** Текущие настройки; пока не загружены — [ProxySettings.DISABLED] (только для показа, не для решений). */
    fun settings(): ProxySettings = current ?: ProxySettings.DISABLED

    /**
     * Прокси для нового соединения или null, если прокси выключен (идти напрямую).
     * @throws ProxyNotReadyException настройки ещё не прочитаны (см. [awaitProxy])
     * @throws ProxyMisconfiguredException прокси включён, но адрес или порт неверны
     */
    fun createProxy(): Proxy? = proxyFor(current ?: throw ProxyNotReadyException())

    /** То же, но дожидается первой загрузки настроек — для первого соединения после запуска приложения. */
    suspend fun awaitProxy(): Proxy? {
        loaded.await()
        return createProxy()
    }

    /**
     * Настройки для нового соединения или null, если прокси выключен. В отличие от [createProxy] отдаёт и логин с
     * паролем: они нужны ручному рукопожатию (SOCKS5 RFC 1929, HTTP CONNECT), где `java.net.Proxy` их не несёт.
     * @throws ProxyNotReadyException настройки ещё не прочитаны
     * @throws ProxyMisconfiguredException прокси включён, но адрес или порт неверны
     */
    fun usableSettings(): ProxySettings? {
        val settings = current ?: throw ProxyNotReadyException()
        if (!settings.enabled) return null
        settings.validate()?.let { throw ProxyMisconfiguredException(it) }
        return settings
    }

    /**
     * То же, но дожидается первой загрузки не дольше [timeoutMillis]; не загрузились, значит [ProxyNotReadyException]
     * (соединение не устанавливается, а не уходит напрямую).
     */
    suspend fun awaitUsableSettings(timeoutMillis: Long = DEFAULT_AWAIT_MILLIS): ProxySettings? {
        withTimeoutOrNull(timeoutMillis) { loaded.await() } ?: throw ProxyNotReadyException()
        return usableSettings()
    }

    companion object {
        const val DEFAULT_AWAIT_MILLIS = 5_000L

        /** Чистая часть решения (без состояния) — отдельно, чтобы проверять тестом. */
        fun proxyFor(settings: ProxySettings): Proxy? {
            if (!settings.enabled) return null
            settings.validate()?.let { throw ProxyMisconfiguredException(it) }
            val address = InetSocketAddress.createUnresolved(ProxySettings.stripBrackets(settings.host.trim()), settings.port)
            val type = when (settings.type) {
                ProxyType.SOCKS5 -> Proxy.Type.SOCKS
                ProxyType.HTTP -> Proxy.Type.HTTP
            }
            return Proxy(type, address)
        }
    }
}
