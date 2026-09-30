package com.ramka.domain.usecase

import com.ramka.domain.model.AppMessage
import com.ramka.domain.model.Message
import com.ramka.domain.model.MessageBody
import com.ramka.domain.model.MessageDirection
import com.ramka.domain.model.MessageStatus
import com.ramka.domain.repository.ContactRepository
import com.ramka.domain.repository.MessageRepository
import java.util.UUID

/**
 * Сохраняет входящее текстовое сообщение ИДЕМПОТЕНТНО по `remoteMessageId`
 * (ЭТАП B, ревью п.2): повторный приём того же `messageId` от отправителя
 * (например, ретрай после крэша отправителя до того, как он снял запись Outbox)
 * не создаёт вторую строку в UI.
 *
 * DELIVERED-ACK подтверждается ВСЕГДА, в том числе на дубликат: у отправителя мог
 * потеряться именно ACK, и повторный ACK — механизм починки такой потери. Сама
 * отправка ACK — колбэк [acknowledge] (получает только messageId отправителя —
 * ни времени, ни ID контакта, ни содержимого), чтобы решение "подтверждать всегда"
 * жило здесь, в domain, и проверялось pure-JVM тестом, а не пряталось в
 * Android-зависимом IncomingMessageProcessor. Колбэк не должен блокировать
 * вызывающего на сетевой попытке (реализация запускает её в отдельной корутине).
 */
class HandleIncomingTextUseCase(
    private val messageRepository: MessageRepository,
    private val contactRepository: ContactRepository,
    /** true, если чат с этим контактом сейчас виден — тогда счётчик непрочитанных не растёт. */
    private val isChatOpen: (contactId: String) -> Boolean = { false }
) {
    /** @return true, если сообщение реально новое и сохранено; false — дубликат (запись не создавалась). */
    suspend operator fun invoke(
        contactId: String,
        text: AppMessage.Text,
        nowEpochMillis: Long,
        acknowledge: suspend (senderMessageId: String) -> Unit
    ): Boolean {
        val existing = messageRepository.findIncomingByRemoteId(contactId, text.messageId)
        val isNew = existing == null

        if (isNew) {
            messageRepository.saveIncoming(
                Message(
                    localId = UUID.randomUUID().toString(),
                    contactId = contactId,
                    direction = MessageDirection.INCOMING,
                    body = MessageBody.Text(text.text),
                    status = MessageStatus.DELIVERED,
                    localCreatedAtEpochMillis = nowEpochMillis,
                    remoteMessageId = text.messageId
                )
            )
            // Badge непрочитанных (UX-правка) — только на реально новое сообщение,
            // повторный приём дубликата счётчик больше не крутит.
            // Если чат с этим контактом сейчас открыт — пользователь видит сообщение сразу,
            // в непрочитанные оно не попадает.
            if (!isChatOpen(contactId)) contactRepository.incrementUnreadCount(contactId)
        }

        acknowledge(text.messageId) // всегда — и на новое, и на дубликат
        return isNew
    }
}
