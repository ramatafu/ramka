package com.ramka.domain.usecase

import com.ramka.domain.model.AppMessage
import com.ramka.domain.model.Message
import com.ramka.domain.model.MessageBody
import com.ramka.domain.model.MessageDirection
import com.ramka.domain.model.MessageStatus
import com.ramka.domain.repository.MessageRepository
import com.ramka.domain.util.FileNames
import com.ramka.domain.util.FileTransferConfig
import com.ramka.domain.util.FileTransferTracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.URI
import java.util.UUID

/**
 * Отправка файла контакту (этап 6.4): сначала пакет [AppMessage.FileMeta], затем чанки
 * [AppMessage.FileChunk] строго по порядку, по одному, через тот же зашифрованный канал, что и
 * текст ([send] = AttemptDeliveryUseCase: полное IK-рукопожатие на каждый пакет, D-3/D-6 не
 * затрагиваются). Файлы в Outbox НЕ попадают: ProcessOutboxUseCase их по-прежнему пропускает,
 * повтор — только вручную кнопкой «Повторить» (с начала: приёмник начинает заново при повторе
 * метаданных с тем же messageId).
 *
 * Прогресс: живой — в [FileTransferTracker], в БД — один раз в конце (успех 100 / сбой — последний
 * достигнутый). Статусы: SENDING на время передачи, затем SENT (все пакеты ушли) или FAILED.
 * DELIVERED/READ приходят отдельными ACK, как у текста. Приёмник подтверждает файл целиком
 * (DELIVERED-ACK) только после сборки всех чанков.
 */
class SendFileUseCase(
    private val messageRepository: MessageRepository,
    private val tracker: FileTransferTracker,
    private val send: suspend (contactId: String, payload: ByteArray) -> Boolean,
    private val pause: suspend (millis: Long) -> Unit = { delay(it) }
) {
    /** Создаёт и сохраняет исходящее файловое сообщение (SENDING, прогресс 0). Сеть не трогает. */
    suspend fun createOutgoing(
        contactId: String,
        fileName: String,
        fileSize: Long,
        mimeType: String,
        localUri: String,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): Message {
        val message = Message(
            localId = UUID.randomUUID().toString(),
            contactId = contactId,
            direction = MessageDirection.OUTGOING,
            body = MessageBody.File(
                fileName = FileNames.sanitizeFileName(fileName),
                fileSize = fileSize,
                mimeType = FileNames.sanitizeMimeType(mimeType),
                uri = localUri,
                progress = 0
            ),
            status = MessageStatus.SENDING,
            localCreatedAtEpochMillis = nowEpochMillis
        )
        messageRepository.saveOutgoing(message)
        return message
    }

    /**
     * Передаёт файл сообщения [messageLocalId]; безопасно вызывать и для первой отправки, и для
     * повтора из FAILED. @return true, если все пакеты ушли (статус SENT).
     */
    suspend fun transfer(messageLocalId: String): Boolean {
        val message = messageRepository.getMessage(messageLocalId) ?: return false
        val body = message.body as? MessageBody.File ?: return false
        if (message.direction != MessageDirection.OUTGOING) return false

        messageRepository.updateStatus(messageLocalId, MessageStatus.SENDING)
        messageRepository.updateFileProgress(messageLocalId, 0)
        tracker.update(messageLocalId, 0)

        var lastPercent = 0
        try {
            val ok = sendAll(message, body) { percent ->
                lastPercent = percent
                tracker.update(messageLocalId, percent)
            }
            if (ok) {
                messageRepository.updateFileProgress(messageLocalId, 100)
                messageRepository.updateStatus(messageLocalId, MessageStatus.SENT)
            } else {
                markFailed(messageLocalId, lastPercent)
            }
            return ok
        } catch (e: CancellationException) {
            withContext(NonCancellable) { markFailed(messageLocalId, lastPercent) }
            throw e
        } catch (e: Exception) {
            markFailed(messageLocalId, lastPercent)
            return false
        } finally {
            tracker.clear(messageLocalId)
        }
    }

    private suspend fun markFailed(id: String, percent: Int) {
        messageRepository.updateFileProgress(id, percent)
        messageRepository.updateStatus(id, MessageStatus.FAILED)
    }

    private suspend fun sendAll(message: Message, body: MessageBody.File, onProgress: (Int) -> Unit): Boolean {
        val file = localFile(body.uri) ?: return false
        if (!file.isFile) return false
        val size = file.length()
        if (size <= 0 || size > FileTransferConfig.MAX_FILE_SIZE_BYTES) return false

        val chunkSize = FileTransferConfig.CHUNK_SIZE
        val chunkCount = FileTransferConfig.chunkCount(size, chunkSize)

        val meta = AppMessage.FileMeta(
            messageId = message.localId,
            fileName = FileNames.sanitizeFileName(body.fileName),
            totalSize = size,
            totalChunks = chunkCount,
            chunkSize = chunkSize,
            mimeType = FileNames.sanitizeMimeType(body.mimeType)
        )
        if (!sendWithRetry(message.contactId, meta.encode())) return false

        FileInputStream(file).use { input ->
            val buffer = ByteArray(chunkSize)
            for (index in 0 until chunkCount) {
                val expected = if (index < chunkCount - 1) chunkSize else (size - index.toLong() * chunkSize).toInt()
                // Файл укоротили/изменили во время передачи — продолжать нельзя, приёмник получит мусор.
                if (!readFully(input, buffer, expected)) return false
                val chunk = AppMessage.FileChunk(message.localId, index, buffer.copyOf(expected))
                if (!sendWithRetry(message.contactId, chunk.encode())) return false
                onProgress(((index + 1) * 100L / chunkCount).toInt())
            }
        }
        return true
    }

    private suspend fun sendWithRetry(contactId: String, payload: ByteArray): Boolean {
        val attempts = FileTransferConfig.SEND_ATTEMPTS_PER_PACKET
        for (attempt in 1..attempts) {
            if (send(contactId, payload)) return true
            if (attempt < attempts) pause(FileTransferConfig.RETRY_PAUSE_MILLIS * attempt)
        }
        return false
    }

    private fun readFully(input: InputStream, buffer: ByteArray, length: Int): Boolean {
        var offset = 0
        while (offset < length) {
            val n = input.read(buffer, offset, length - offset)
            if (n < 0) return false
            offset += n
        }
        return true
    }

    /** Поддерживается только file:// — копия во внутреннем хранилище (шаг 6.3). */
    private fun localFile(uri: String?): File? =
        uri?.let { runCatching { File(URI(it)) }.getOrNull() }
}
