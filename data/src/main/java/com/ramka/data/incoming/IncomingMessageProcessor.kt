package com.ramka.data.incoming

import android.util.Base64
import com.ramka.domain.model.*
import com.ramka.domain.repository.ContactRepository
import com.ramka.domain.repository.MessageRepository
import com.ramka.domain.repository.TransportRepository
import com.ramka.domain.usecase.AttemptDeliveryUseCase
import com.ramka.domain.usecase.HandleIncomingTextUseCase
import com.ramka.domain.util.ActiveChatTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Единственный на всё приложение подписчик на входящие пакеты. К этому моменту
 * [TransportRepository] уже провёл IK-рукопожатие, проверил подпись транскрипта и
 * расшифровал тело (см. SecureLanChannel в network-модуле) — здесь: сопоставление
 * отправителя с локальным контактом по публичному ключу (п. 3.1 — контакт
 * существует только локально, глобального ID нет), декодирование прикладного
 * конверта [AppMessage] и обработка по типу (текст / DELIVERED-ACK / READ-ACK,
 * ЭТАП B.5). Отправитель, которого нет в списке контактов, уже отклонён на
 * уровне транспорта до расшифровки (п. 3.8) — сюда такие пакеты не попадают.
 */
class IncomingMessageProcessor(
    private val contactRepository: ContactRepository,
    private val messageRepository: MessageRepository,
    private val transportRepository: TransportRepository,
    private val activeChat: ActiveChatTracker = ActiveChatTracker(),
    /**
     * Вызывается ПОСЛЕ сохранения НОВОГО (не дублирующего) входящего текстового
     * сообщения — единственная точка интеграции с уведомлениями (ЭТАП B.7). На
     * дубликат по remoteMessageId НЕ вызывается — дубликат не должен спамить
     * уведомлением. Реализация в app-слое обязана показывать только фиксированный
     * текст "ramka — Новое сообщение", без псевдонима и содержимого. По умолчанию —
     * no-op, чтобы старые вызовы конструктора не ломались.
     */
    private val onIncomingTextSaved: suspend (Message) -> Unit = {}
) {
    private val attemptDelivery = AttemptDeliveryUseCase(contactRepository, transportRepository)
    private val handleIncomingText = HandleIncomingTextUseCase(messageRepository, contactRepository) { activeChat.isOpen(it) }

    fun start(scope: CoroutineScope) {
        transportRepository.incomingPackets()
            .onEach { (senderPublicKey, plaintext) -> handle(scope, senderPublicKey, plaintext) }
            .launchIn(scope)
    }

    private suspend fun handle(scope: CoroutineScope, senderPublicKey: ByteArray, plaintext: ByteArray) {
        val senderKeyB64 = Base64.encodeToString(senderPublicKey, Base64.NO_WRAP)
        val contact = findContactByPublicKey(senderKeyB64) ?: return

        val appMessage = AppMessage.decode(plaintext) ?: return // повреждённый/незнакомый конверт — отбрасываем

        when (appMessage) {
            is AppMessage.Text -> handleIncomingTextMessage(scope, contact, appMessage)
            is AppMessage.DeliveredAck -> advanceStatus(appMessage.messageLocalId, MessageStatus.DELIVERED)
            is AppMessage.ReadAck -> appMessage.messageLocalIds.forEach { advanceStatus(it, MessageStatus.READ) }
        }
    }

    private suspend fun handleIncomingTextMessage(scope: CoroutineScope, contact: Contact, text: AppMessage.Text) {
        val isNew = handleIncomingText(contact.localId, text, System.currentTimeMillis()) { senderMessageId ->
            // DELIVERED-ACK — best-effort, НЕ через Outbox, ВСЕГДА (в т.ч. на дубликат) и в
            // отдельной корутине, чтобы сетевая попытка не тормозила приём следующих
            // пакетов (см. HandleIncomingTextUseCase и DEVIATIONS.md).
            scope.launch {
                attemptDelivery(contact.localId, AppMessage.DeliveredAck(senderMessageId).encode())
            }
        }

        if (isNew) {
            val saved = messageRepository.findIncomingByRemoteId(contact.localId, text.messageId)
            if (saved != null) onIncomingTextSaved(saved)
        }
    }

    /** Обновляет статус, только если это движение ВПЕРЁД (SENT -> DELIVERED -> READ), не назад. */
    private suspend fun advanceStatus(messageLocalId: String, newStatus: MessageStatus) {
        val current = messageRepository.getMessage(messageLocalId) ?: return
        if (rank(newStatus) > rank(current.status)) {
            messageRepository.updateStatus(messageLocalId, newStatus)
        }
    }

    private fun rank(status: MessageStatus): Int = when (status) {
        MessageStatus.SENDING -> 0
        MessageStatus.FAILED -> 0
        MessageStatus.SENT -> 1
        MessageStatus.DELIVERED -> 2
        MessageStatus.READ -> 3
    }

    private suspend fun findContactByPublicKey(publicKeyB64: String): Contact? {
        // Для MVP — линейный поиск по текущему снимку локального списка; список
        // контактов небольшой, оптимизация индексом по ключу — не приоритет этапа 1.
        val contacts = contactRepository.observeContacts().first()
        return contacts.firstOrNull { Base64.encodeToString(it.publicKey, Base64.NO_WRAP) == publicKeyB64 }
    }
}
