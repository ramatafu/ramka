package com.ramka.network.relay

import com.ramka.domain.relay.RelayFailure
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * Проводной протокол релея, версия 1 (RELAY_PROTOCOL.md). Здесь только кадры управления;
 * после READY соединение становится «трубой» с обычным форматом ramka (см. SecureLanChannel).
 */
internal object RelayProtocol {
    const val VERSION = 1
    const val MAX_FRAME_BODY = 512 // §2

    const val ROUTING_ID_SIZE = 16 // §3
    const val SESSION_ID_SIZE = 16 // §4.2
    const val NONCE_SIZE = 32 // §4.1
    const val PUBLIC_KEY_SIZE = 32
    const val SIGNATURE_SIZE = 64

    // Типы кадров (§2).
    const val REGISTER = 0x01
    const val CONNECT = 0x02
    const val ACCEPT = 0x03
    const val CHALLENGE = 0x04
    const val PROOF = 0x05
    const val REGISTERED = 0x06
    const val INCOMING = 0x07
    const val READY = 0x08
    const val PING = 0x09
    const val PONG = 0x0A
    const val ERR = 0x7F

    // Коды ERR (§5).
    const val CODE_BAD_FRAME = 0x01
    const val CODE_UNSUPPORTED_VERSION = 0x02
    const val CODE_AUTH = 0x03
    const val CODE_BAD_ID = 0x04
    const val CODE_BAD_PROOF = 0x05
    const val CODE_OFFLINE = 0x06
    const val CODE_TIMEOUT = 0x07
    const val CODE_NO_SESSION = 0x08
    const val CODE_BUSY = 0x09
    const val CODE_REPLACED = 0x0A
    const val CODE_INTERNAL = 0x0B

    private val ROUTING_LABEL = "ramka-relay-v1".toByteArray(Charsets.US_ASCII)
    private val PROOF_LABEL = "ramka-relay-v1-register".toByteArray(Charsets.US_ASCII)

    class Frame(val type: Int, val body: ByteArray)

    /** routing_id = SHA-256("ramka-relay-v1" ‖ ed25519_pub)[0..16] (§3). */
    fun routingId(signingPublicKey: ByteArray): ByteArray {
        require(signingPublicKey.size == PUBLIC_KEY_SIZE) { "Ed25519-ключ должен быть 32 байта" }
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(ROUTING_LABEL)
        digest.update(signingPublicKey)
        return digest.digest().copyOf(ROUTING_ID_SIZE)
    }

    /** Подписываемое сообщение REGISTER: "ramka-relay-v1-register" ‖ nonce ‖ routing_id (§3). */
    fun proofMessage(nonce: ByteArray, routingId: ByteArray): ByteArray = PROOF_LABEL + nonce + routingId

    /** Тело REGISTER/CONNECT/ACCEPT: ver ‖ token_len ‖ token ‖ tail (§2). */
    fun authBody(token: String, tail: ByteArray): ByteArray {
        val t = token.toByteArray(Charsets.UTF_8)
        require(t.size in 16..64) { "Токен должен быть 16..64 байта" }
        val out = ByteArray(2 + t.size + tail.size)
        out[0] = VERSION.toByte()
        out[1] = t.size.toByte()
        System.arraycopy(t, 0, out, 2, t.size)
        System.arraycopy(tail, 0, out, 2 + t.size, tail.size)
        return out
    }

    /** Пишет кадр `[type:1][length:2 BE][body]` одним write (один TLS-record). */
    fun writeFrame(output: OutputStream, type: Int, body: ByteArray = ByteArray(0)) {
        require(body.size <= MAX_FRAME_BODY) { "Тело кадра больше $MAX_FRAME_BODY байт" }
        val frame = ByteArray(3 + body.size)
        frame[0] = type.toByte()
        frame[1] = (body.size ushr 8).toByte()
        frame[2] = body.size.toByte()
        System.arraycopy(body, 0, frame, 3, body.size)
        output.write(frame)
        output.flush()
    }

    /**
     * Читает ровно один кадр, не больше: после READY поток сырой, лишнего читать нельзя
     * (поэтому только `read` напрямую из потока сокета, без буферизации).
     * Таймаут чтения — `soTimeout` сокета; слишком большой кадр — [RelayException] PROTOCOL.
     */
    fun readFrame(input: InputStream): Frame {
        val header = readFully(input, 3)
        val length = ((header[1].toInt() and 0xFF) shl 8) or (header[2].toInt() and 0xFF)
        if (length > MAX_FRAME_BODY) throw RelayException(RelayFailure.PROTOCOL, "Слишком большой кадр: $length")
        return Frame(header[0].toInt() and 0xFF, readFully(input, length))
    }

    private fun readFully(input: InputStream, size: Int): ByteArray {
        val buffer = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val n = input.read(buffer, offset, size - offset)
            if (n < 0) throw EOFException("Релей закрыл соединение")
            offset += n
        }
        return buffer
    }

    /** Код ERR → причина для UI и [com.ramka.domain.relay.RelayProvider.report]. */
    fun failureForCode(code: Int): RelayFailure = when (code) {
        CODE_AUTH -> RelayFailure.AUTH
        CODE_OFFLINE -> RelayFailure.TARGET_OFFLINE
        CODE_TIMEOUT -> RelayFailure.TIMEOUT
        CODE_BUSY -> RelayFailure.BUSY
        CODE_REPLACED -> RelayFailure.REPLACED
        CODE_INTERNAL -> RelayFailure.INTERNAL
        else -> RelayFailure.PROTOCOL // BAD_FRAME, UNSUPPORTED_VERSION, BAD_ID, BAD_PROOF, NO_SESSION, неизвестные
    }

    /** Кадр ERR → исключение; для остальных неожиданных кадров — PROTOCOL. */
    fun unexpected(frame: Frame): RelayException =
        if (frame.type == ERR && frame.body.size == 1) {
            val code = frame.body[0].toInt() and 0xFF
            RelayException(failureForCode(code), "ERR 0x%02x".format(code))
        } else {
            RelayException(RelayFailure.PROTOCOL, "Неожиданный кадр 0x%02x".format(frame.type))
        }
}

/** Ошибка работы с релеем с готовой причиной для UI. */
class RelayException(val failure: RelayFailure, message: String? = null, cause: Throwable? = null) :
    IOException(message ?: failure.name, cause)
