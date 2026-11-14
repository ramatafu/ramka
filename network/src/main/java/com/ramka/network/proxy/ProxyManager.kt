package com.ramka.network.proxy

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy

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

    init {
        scope.launch {
            source.settings.collect { settings ->
                current = settings
                loaded.complete(Unit)
            }
        }
    }

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

    companion object {
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
