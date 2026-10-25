package com.ramka.data.repository

import com.ramka.domain.model.*
import com.ramka.domain.repository.MessageRepository
import com.ramka.storage.db.MessageDao
import com.ramka.storage.db.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MessageRepositoryImpl(private val dao: MessageDao) : MessageRepository {

    override fun observeMessages(contactId: String): Flow<List<Message>> =
        dao.observeForContact(contactId).map { list -> list.map { it.toDomain() } }

    override suspend fun getMessage(localId: String): Message? =
        dao.getById(localId)?.toDomain()

    override suspend fun saveOutgoing(message: Message) {
        dao.upsert(message.toEntity())
    }

    override suspend fun saveIncoming(message: Message) {
        dao.upsert(message.toEntity())
    }

    override suspend fun updateStatus(localId: String, status: MessageStatus) {
        dao.updateStatus(localId, status.name)
    }

    override suspend fun markReadAckSent(localId: String) {
        dao.markReadAckSent(localId)
    }

    override suspend fun deleteMessage(localId: String) {
        dao.delete(localId)
    }

    override suspend fun deleteAllForContact(contactId: String) {
        dao.deleteAllForContact(contactId)
    }

    override suspend fun pendingForContact(contactId: String): List<Message> =
        dao.pendingForContact(contactId).map { it.toDomain() }

    override suspend fun incomingWithoutReadAck(contactId: String): List<Message> =
        dao.incomingWithoutReadAck(contactId).map { it.toDomain() }

    override suspend fun findIncomingByRemoteId(contactId: String, remoteMessageId: String): Message? =
        dao.findIncomingByRemoteId(contactId, remoteMessageId)?.toDomain()

    override suspend fun updateFileProgress(localId: String, progress: Int) {
        dao.updateFileProgress(localId, progress.coerceIn(0, 100))
    }

    override suspend fun updateFileUri(localId: String, uri: String) {
        dao.updateFileUri(localId, uri)
    }

    override suspend fun failInterruptedFileTransfers() {
        dao.failInterruptedFileTransfers()
    }
}

private fun MessageEntity.toDomain(): Message = Message(
    localId = localId,
    contactId = contactId,
    direction = MessageDirection.valueOf(direction),
    body = when (bodyType) {
        "FILE" -> MessageBody.File(
            fileName = fileName.orEmpty(),
            fileSize = fileSize ?: 0L,
            mimeType = fileMimeType ?: "application/octet-stream",
            uri = fileUri,
            progress = fileProgress
        )
        else -> MessageBody.Text(bodyText.orEmpty())
    },
    status = MessageStatus.valueOf(status),
    localCreatedAtEpochMillis = localCreatedAtEpochMillis,
    deleteAfterReadSeconds = deleteAfterReadSeconds,
    remoteMessageId = remoteMessageId,
    readAckSent = readAckSent
)

private fun Message.toEntity(): MessageEntity = MessageEntity(
    localId = localId,
    contactId = contactId,
    direction = direction.name,
    bodyType = when (body) {
        is MessageBody.Text -> "TEXT"
        is MessageBody.File -> "FILE"
    },
    bodyText = (body as? MessageBody.Text)?.text,
    status = status.name,
    localCreatedAtEpochMillis = localCreatedAtEpochMillis,
    deleteAfterReadSeconds = deleteAfterReadSeconds,
    remoteMessageId = remoteMessageId,
    readAckSent = readAckSent,
    fileName = (body as? MessageBody.File)?.fileName,
    fileSize = (body as? MessageBody.File)?.fileSize,
    fileMimeType = (body as? MessageBody.File)?.mimeType,
    fileUri = (body as? MessageBody.File)?.uri,
    fileProgress = (body as? MessageBody.File)?.progress ?: 0
)
