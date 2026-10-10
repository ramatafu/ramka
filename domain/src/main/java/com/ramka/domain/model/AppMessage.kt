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

    /**
     * Начало передачи файла (этап 6.4). [messageId] — localId сообщения у отправителя (UUID),
     * как и у [Text]. [chunkSize] — размер всех чанков, кроме последнего; приёмник кладёт
     * чанк с номером i по смещению i * chunkSize, поэтому порядок прихода чанков не важен.
     * Имя и MIME-тип недоверенные: приёмник обязан их очистить (см. FileNames).
     */
    data class FileMeta(
        val messageId: String,
        val fileName: String,
        val totalSize: Long,
        val totalChunks: Int,
        val chunkSize: Int,
        val mimeType: String
    ) : AppMessage()

    /** Один чанк файла: [index] от 0 до totalChunks-1. Данные — как есть, без Base64 (канал бинарный). */
    data class FileChunk(val messageId: String, val index: Int, val data: ByteArray) : AppMessage() {
        override fun equals(other: Any?): Boolean =
            other is FileChunk && messageId == other.messageId && index == other.index && data.contentEquals(other.data)

        override fun hashCode(): Int = 31 * (31 * messageId.hashCode() + index) + data.contentHashCode()
    }

    fun encode(): ByteArray = when (this) {
        is Text -> byteArrayOf(TAG_TEXT) + messageId.toByteArray(Charsets.US_ASCII) + text.toByteArray(Charsets.UTF_8)
        is DeliveredAck -> byteArrayOf(TAG_DELIVERED_ACK) + messageLocalId.toByteArray(Charsets.US_ASCII)
        is ReadAck -> byteArrayOf(TAG_READ_ACK) + messageLocalIds.joinToString("").toByteArray(Charsets.US_ASCII)
        is FileMeta -> encodeFileMeta(this)
        is FileChunk -> byteArrayOf(TAG_FILE_CHUNK) + messageId.toByteArray(Charsets.US_ASCII) + intBytes(index) + data
    }

    companion object {
        private const val TAG_TEXT: Byte = 0
        private const val TAG_DELIVERED_ACK: Byte = 1
        private const val TAG_READ_ACK: Byte = 2
        private const val TAG_FILE_META: Byte = 3
        private const val TAG_FILE_CHUNK: Byte = 4
        private const val UUID_LENGTH = 36

        /** Тег + UUID + индекс: заголовок [FileChunk] до данных. Нужен для расчёта размера чанка. */
        const val FILE_CHUNK_OVERHEAD = 1 + UUID_LENGTH + 4

        // FileMeta: [тег][UUID 36][totalSize 8][totalChunks 4][chunkSize 4][mimeLen 1][mime][имя UTF-8 до конца]
        private const val FILE_META_FIXED = UUID_LENGTH + 8 + 4 + 4 + 1

        private fun intBytes(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())

        private fun readInt(b: ByteArray, o: Int) =
            ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
                ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)

        private fun readLong(b: ByteArray, o: Int): Long {
            var r = 0L
            for (i in 0 until 8) r = (r shl 8) or (b[o + i].toLong() and 0xFF)
            return r
        }

        private fun encodeFileMeta(m: FileMeta): ByteArray {
            val mime = m.mimeType.toByteArray(Charsets.UTF_8)
            require(mime.size <= 255) { "MIME-тип длиннее 255 байт" }
            val size = m.totalSize
            val sizeBytes = ByteArray(8) { i -> (size ushr (56 - 8 * i)).toByte() }
            return byteArrayOf(TAG_FILE_META) + m.messageId.toByteArray(Charsets.US_ASCII) + sizeBytes +
                intBytes(m.totalChunks) + intBytes(m.chunkSize) + byteArrayOf(mime.size.toByte()) +
                mime + m.fileName.toByteArray(Charsets.UTF_8)
        }

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
                TAG_FILE_META -> {
                    if (payload.size < FILE_META_FIXED) return null
                    val totalSize = readLong(payload, UUID_LENGTH)
                    val totalChunks = readInt(payload, UUID_LENGTH + 8)
                    val chunkSize = readInt(payload, UUID_LENGTH + 12)
                    val mimeLen = payload[UUID_LENGTH + 16].toInt() and 0xFF
                    val mimeStart = FILE_META_FIXED
                    if (mimeStart + mimeLen > payload.size) return null
                    if (totalSize < 0 || totalChunks < 0 || chunkSize <= 0) return null
                    FileMeta(
                        messageId = payload.copyOfRange(0, UUID_LENGTH).toString(Charsets.US_ASCII),
                        fileName = payload.copyOfRange(mimeStart + mimeLen, payload.size).toString(Charsets.UTF_8),
                        totalSize = totalSize,
                        totalChunks = totalChunks,
                        chunkSize = chunkSize,
                        mimeType = payload.copyOfRange(mimeStart, mimeStart + mimeLen).toString(Charsets.UTF_8)
                    )
                }
                TAG_FILE_CHUNK -> {
                    if (payload.size < UUID_LENGTH + 4) return null
                    val index = readInt(payload, UUID_LENGTH)
                    if (index < 0) return null
                    FileChunk(
                        messageId = payload.copyOfRange(0, UUID_LENGTH).toString(Charsets.US_ASCII),
                        index = index,
                        data = payload.copyOfRange(UUID_LENGTH + 4, payload.size)
                    )
                }
                else -> null
            }
        }
    }
}
