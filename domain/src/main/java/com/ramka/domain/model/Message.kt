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
    // Изображения — этап 6.
}

/** Статусы доставки. Никогда не сопровождаются точным временем на UI. */
enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}
