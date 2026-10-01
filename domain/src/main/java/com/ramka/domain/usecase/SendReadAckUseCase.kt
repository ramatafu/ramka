package com.ramka.domain.usecase

import com.ramka.domain.model.AppMessage
import com.ramka.domain.model.MessageStatus
import com.ramka.domain.repository.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * READ-ACK при показе чата (ЭТАП B.5, ревью п.4; джиттер — этап 2.5).
 *
 * Порядок важен: флаг `readAckSent` проставляется ДО отложенной отправки. Тогда повторное
 * открытие чата во время задержки не найдёт эти сообщения в `incomingWithoutReadAck` и не пошлёт
 * дубль. Выбор сообщений и простановка флагов идут под [Mutex] и не отменяются
 * ([NonCancellable]): два подряд вызова не увидят одни и те же сообщения, а отмена на полпути
 * не оставит часть сообщений помеченными без ACK.
 *
 * Отложенная отправка запускается в [ackScope] (живёт столько же, сколько приложение), а не в
 * скоупе вызывающего: если экран закроется во время задержки, ACK всё равно уйдёт, флаг-то уже стоит.
 *
 * Известное ограничение (как и до джиттера): если после проставления флага отправка не удалась
 * (собеседник офлайн) или процесс убит во время задержки, ACK не повторяется.
 */
class SendReadAckUseCase(
    private val messageRepository: MessageRepository,
    private val ackScope: CoroutineScope,
    private val sendAck: suspend (contactId: String, payload: ByteArray) -> Boolean
) {
    private val mutex = Mutex()

    suspend operator fun invoke(contactId: String) {
        val remoteIds = withContext(NonCancellable) {
            mutex.withLock {
                val eligible = messageRepository.incomingWithoutReadAck(contactId).filter {
                    it.status == MessageStatus.DELIVERED || it.status == MessageStatus.READ
                }
                val ids = eligible.mapNotNull { it.remoteMessageId }
                if (ids.isEmpty()) {
                    null
                } else {
                    eligible.forEach { messageRepository.markReadAckSent(it.localId) }
                    ids
                }
            }
        } ?: return

        ackScope.launch { sendAck(contactId, AppMessage.ReadAck(remoteIds).encode()) }
    }
}
