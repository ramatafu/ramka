package com.ramka.app.turn

import com.ramka.app.data.TurnUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import java.io.DataInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import javax.inject.Inject

/**
 * Лёгкая проверка доступности TURN-сервера БЕЗ авторизации и без Allocate: STUN Binding Request
 * (RFC 5389) — на него отвечает любой TURN-сервер (в том числе coturn), логин и пароль не нужны.
 *
 *  - `turn:`  — UDP Binding Request (3 попытки), если ответа нет — тот же запрос по TCP
 *              (звонок использует оба транспорта: основной UDP и резервный `?transport=tcp`).
 *  - `turns:` — TCP-подключение к порту (TLS-рукопожатие не делаем: самоподписанный сертификат
 *              проверка не должна считать ошибкой, а звонок сам проверит его при подключении).
 *
 * Общий лимит одной проверки — [TOTAL_TIMEOUT_MS] (5 с). Ничего не логируется.
 */
class TurnServerChecker @Inject constructor() {

    // Отдельный scope: блокирующий сокет нельзя прервать, но результат при тайм-ауте мы всё равно
    // выбрасываем, а потоку даём доработать сам (у всех сокетов есть собственные тайм-ауты).
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun check(url: TurnUrl): TurnServerStatus = checkTurnServerAvailability(url.host, url.port, url.secure)

    suspend fun checkTurnServerAvailability(host: String, port: Int, secure: Boolean): TurnServerStatus {
        val job = ioScope.async { probe(host, port, secure) }
        val ok = withTimeoutOrNull(TOTAL_TIMEOUT_MS) { job.await() }
        if (ok == null) job.cancel()
        return if (ok == true) TurnServerStatus.WORKING else TurnServerStatus.UNREACHABLE
    }

    private fun probe(host: String, port: Int, secure: Boolean): Boolean {
        val address = try {
            InetAddress.getByName(host)
        } catch (_: Exception) {
            return false
        }
        return if (secure) {
            tcpPortOpen(address, port)
        } else {
            udpBinding(address, port) || tcpBinding(address, port)
        }
    }

    private fun udpBinding(address: InetAddress, port: Int): Boolean = try {
        DatagramSocket().use { socket ->
            socket.soTimeout = UDP_ATTEMPT_MS
            socket.connect(address, port) // чужие пакеты отфильтруются; ICMP «порт закрыт» приходит как исключение
            val tx = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val request = StunProbe.bindingRequest(tx)
            val buf = ByteArray(512)
            val reply = DatagramPacket(buf, buf.size)
            repeat(UDP_ATTEMPTS) {
                socket.send(DatagramPacket(request, request.size))
                try {
                    socket.receive(reply)
                    if (StunProbe.isBindingResponse(reply.data, reply.length, tx)) return@use true
                } catch (_: SocketTimeoutException) {
                    // ждём следующую попытку
                }
            }
            false
        }
    } catch (_: Exception) {
        false
    }

    private fun tcpBinding(address: InetAddress, port: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(address, port), TCP_CONNECT_MS)
            socket.soTimeout = TCP_READ_MS
            val tx = ByteArray(12).also { SecureRandom().nextBytes(it) }
            socket.getOutputStream().apply { write(StunProbe.bindingRequest(tx)); flush() }
            val header = ByteArray(StunProbe.HEADER_SIZE)
            DataInputStream(socket.getInputStream()).readFully(header)
            StunProbe.isBindingResponse(header, header.size, tx)
        }
    } catch (_: Exception) {
        false
    }

    private fun tcpPortOpen(address: InetAddress, port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(address, port), TCP_CONNECT_MS) }
        true
    } catch (_: Exception) {
        false
    }

    companion object {
        const val TOTAL_TIMEOUT_MS = 5_000L
        private const val UDP_ATTEMPTS = 3
        private const val UDP_ATTEMPT_MS = 800
        private const val TCP_CONNECT_MS = 1_500
        private const val TCP_READ_MS = 1_200
    }
}

/** Разбор и сборка STUN-сообщений Binding (чистая логика, без сети). */
internal object StunProbe {
    const val HEADER_SIZE = 20
    private const val TYPE_BINDING_REQUEST = 0x0001
    private const val TYPE_BINDING_SUCCESS = 0x0101
    private const val TYPE_BINDING_ERROR = 0x0111
    private const val MAGIC_COOKIE = 0x2112A442

    /** 20 байт: тип 0x0001, длина 0, magic cookie, 12 байт идентификатора транзакции. */
    fun bindingRequest(transactionId: ByteArray): ByteArray {
        require(transactionId.size == 12) { "transactionId must be 12 bytes" }
        val out = ByteArray(HEADER_SIZE)
        out[0] = (TYPE_BINDING_REQUEST shr 8).toByte()
        out[1] = TYPE_BINDING_REQUEST.toByte()
        // out[2], out[3] — длина тела = 0
        out[4] = (MAGIC_COOKIE ushr 24).toByte()
        out[5] = (MAGIC_COOKIE ushr 16).toByte()
        out[6] = (MAGIC_COOKIE ushr 8).toByte()
        out[7] = MAGIC_COOKIE.toByte()
        transactionId.copyInto(out, destinationOffset = 8)
        return out
    }

    /** Ответ на наш запрос: успех (0x0101) или ошибка (0x0111) с тем же cookie и transaction id — сервер жив. */
    fun isBindingResponse(data: ByteArray, length: Int, transactionId: ByteArray): Boolean {
        if (length < HEADER_SIZE || data.size < HEADER_SIZE || transactionId.size != 12) return false
        val type = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        if (type != TYPE_BINDING_SUCCESS && type != TYPE_BINDING_ERROR) return false
        val cookie = ((data[4].toInt() and 0xFF) shl 24) or ((data[5].toInt() and 0xFF) shl 16) or
            ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
        if (cookie != MAGIC_COOKIE) return false
        for (i in 0 until 12) if (data[8 + i] != transactionId[i]) return false
        return true
    }
}
