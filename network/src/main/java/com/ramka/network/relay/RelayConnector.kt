package com.ramka.network.relay

import com.ramka.domain.relay.RelayEndpoint
import com.ramka.domain.relay.RelayFailure
import com.ramka.network.proxy.ProxyManager
import com.ramka.network.proxy.ProxyMisconfiguredException
import com.ramka.network.proxy.ProxyNotReadyException
import com.ramka.network.proxy.ProxySettings
import com.ramka.network.proxy.ProxyTunnel
import kotlinx.coroutines.runBlocking
import java.util.Collections
import java.util.WeakHashMap
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Устанавливает защищённое соединение с релеем. Блокирующий; вызывать на IO-потоке. */
interface RelayConnector {
    /**
     * Возвращает готовый к обмену сокет (TLS-рукопожатие завершено).
     * @throws RelayException с причиной: UNREACHABLE, TLS, TLS13_UNSUPPORTED.
     */
    fun connect(endpoint: RelayEndpoint, timeoutMillis: Int): Socket
}

/**
 * TLS 1.3 к релею (RELAY_PROTOCOL.md §1).
 *
 * - Пин задан → проверяется ТОЛЬКО он: SHA-256 от DER SubjectPublicKeyInfo листового сертификата;
 *   цепочка и имя хоста не проверяются (сертификат может быть самоподписанным).
 * - Пина нет → системные CA и проверка имени хоста. Для IP-литерала без пина подключение запрещено.
 * - Имя резолвится при каждом подключении через `InetSocketAddress(host, port)`, без кэша.
 * - Прокси: если он включён, TCP-соединение идёт через [ProxyTunnel] (SOCKS5 или HTTP CONNECT, рукопожатие вручную,
 *   имя релея локально НЕ резолвится), а TLS и проверка пина делаются поверх туннеля как обычно. Если прокси
 *   включён, но настроен неверно, не загружен или не ответил, соединения нет ([RelayFailure.PROXY]): напрямую
 *   оно не уходит (fail closed).
 * - Смена маршрута (прокси включили, выключили или изменили): [closeStale] немедленно закрывает ВСЕ сокеты, открытые
 *   по прежнему маршруту и ещё живые (регистрации, отправки, проверки, подключения в процессе), чтобы ничего не
 *   осталось на старом маршруте. Каждый сокет помечен номером маршрута на момент начала подключения, поэтому
 *   сокеты нового маршрута не трогаются. Подключение, начатое по старым настройкам, само обрывается, если маршрут
 *   сменился, пока оно шло.
 *
 * TLS 1.3 в платформе есть с Android 10 (API 29); ниже — [RelayFailure.TLS13_UNSUPPORTED].
 */
class TlsRelayConnector(private val proxyManager: ProxyManager) : RelayConnector {

    /**
     * Открытые TCP-сокеты (под TLS) и номер маршрута на момент начала их подключения. Слабые ссылки: закрытые и
     * забытые сокеты из набора уходят сами.
     */
    private val live: MutableMap<Socket, Long> = Collections.synchronizedMap(WeakHashMap<Socket, Long>())

    init {
        proxyManager.addRouteChangeListener { closeStale() }
    }

    /**
     * Закрывает все ещё живые сокеты, открытые до последней смены маршрута. Сокеты нового маршрута остаются.
     * Не бросает исключений.
     */
    fun closeStale() {
        val current = proxyManager.generation.value
        val stale = synchronized(live) { live.entries.filter { it.value < current }.map { it.key } }
        for (socket in stale) closeQuietly(socket)
    }

    private fun track(socket: Socket, generation: Long) {
        live[socket] = generation
    }

    /** Если маршрут сменился с момента [generation], это подключение уже нельзя считать верным: оно оборвётся. */
    private fun ensureRouteUnchanged(generation: Long) {
        if (proxyManager.generation.value != generation) {
            throw RelayException(RelayFailure.PROXY, "Настройки прокси изменились во время подключения")
        }
    }

    override fun connect(endpoint: RelayEndpoint, timeoutMillis: Int): Socket {
        val address = endpoint.address
        val pin = endpoint.pinSha256
        if (pin == null && address.isIpLiteral) {
            throw RelayException(RelayFailure.TLS, "Для IP-адреса без пина сертификат проверить нечем")
        }
        val generation = proxyManager.generation.value // читаем ДО настроек: иначе смену можно пропустить
        val proxy = currentProxy() // до любой сети: неверный прокси не должен приводить к прямому подключению
        val context = createContext(pin)

        var raw: Socket? = null
        try {
            val tcp = if (proxy == null) connectDirect(address.host, address.port, timeoutMillis, generation)
            else connectViaProxy(proxy, address.host, address.port, timeoutMillis, generation)
            raw = tcp
            ensureRouteUnchanged(generation)
            tcp.tcpNoDelay = true
            tcp.soTimeout = timeoutMillis // потолок на TLS-рукопожатие

            val ssl = context.socketFactory.createSocket(tcp, address.host, address.port, true) as SSLSocket
            try {
                ssl.enabledProtocols = arrayOf(TLS_13)
            } catch (e: IllegalArgumentException) {
                throw RelayException(RelayFailure.TLS13_UNSUPPORTED, "Платформа не поддерживает TLS 1.3", e)
            }
            if (pin == null) {
                val params = ssl.sslParameters
                params.endpointIdentificationAlgorithm = "HTTPS" // проверка имени хоста по сертификату
                ssl.sslParameters = params
            }
            ssl.startHandshake()
            ensureRouteUnchanged(generation) // смена могла прийтись на рукопожатие
            return ssl
        } catch (e: RelayException) {
            raw?.let { closeQuietly(it) }
            throw e
        } catch (e: SSLException) {
            raw?.let { closeQuietly(it) }
            throw RelayException(RelayFailure.TLS, "TLS: ${e.message}", e)
        } catch (e: IOException) {
            raw?.let { closeQuietly(it) }
            throw RelayException(RelayFailure.UNREACHABLE, e.message, e)
        } catch (e: RuntimeException) {
            raw?.let { closeQuietly(it) }
            throw RelayException(RelayFailure.UNREACHABLE, e.message, e)
        }
    }

