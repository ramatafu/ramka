package com.ramka.network.protocol

/**
 * Кадр, передаваемый по LAN-сокету. Простой бинарный формат — 1 байт типа
 * + произвольные байты полезной нагрузки (сам кадр дополнительно оборачивается
 * 4-байтной длиной на уровне сокета, см. network/local/LanFraming.kt).
 *
 * Никаких полей online/typing/lastSeen/точного времени — запрет п. 6.1
 * PROJECT_SPEC.md. Для зашифрованных сообщений payload = counter(8 байт) || ciphertext.
 */
data class Frame(val type: FrameType, val payload: ByteArray) {
    fun encode(): ByteArray = byteArrayOf(type.code) + payload

    companion object {
        fun decode(bytes: ByteArray): Frame {
            require(bytes.isNotEmpty()) { "Пустой кадр" }
            val type = FrameType.fromCode(bytes[0])
            return Frame(type, bytes.copyOfRange(1, bytes.size))
        }
    }
}

enum class FrameType(val code: Byte) {
    /** Message1 рукопожатия IK: senderStaticX25519Pub || e_i_pub. */
    HANDSHAKE_1(1),
    /** Message2 рукопожатия IK: e_r_pub || подпись Ed25519. */
    HANDSHAKE_2(2),
    /** Message3 рукопожатия IK: подпись Ed25519 инициатора. */
    HANDSHAKE_3(3),
    /** Зашифрованное сообщение поверх уже установленной сессии. */
    ENCRYPTED_MESSAGE(4);

    companion object {
        fun fromCode(code: Byte): FrameType =
            entries.firstOrNull { it.code == code } ?: throw IllegalArgumentException("Неизвестный тип кадра: $code")
    }
}
