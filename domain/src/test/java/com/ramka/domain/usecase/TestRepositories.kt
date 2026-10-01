package com.ramka.domain.usecase

import com.ramka.domain.model.Contact
import com.ramka.domain.model.Message
import com.ramka.domain.model.MessageDirection
import com.ramka.domain.model.MessageStatus
import com.ramka.domain.model.NetworkAddress
import com.ramka.domain.model.OutboxEntry
import com.ramka.domain.repository.ContactRepository
import com.ramka.domain.repository.MessageRepository
import com.ramka.domain.repository.OutboxRepository
import com.ramka.domain.repository.TransportRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/** Минимальные потокобезопасные заглушки репозиториев для тестов джиттера (этап 2.5). */
internal class StubMessageRepository(
    /** Искусственная пауза в чтении «неотправленных READ-ACK»: расширяет окно гонки для теста Mutex. */
    private val readDelayMillis: Long = 0
) : MessageRepository {
    private val store = mutableListOf<Message>()

    fun add(message: Message) = synchronized(store) { store += message }
    fun all(): List<Message> = synchronized(store) { store.toList() }

    override fun observeMessages(contactId: String): Flow<List<Message>> =
        MutableStateFlow(all().filter { it.contactId == contactId })
    override suspend fun getMessage(localId: String): Message? = all().firstOrNull { it.localId == localId }
    override suspend fun saveOutgoing(message: Message) { add(message) }
    override suspend fun saveIncoming(message: Message) { add(message) }
    override suspend fun updateStatus(localId: String, status: MessageStatus) = replace(localId) { it.copy(status = status) }
    override suspend fun markReadAckSent(localId: String) = replace(localId) { it.copy(readAckSent = true) }
    override suspend fun deleteMessage(localId: String) { synchronized(store) { store.removeAll { it.localId == localId } } }
    override suspend fun pendingForContact(contactId: String): List<Message> = emptyList()
    override suspend fun incomingWithoutReadAck(contactId: String): List<Message> {
        val snapshot = all().filter {
            it.contactId == contactId && it.direction == MessageDirection.INCOMING && !it.readAckSent
        }
        if (readDelayMillis > 0) delay(readDelayMillis)
        return snapshot
    }
    override suspend fun findIncomingByRemoteId(contactId: String, remoteMessageId: String): Message? =
        all().firstOrNull { it.contactId == contactId && it.remoteMessageId == remoteMessageId }

    private fun replace(localId: String, change: (Message) -> Message) = synchronized(store) {
        val i = store.indexOfFirst { it.localId == localId }
        if (i >= 0) store[i] = change(store[i])
    }
}

/** Контактов нет: `getContact` -> null, поэтому `AttemptDeliveryUseCase` возвращает false без сети. */
internal class StubContactRepository : ContactRepository {
    override fun observeContacts(): Flow<List<Contact>> = flowOf(emptyList())
    override suspend fun getContact(localId: String): Contact? = null
    override suspend fun addContact(contact: Contact) = Unit
    override suspend fun updateAlias(localId: String, alias: String) = Unit
    override suspend fun updateLastKnownAddress(localId: String, address: NetworkAddress) = Unit
    override suspend fun markKeyChanged(localId: String, newPublicKey: ByteArray) = Unit
    override suspend fun removeContact(localId: String) = Unit
    override suspend fun incrementUnreadCount(localId: String) = Unit
    override suspend fun resetUnreadCount(localId: String) = Unit
}

internal class StubTransportRepository : TransportRepository {
    override suspend fun sendEncryptedPacket(contact: Contact, packet: ByteArray): Boolean = false
    override fun incomingPackets(): Flow<Pair<ByteArray, ByteArray>> = emptyFlow()
}

internal class StubOutboxRepository(private val entries: List<OutboxEntry>) : OutboxRepository {
    data class Failure(val messageLocalId: String, val attemptCount: Int, val nextAttemptAtEpochMillis: Long)

    val failures = mutableListOf<Failure>()

    override fun observePending(): Flow<List<OutboxEntry>> = MutableStateFlow(entries)
    override suspend fun enqueue(entry: OutboxEntry) = Unit
    override suspend fun due(nowEpochMillis: Long): List<OutboxEntry> = entries
    override suspend fun recordFailedAttempt(messageLocalId: String, attemptCount: Int, nextAttemptAtEpochMillis: Long) {
        failures += Failure(messageLocalId, attemptCount, nextAttemptAtEpochMillis)
    }
    override suspend fun remove(messageLocalId: String) = Unit
}
