package com.ramka.domain.usecase

import com.ramka.domain.model.AppMessage
import com.ramka.domain.model.Message
import com.ramka.domain.model.MessageBody
import com.ramka.domain.model.MessageDirection
import com.ramka.domain.model.MessageStatus
import com.ramka.domain.repository.ContactRepository
import com.ramka.domain.repository.IncomingFileStore
import com.ramka.domain.repository.MessageRepository
import com.ramka.domain.util.FileNames
import com.ramka.domain.util.FileTransferConfig
import com.ramka.domain.util.FileTransferTracker
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Приём файла (этап 6.4): [AppMessage.FileMeta] заводит входящее сообщение (SENDING, «Загрузка…»)
 * и временный файл; каждый [AppMessage.FileChunk] пишется по смещению index * chunkSize
 * (каждый пакет — отдельное соединение, порядок их обработки не гарантирован); когда собраны все
 * чанки, файл публикуется ([IncomingFileStore.publish]), сообщение становится DELIVERED с Uri
 * файла, растёт счётчик непрочитанных и уходит DELIVERED-ACK — тот же механизм, что у текста.
 *
 * Недоверенный ввод: размеры и число чанков проверяются, имя и MIME очищаются ([FileNames]), чанк
 * неверной длины обрывает передачу, чанки неизвестной передачи отбрасываются молча. Одновременных
 * приёмов не больше [FileTransferConfig.MAX_ACTIVE_INCOMING].
 *
 * Повторные метаданные того же messageId: если файл уже принят — только повторный ACK (у отправителя
 * мог потеряться ACK), иначе приём начинается заново. Состояние активных приёмов — в памяти; при
 * перезапуске процесса незавершённые помечаются FAILED (MessageRepository.failInterruptedFileTransfers).
 */
