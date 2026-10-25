package com.ramka.domain.usecase

import com.ramka.domain.model.ContactConstants
import com.ramka.domain.model.Message
import com.ramka.domain.model.MessageBody
import com.ramka.domain.model.MessageDirection
import com.ramka.domain.model.MessageStatus
import com.ramka.domain.repository.MessageRepository
import com.ramka.domain.util.FileNames
import java.util.UUID

/**
 * Заметки в «Избранном»: только локальное сохранение. Ни Outbox, ни транспорт, ни FileTransferManager
 * не участвуют. Статус сразу [MessageStatus.SENT]: записи SENDING/FAILED подхватываются очередью и
 * повторными отправками, а этим сообщениям там делать нечего.
 */
class SaveSelfNoteUseCase(private val messageRepository: MessageRepository) {

    suspend fun saveText(text: String, nowEpochMillis: Long = System.currentTimeMillis()): Message =
        save(MessageBody.Text(text), nowEpochMillis)

    /** [localUri] — копия файла во внутреннем хранилище (outgoing_files), её открывает FileBubble как обычный исходящий файл. */
    suspend fun saveFile(
        fileName: String,
        fileSize: Long,
        mimeType: String,
        localUri: String,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): Message = save(
        MessageBody.File(
            fileName = FileNames.sanitizeFileName(fileName),
            fileSize = fileSize,
            mimeType = FileNames.sanitizeMimeType(mimeType),
            uri = localUri,
            progress = 100
        ),
        nowEpochMillis
    )

    private suspend fun save(body: MessageBody, nowEpochMillis: Long): Message {
        val message = Message(
            localId = UUID.randomUUID().toString(),
            contactId = ContactConstants.SELF_CONTACT_ID,
            direction = MessageDirection.OUTGOING,
            body = body,
            status = MessageStatus.SENT,
            localCreatedAtEpochMillis = nowEpochMillis
        )
        messageRepository.saveOutgoing(message)
        return message
    }
}
