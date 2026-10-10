package com.ramka.domain.model

/**
 * Сообщение в переписке с контактом.
 *
 * Намеренно НЕТ полей вроде `sentAtExact`, `deliveredAtExact` с точным временем —
 * см. запрет 6.4 в PROJECT_SPEC.md. Время создания хранится локально для
 * отображения в пузыре, но не передаётся по сети как метаданные вне
 * зашифрованного тела сообщения.
 *
 * @property remoteMessageId только для входящих (direction=INCOMING): localId,
 *   под которым ЭТО ЖЕ сообщение известно отправителю (глобального ID сообщений
 *   нет — см. §3.1, у каждой стороны свой localId для одной и той же переписки).
 *   Именно это значение нужно эхом отправить обратно в DeliveredAck/ReadAck,
 *   чтобы отправитель смог сопоставить подтверждение со своей записью в Outbox/БД.
 *   Для исходящих сообщений всегда null — там localId и есть значение, которое
 *   отправитель ждёт увидеть в чужом ACK.
 * @property readAckSent только для входящих: отправлялся ли уже ReadAck за это
 *   сообщение (чтобы не слать его повторно при каждом открытии чата).
 */
data class Message(
    val localId: String,
    val contactId: String,
    val direction: MessageDirection,
    val body: MessageBody,
    val status: MessageStatus,
    val localCreatedAtEpochMillis: Long,
    val deleteAfterReadSeconds: Long? = null,
    val remoteMessageId: String? = null,
    val readAckSent: Boolean = false
)

enum class MessageDirection { OUTGOING, INCOMING }

sealed class MessageBody {
    data class Text(val text: String) : MessageBody()

    /**
     * Файловое сообщение (этап 6). Направление и время берутся из [Message]
     * (direction / localCreatedAtEpochMillis) — здесь только данные файла.
     *
     * @property fileSize размер в байтах.
     * @property uri локальный URI файла: file:// (исходящий — копия в filesDir) или
     *   content:// (принятый — запись в MediaStore); null, пока файл не получен целиком.
     * @property progress ход передачи, 0..100; UI сам ограничивает значение диапазоном.
     *   Живое значение приходит из FileTransferTracker, в БД — последнее сохранённое.
     *
     * Статус файлового сообщения: SENDING — идёт передача (исходящая или входящая),
     * FAILED — передача оборвалась, дальше как у текста (SENT/DELIVERED/READ).
     * Входящий полностью принятый файл — DELIVERED, как и входящий текст.
     */
    data class File(
        val fileName: String,
        val fileSize: Long,
        val mimeType: String,
        val uri: String? = null,
        val progress: Int = 0
    ) : MessageBody()
}

/** Статусы доставки. Никогда не сопровождаются точным временем на UI. */
enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}
