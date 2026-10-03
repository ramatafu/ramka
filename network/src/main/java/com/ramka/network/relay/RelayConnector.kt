package com.ramka.network.relay

import com.ramka.domain.relay.RelayEndpoint
import com.ramka.domain.relay.RelayFailure
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
 *
 * TLS 1.3 в платформе есть с Android 10 (API 29); ниже — [RelayFailure.TLS13_UNSUPPORTED].
 */
class TlsRelayConnector : RelayConnector {

    override fun connect(endpoint: RelayEndpoint, timeoutMillis: Int): Socket {
        val address = endpoint.address
        val pin = endpoint.pinSha256
        if (pin == null && address.isIpLiteral) {
            throw RelayException(RelayFailure.TLS, "Для IP-адреса без пина сертификат проверить нечем")
        }
        val context = createContext(pin)

        val raw = Socket()
        try {
            val resolved = InetSocketAddress(address.host, address.port) // резолв здесь, на каждом подключении
            if (resolved.isUnresolved) throw RelayException(RelayFailure.UNREACHABLE, "Не удалось разрешить имя ${address.host}")
            raw.connect(resolved, timeoutMillis)
            raw.tcpNoDelay = true
            raw.soTimeout = timeoutMillis // потолок на TLS-рукопожатие

            val ssl = context.socketFactory.createSocket(raw, address.host, address.port, true) as SSLSocket
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
            return ssl
        } catch (e: RelayException) {
            closeQuietly(raw)
            throw e
        } catch (e: SSLException) {
            closeQuietly(raw)
            throw RelayException(RelayFailure.TLS, "TLS: ${e.message}", e)
        } catch (e: IOException) {
            closeQuietly(raw)
            throw RelayException(RelayFailure.UNREACHABLE, e.message, e)
        } catch (e: RuntimeException) {
            closeQuietly(raw)
            throw RelayException(RelayFailure.UNREACHABLE, e.message, e)
        }
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
