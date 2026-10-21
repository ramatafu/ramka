package com.ramka.app.ui.chat

import com.ramka.domain.model.Message
import com.ramka.domain.model.MessageBody
import com.ramka.domain.model.MessageDirection
import com.ramka.domain.model.MessageStatus

/**
 * Данные для окна «Информация». Точных времён доставки и прочтения здесь нет НАМЕРЕННО: в [Message] их
 * нет (запрет 6.4 в PROJECT_SPEC.md — точное время событий не хранится), есть только время создания
 * записи и текущий статус.
 */
data class MessageInfo(
    val isOutgoing: Boolean,
    val createdAtEpochMillis: Long,
    val statusLabel: String,
    /** Размер файла в байтах; null для текста. */
    val fileSizeBytes: Long?,
    val localId: String,
    /** Только у входящих: ID этого сообщения у отправителя. */
    val remoteMessageId: String?
)

fun Message.toInfo(): MessageInfo = MessageInfo(
    isOutgoing = direction == MessageDirection.OUTGOING,
    createdAtEpochMillis = localCreatedAtEpochMillis,
    statusLabel = statusLabel(direction, status),
    fileSizeBytes = (body as? MessageBody.File)?.fileSize,
    localId = localId,
    remoteMessageId = remoteMessageId
)

internal fun statusLabel(direction: MessageDirection, status: MessageStatus): String = when (direction) {
    MessageDirection.OUTGOING -> when (status) {
        MessageStatus.SENDING -> "Отправляется"
        MessageStatus.SENT -> "Отправлено"
        MessageStatus.DELIVERED -> "Доставлено"
        MessageStatus.READ -> "Прочитано"
        MessageStatus.FAILED -> "Не удалось отправить"
    }
    MessageDirection.INCOMING -> when (status) {
        MessageStatus.SENDING -> "Принимается"
        MessageStatus.FAILED -> "Приём прервался"
        MessageStatus.SENT, MessageStatus.DELIVERED, MessageStatus.READ -> "Получено"
    }
}