    /** Прямое подключение; имя резолвится здесь, на каждом подключении. */
    private fun connectDirect(host: String, port: Int, timeoutMillis: Int, generation: Long): Socket {
        val socket = Socket() // без аргументов: глобальный Authenticator и системные прокси не затрагиваются
        track(socket, generation)
        try {
            val resolved = InetSocketAddress(host, port)
            if (resolved.isUnresolved) throw RelayException(RelayFailure.UNREACHABLE, "Не удалось разрешить имя $host")
            socket.connect(resolved, timeoutMillis)
            return socket
        } catch (e: Exception) {
            closeQuietly(socket)
            throw e
        }
    }

    /** Подключение через прокси; имя релея прокси получает как есть, локально оно не резолвится. */
    private fun connectViaProxy(proxy: ProxySettings, host: String, port: Int, timeoutMillis: Int, generation: Long): Socket =
        try {
            ProxyTunnel.open(proxy, host, port, timeoutMillis, onSocket = { track(it, generation) })
        } catch (e: ProxyMisconfiguredException) {
            throw RelayException(RelayFailure.PROXY, e.message, e)
        } catch (e: IOException) {
            // Тайм-аут, отказ прокси, неверный логин, обрыв: для пользователя это «не удалось через прокси».
            throw RelayException(RelayFailure.PROXY, e.message, e)
        }

    /**
     * Прокси на это соединение: null — выключен, идти напрямую. Ждёт загрузки настроек не дольше
     * [PROXY_AWAIT_MILLIS] (вызов идёт на IO-потоке). Неверные или незагруженные настройки — [RelayException].
     */
    private fun currentProxy(): ProxySettings? = try {
        runBlocking { proxyManager.awaitUsableSettings(PROXY_AWAIT_MILLIS) }
    } catch (e: ProxyMisconfiguredException) {
        throw RelayException(RelayFailure.PROXY, e.message, e)
    } catch (e: ProxyNotReadyException) {
        throw RelayException(RelayFailure.PROXY, e.message, e)
    }

    private fun createContext(pin: String?): SSLContext {
        val context = try {
            SSLContext.getInstance(TLS_13)
        } catch (e: NoSuchAlgorithmException) {
            throw RelayException(RelayFailure.TLS13_UNSUPPORTED, "Платформа не поддерживает TLS 1.3", e)
        }
        val managers: Array<TrustManager> = if (pin != null) {
            arrayOf(PinnedTrustManager(pin))
        } else {
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply { init(null as java.security.KeyStore?) }
                .trustManagers
        }
        context.init(null, managers, SecureRandom())
        return context
    }

    private fun closeQuietly(socket: Socket) {
        try {
            socket.close()
        } catch (_: IOException) {
        }
    }

    private companion object {
        const val TLS_13 = "TLSv1.3"
        const val PROXY_AWAIT_MILLIS = 5_000L
    }
}

/**
 * Доверяет единственному сертификату: тому, у которого SHA-256 от SubjectPublicKeyInfo равен пину (§1).
 * Пин переживает перевыпуск сертификата на том же ключе. Клиентские сертификаты не поддерживаются.
 */
internal class PinnedTrustManager(pinHex: String) : X509TrustManager {
    private val expected: ByteArray = decodeHex(pinHex)

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("Клиентская аутентификация не поддерживается")
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (chain.isNullOrEmpty()) throw CertificateException("Пустая цепочка сертификатов")
        val spki = chain[0].publicKey.encoded // DER SubjectPublicKeyInfo
        val actual = MessageDigest.getInstance("SHA-256").digest(spki)
        if (!MessageDigest.isEqual(actual, expected)) throw CertificateException("Пин сертификата не совпал")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    private companion object {
        fun decodeHex(hex: String): ByteArray {
            require(hex.length == 64) { "Пин должен быть 64 hex-символа" }
            return ByteArray(32) { i -> hex.substring(2 * i, 2 * i + 2).toInt(16).toByte() }
        }
    }
}