class HandleIncomingFileUseCase(
    private val messageRepository: MessageRepository,
    private val contactRepository: ContactRepository,
    private val store: IncomingFileStore,
    private val tracker: FileTransferTracker,
    /** Источник времени для вытеснения брошенных приёмов (в тестах подменяется). */
    private val clock: () -> Long = { System.currentTimeMillis() },
    /** true, если чат с этим контактом сейчас виден — тогда счётчик непрочитанных не растёт. */
    private val isChatOpen: (contactId: String) -> Boolean = { false }
) {
    private class Active(
        val localId: String,
        val remoteId: String,
        val displayName: String,
        val mimeType: String,
        val totalSize: Long,
        val totalChunks: Int,
        val chunkSize: Int
    ) {
        val received = BooleanArray(totalChunks)
        var receivedCount = 0
        var lastActivityMillis = 0L
    }

    private val mutex = Mutex()
    private val active = HashMap<String, Active>()

    private fun key(contactId: String, remoteId: String) = "$contactId/$remoteId"

    suspend fun onMeta(
        contactId: String,
        meta: AppMessage.FileMeta,
        nowEpochMillis: Long,
        acknowledge: suspend (senderMessageId: String) -> Unit
    ) = mutex.withLock {
        val valid = meta.totalSize in 1..FileTransferConfig.MAX_FILE_SIZE_BYTES &&
            meta.chunkSize in 1..FileTransferConfig.MAX_ACCEPTED_CHUNK_SIZE &&
            meta.totalChunks == FileTransferConfig.chunkCount(meta.totalSize, meta.chunkSize)
        if (!valid) return@withLock

        val key = key(contactId, meta.messageId)
        val existing = messageRepository.findIncomingByRemoteId(contactId, meta.messageId)

        if (existing != null) {
            if (existing.body !is MessageBody.File) return@withLock
            if (existing.status == MessageStatus.DELIVERED || existing.status == MessageStatus.READ) {
                acknowledge(meta.messageId) // уже принят: отправитель повторяет из-за потерянного ACK
                return@withLock
            }
        }
        // Новый приём или перезапуск прерванного: лимит одновременных (перезапуск своей записи не считается).
        if (key !in active && active.size >= FileTransferConfig.MAX_ACTIVE_INCOMING) {
            // Слоты заняты: брошенные отправителем приёмы (давно нет чанков) освобождают место.
            val now = clock()
            active.entries
                .filter { now - it.value.lastActivityMillis > FileTransferConfig.STALE_INCOMING_MILLIS }
                .map { it.key to it.value }
                .forEach { (staleKey, stale) -> fail(staleKey, stale) }
            if (active.size >= FileTransferConfig.MAX_ACTIVE_INCOMING) return@withLock
        }

        val localId = existing?.localId ?: UUID.randomUUID().toString()
        active.remove(key)
        store.discard(localId)
        if (!store.begin(localId)) {
            if (existing != null) failExisting(localId, 0)
            return@withLock
        }

        val name = FileNames.sanitizeFileName(meta.fileName)
        val mime = FileNames.sanitizeMimeType(meta.mimeType)
        if (existing == null) {
            messageRepository.saveIncoming(
                Message(
                    localId = localId,
                    contactId = contactId,
                    direction = MessageDirection.INCOMING,
                    body = MessageBody.File(name, meta.totalSize, mime, uri = null, progress = 0),
                    status = MessageStatus.SENDING,
                    localCreatedAtEpochMillis = nowEpochMillis,
                    remoteMessageId = meta.messageId
                )
            )
        } else {
            messageRepository.updateFileProgress(localId, 0)
            messageRepository.updateStatus(localId, MessageStatus.SENDING)
        }
        active[key] = Active(localId, meta.messageId, name, mime, meta.totalSize, meta.totalChunks, meta.chunkSize)
            .also { it.lastActivityMillis = clock() }
        tracker.update(localId, 0)
    }

    suspend fun onChunk(
        contactId: String,
        chunk: AppMessage.FileChunk,
        acknowledge: suspend (senderMessageId: String) -> Unit,
        onCompleted: suspend (Message) -> Unit
    ) = mutex.withLock {
        val key = key(contactId, chunk.messageId)
        val transfer = active[key] ?: return@withLock // неизвестная или уже завершённая передача

        if (chunk.index >= transfer.totalChunks) return@withLock fail(key, transfer)
        val expectedLength = if (chunk.index < transfer.totalChunks - 1) {
            transfer.chunkSize.toLong()
        } else {
            transfer.totalSize - chunk.index.toLong() * transfer.chunkSize
        }
        if (chunk.data.size.toLong() != expectedLength) return@withLock fail(key, transfer)
        if (transfer.received[chunk.index]) return@withLock // дубликат

        if (!store.write(transfer.localId, chunk.index.toLong() * transfer.chunkSize, chunk.data)) {
            return@withLock fail(key, transfer)
        }
        transfer.received[chunk.index] = true
        transfer.receivedCount++
        transfer.lastActivityMillis = clock()

        if (transfer.receivedCount < transfer.totalChunks) {
            tracker.update(transfer.localId, (transfer.receivedCount * 100L / transfer.totalChunks).toInt())
            return@withLock
        }

        val uri = store.publish(transfer.localId, transfer.displayName, transfer.mimeType)
            ?: return@withLock fail(key, transfer)

        active.remove(key)
        messageRepository.updateFileUri(transfer.localId, uri)
        messageRepository.updateFileProgress(transfer.localId, 100)
        messageRepository.updateStatus(transfer.localId, MessageStatus.DELIVERED)
        tracker.clear(transfer.localId)
        if (!isChatOpen(contactId)) contactRepository.incrementUnreadCount(contactId)
        acknowledge(transfer.remoteId)
        messageRepository.getMessage(transfer.localId)?.let { onCompleted(it) }
        Unit
    }

    private suspend fun fail(key: String, transfer: Active) {
        active.remove(key)
        store.discard(transfer.localId)
        failExisting(transfer.localId, (transfer.receivedCount * 100L / transfer.totalChunks).toInt())
    }

    private suspend fun failExisting(localId: String, percent: Int) {
        messageRepository.updateFileProgress(localId, percent)
        messageRepository.updateStatus(localId, MessageStatus.FAILED)
        tracker.clear(localId)
    }
}
