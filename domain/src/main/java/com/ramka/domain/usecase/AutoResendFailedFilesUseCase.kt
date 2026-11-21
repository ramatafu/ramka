package com.ramka.domain.usecase

import com.ramka.domain.model.MessageBody
import com.ramka.domain.model.MessageDirection
import com.ramka.domain.model.MessageStatus
import com.ramka.domain.repository.ContactRepository
import com.ramka.domain.repository.MessageRepository
import com.ramka.domain.util.FileTransferConfig
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Связь появилась: повторяет исходящие файлы и голосовые, которые остались FAILED. По одному, тихо (без текстового
 * сигнала собеседнику), не больше [FileTransferConfig.MAX_AUTO_RETRIES] автопопыток на сообщение и только для
 * файлов до [maxFileBytes]. Пропускаются: удалённое сообщение, удалённый контакт, «Избранное», сообщение, которое
 * уже не FAILED. Параллельный второй вызов ничего не делает: идёт уже работающий проход.
 */
class AutoResendFailedFilesUseCase(
    private val messageRepository: MessageRepository,
    private val contactRepository: ContactRepository,
    private val transfers: FileTransferManager,
    private val maxFileBytes: Long = FileTransferConfig.AUTO_RETRY_MAX_FILE_BYTES
) {
    private val running = AtomicBoolean(false)

    /** @return сколько передач запущено. */
    suspend operator fun invoke(): Int {
        if (!running.compareAndSet(false, true)) return 0
        try {
            var started = 0
            for (candidate in messageRepository.failedOutgoingFiles(maxFileBytes)) {
                currentCoroutineContext().ensureActive()
                // Данные могли измениться, пока шла предыдущая передача: смотрим на свежие.
                val message = messageRepository.getMessage(candidate.localId) ?: continue // удалено пользователем
                if (message.direction != MessageDirection.OUTGOING || message.status != MessageStatus.FAILED) continue
                val body = message.body as? MessageBody.File ?: continue
                if (body.fileSize > maxFileBytes) continue
                val contact = contactRepository.getContact(message.contactId) ?: continue // контакт удалён
                if (contact.isSelf) continue
                val transfer = transfers.startAutoRetry(message.localId) ?: continue
                started++
                transfer.join() // по одному: без одновременной бури передач
            }
            return started
        } finally {
            running.set(false)
        }
    }
}
