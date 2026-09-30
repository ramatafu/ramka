package com.ramka.domain.usecase

import com.ramka.domain.model.*
import com.ramka.domain.repository.ContactRepository
import com.ramka.domain.repository.MessageRepository
import com.ramka.domain.repository.OutboxRepository
import java.util.UUID

/**
 * Отправляет текстовое сообщение контакту. Сообщение сразу пытается уйти по сети
 * (см. [AttemptDeliveryUseCase] — полное IK-рукопожатие, как и раньше); если
 * попытка не удалась (контакт офлайн/недостижим), сообщение остаётся в Outbox
 * (§4.5, ЭТАП B.1) и переживёт перезапуск процесса — дальнейшие попытки берёт на
 * себя [ProcessOutboxUseCase] по триггерам из ЭТАП B.2 (обнаружение пира,
 * периодический sweep, ручное обновление).
 */
class SendMessageUseCase(
    private val contactRepository: ContactRepository,
    private val messageRepository: MessageRepository,
    private val outboxRepository: OutboxRepository,
    private val attemptDelivery: AttemptDeliveryUseCase
) {
    suspend operator fun invoke(contactId: String, text: String, nowEpochMillis: Long = System.currentTimeMillis()): Message {
        requireNotNull(contactRepository.getContact(contactId)) {
            "Контакт $contactId не найден"
        }

        val message = Message(
            localId = UUID.randomUUID().toString(),
            contactId = contactId,
            direction = MessageDirection.OUTGOING,
            body = MessageBody.Text(text),
            status = MessageStatus.SENDING,
            localCreatedAtEpochMillis = nowEpochMillis
        )
        messageRepository.saveOutgoing(message)

        outboxRepository.enqueue(
            OutboxEntry(
                messageLocalId = message.localId,
                contactId = contactId,
                attemptCount = 0,
                nextAttemptAtEpochMillis = nowEpochMillis,
                createdAtEpochMillis = nowEpochMillis
            )
        )

        val delivered = attemptDelivery(contactId, AppMessage.Text(message.localId, text).encode())
        if (delivered) {
            messageRepository.updateStatus(message.localId, MessageStatus.SENT)
            outboxRepository.remove(message.localId)
        }
        // При неудаче запись в Outbox остаётся как есть (attemptCount=0,
        // nextAttempt=nowEpochMillis) — её подхватит ближайший триггер ЭТАП B.2,
        // не отдельная логика retry прямо здесь.

        return message
    }
}
