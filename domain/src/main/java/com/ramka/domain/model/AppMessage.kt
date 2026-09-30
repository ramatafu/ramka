package com.ramka.domain.model

/**
 * Прикладной конверт поверх уже расшифрованного тела (см. TransportRepository —
 * шифруется/расшифровывается на уровне транспорта ровно этот закодированный
 * конверт, а не голый текст). Позволяет провести DELIVERED-ACK/READ-ACK через
 * тот же зашифрованный канал, что и обычные текстовые сообщения (ЭТАП B.5) —
 * и то, и другое идёт одним и тем же `TransportRepository.sendEncryptedPacket`,
 * разница только в теге типа внутри уже зашифрованных данных.
 *
 * НАМЕРЕННО НЕТ ни единого поля с точным временем — ни здесь, ни в закодированном
 * виде. НЕТ ID контактов и содержимого сообщений в ACK — только ID(ы) сообщений
 * и тип (ревью ЭТАП B, п.2).
 *
 * [Text.messageId] — localId сообщения СО СТОРОНЫ ОТПРАВИТЕЛЯ, всегда UUIDv4
 * (случайный, не последовательный — см. ревью п.2). Получатель обязан сохранить
 * это значение (Message.remoteMessageId) и вернуть его же в ACK.
 *
 * [ReadAck.messageLocalIds] — батч из нескольких ID разрешён (ревью п.4): при
 * открытии чата с несколькими непрочитанными сообщениями отправляется ОДИН
 * ReadAck со списком, а не по одному соединению на сообщение (в отличие от
 * flush очереди Outbox, где батчинг прямо запрещён — см. DEVIATIONS.md).
 * [DeliveredAck] батч НЕ поддерживает: он всегда об одном, только что принятом
 * сообщении, отправляется немедленно, а не пачкой позже.
 *
 * Кодирование — простой самодельный бинарный формат (1 байт тега + payload),
 * НЕ криптография — сериализация происходит ДО шифрования (при отправке) и
 * ПОСЛЕ расшифровки (при приёме), сама по себе секретности не добавляет.
 * messageId — всегда UUID.toString(), то есть ровно 36 ASCII-символов, поэтому
 * несколько ID можно склеить без разделителей и резать по фиксированной длине.
 */
sealed class AppMessage {
    data class Text(val messageId: String, val text: String) : AppMessage()
    data class DeliveredAck(val messageLocalId: String) : AppMessage()
    data class ReadAck(val messageLocalIds: List<String>) : AppMessage()

    fun encode(): ByteArray = when (this) {
        is Text -> byteArrayOf(TAG_TEXT) + messageId.toByteArray(Charsets.US_ASCII) + text.toByteArray(Charsets.UTF_8)
        is DeliveredAck -> byteArrayOf(TAG_DELIVERED_ACK) + messageLocalId.toByteArray(Charsets.US_ASCII)
        is ReadAck -> byteArrayOf(TAG_READ_ACK) + messageLocalIds.joinToString("").toByteArray(Charsets.US_ASCII)
    }

    companion object {
        private const val TAG_TEXT: Byte = 0
        private const val TAG_DELIVERED_ACK: Byte = 1
        private const val TAG_READ_ACK: Byte = 2
        private const val UUID_LENGTH = 36

        /** Возвращает null на пустых/повреждённых/незнакомых данных — вызывающий код должен их отбрасывать, а не падать. */
        fun decode(bytes: ByteArray): AppMessage? {
            if (bytes.isEmpty()) return null
            val payload = bytes.copyOfRange(1, bytes.size)
            return when (bytes[0]) {
                TAG_TEXT -> {
                    if (payload.size < UUID_LENGTH) return null
                    val messageId = payload.copyOfRange(0, UUID_LENGTH).toString(Charsets.US_ASCII)
                    val text = payload.copyOfRange(UUID_LENGTH, payload.size).toString(Charsets.UTF_8)
                    Text(messageId, text)
                }
                TAG_DELIVERED_ACK -> {
                    if (payload.size != UUID_LENGTH) return null
                    DeliveredAck(payload.toString(Charsets.US_ASCII))
                }
                TAG_READ_ACK -> {
                    if (payload.isEmpty() || payload.size % UUID_LENGTH != 0) return null
                    val ids = payload.toString(Charsets.US_ASCII).chunked(UUID_LENGTH)
                    ReadAck(ids)
                }
                else -> null
            }
        }
    }
}
