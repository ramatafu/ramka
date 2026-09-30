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

    override suspend fun pendingForContact(contactId: String): List<Message> =
        dao.pendingForContact(contactId).map { it.toDomain() }

    override suspend fun incomingWithoutReadAck(contactId: String): List<Message> =
        dao.incomingWithoutReadAck(contactId).map { it.toDomain() }

    override suspend fun findIncomingByRemoteId(contactId: String, remoteMessageId: String): Message? =
        dao.findIncomingByRemoteId(contactId, remoteMessageId)?.toDomain()
}

private fun MessageEntity.toDomain(): Message = Message(
    localId = localId,
    contactId = contactId,
    direction = MessageDirection.valueOf(direction),
    body = when (bodyType) {
        "TEXT" -> MessageBody.Text(bodyText.orEmpty())
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
    bodyType = when (body) { is MessageBody.Text -> "TEXT" },
    bodyText = (body as? MessageBody.Text)?.text,
    status = status.name,
    localCreatedAtEpochMillis = localCreatedAtEpochMillis,
    deleteAfterReadSeconds = deleteAfterReadSeconds,
    remoteMessageId = remoteMessageId,
    readAckSent = readAckSent
)
