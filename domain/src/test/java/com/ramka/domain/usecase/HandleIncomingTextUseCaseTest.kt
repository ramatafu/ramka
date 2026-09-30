package com.ramka.domain.usecase

import com.ramka.domain.model.AppMessage
import com.ramka.domain.model.Contact
import com.ramka.domain.model.KeyVerificationStatus
import com.ramka.domain.model.Message
import com.ramka.domain.model.MessageDirection
import com.ramka.domain.model.MessageStatus
import com.ramka.domain.model.NetworkAddress
import com.ramka.domain.repository.ContactRepository
import com.ramka.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class HandleIncomingTextUseCaseTest {

    private val contactId = "contact-1"

    private fun newMessageId() = UUID.randomUUID().toString()

    private fun newUseCase(
        messages: FakeMessageRepository = FakeMessageRepository(),
        contacts: FakeContactRepository = FakeContactRepository()
    ) = Triple(HandleIncomingTextUseCase(messages, contacts), messages, contacts)

    @Test
    fun `first delivery is saved once with remoteMessageId and acknowledged`() = runBlocking {
        val (useCase, repo, _) = newUseCase()
        val acks = mutableListOf<String>()
        val id = newMessageId()

        val isNew = useCase(contactId, AppMessage.Text(id, "hello"), 1L) { acks += it }

        assertTrue(isNew)
        assertEquals(1, repo.all().size)
        val saved = repo.all().single()
        assertEquals(id, saved.remoteMessageId)
        assertEquals(MessageDirection.INCOMING, saved.direction)
        assertEquals(listOf(id), acks)
    }

    @Test
    fun `duplicate by remoteMessageId does not create a second record but is acknowledged again`() = runBlocking {
        val (useCase, repo, _) = newUseCase()
        val acks = mutableListOf<String>()
        val id = newMessageId()

        val first = useCase(contactId, AppMessage.Text(id, "hello"), 1L) { acks += it }
        val second = useCase(contactId, AppMessage.Text(id, "hello"), 2L) { acks += it }

        assertTrue(first)
        assertFalse(second)
        assertEquals("дубликат не должен создавать вторую запись в UI", 1, repo.all().size)
        assertEquals("ACK обязан уйти на оба приёма", listOf(id, id), acks)
    }

    @Test
    fun `same remoteMessageId from a different contact is a different message`() = runBlocking {
        val (useCase, repo, _) = newUseCase()
        val id = newMessageId()

        useCase("contact-A", AppMessage.Text(id, "hi"), 1L) {}
        val isNewForB = useCase("contact-B", AppMessage.Text(id, "hi"), 2L) {}

        assertTrue(isNewForB)
        assertEquals(2, repo.all().size)
    }

    @Test
    fun `different messageIds from the same contact are all saved`() = runBlocking {
        val (useCase, repo, _) = newUseCase()

        repeat(3) { useCase(contactId, AppMessage.Text(newMessageId(), "m$it"), it.toLong()) {} }

        assertEquals(3, repo.all().size)
    }

    @Test
    fun `new message increments the contact unread badge`() = runBlocking {
        val (useCase, _, contacts) = newUseCase()

        useCase(contactId, AppMessage.Text(newMessageId(), "one"), 1L) {}
        useCase(contactId, AppMessage.Text(newMessageId(), "two"), 2L) {}

        assertEquals(2, contacts.unreadCountFor(contactId))
    }

    @Test
    fun `duplicate message does not increment the unread badge again`() = runBlocking {
        val (useCase, _, contacts) = newUseCase()
        val id = newMessageId()

        useCase(contactId, AppMessage.Text(id, "hello"), 1L) {}
        useCase(contactId, AppMessage.Text(id, "hello"), 2L) {}

        assertEquals("дубликат не должен второй раз крутить счётчик", 1, contacts.unreadCountFor(contactId))
    }

    @Test
    fun `message arriving while that chat is open does not increment the badge but is still saved and acknowledged`() = runBlocking {
        val messages = FakeMessageRepository()
        val contacts = FakeContactRepository()
        val useCase = HandleIncomingTextUseCase(messages, contacts) { it == contactId }
        val acks = mutableListOf<String>()
        val id = newMessageId()

        val isNew = useCase(contactId, AppMessage.Text(id, "hi"), 1L) { acks += it }

        assertTrue(isNew)
        assertEquals(1, messages.all().size)
        assertEquals(0, contacts.unreadCountFor(contactId))
        assertEquals(listOf(id), acks)
    }

    @Test
    fun `open chat of another contact does not suppress the badge`() = runBlocking {
        val contacts = FakeContactRepository()
        val useCase = HandleIncomingTextUseCase(FakeMessageRepository(), contacts) { it == "someone-else" }

        useCase(contactId, AppMessage.Text(newMessageId(), "hi"), 1L) {}

        assertEquals(1, contacts.unreadCountFor(contactId))
    }

    /** In-memory двойник MessageRepository — только то, что реально нужно тестируемому use case. */
    private class FakeMessageRepository : MessageRepository {
        private val store = mutableListOf<Message>()
        fun all(): List<Message> = store.toList()

        override fun observeMessages(contactId: String): Flow<List<Message>> =
            MutableStateFlow(store.filter { it.contactId == contactId })
        override suspend fun getMessage(localId: String): Message? = store.firstOrNull { it.localId == localId }
        override suspend fun saveOutgoing(message: Message) { store += message }
        override suspend fun saveIncoming(message: Message) { store += message }
        override suspend fun updateStatus(localId: String, status: MessageStatus) {
            val i = store.indexOfFirst { it.localId == localId }
            if (i >= 0) store[i] = store[i].copy(status = status)
        }
        override suspend fun markReadAckSent(localId: String) {
            val i = store.indexOfFirst { it.localId == localId }
            if (i >= 0) store[i] = store[i].copy(readAckSent = true)
        }
        override suspend fun deleteMessage(localId: String) { store.removeAll { it.localId == localId } }
        override suspend fun pendingForContact(contactId: String): List<Message> =
            store.filter { it.contactId == contactId && it.status == MessageStatus.SENDING }
        override suspend fun incomingWithoutReadAck(contactId: String): List<Message> =
            store.filter { it.contactId == contactId && it.direction == MessageDirection.INCOMING && !it.readAckSent }
        override suspend fun findIncomingByRemoteId(contactId: String, remoteMessageId: String): Message? =
            store.firstOrNull {
                it.contactId == contactId && it.direction == MessageDirection.INCOMING &&
                    it.remoteMessageId == remoteMessageId
            }
    }

    /** In-memory двойник ContactRepository — только badge непрочитанных нужен этому use case. */
    private class FakeContactRepository : ContactRepository {
        private val unread = mutableMapOf<String, Int>()
        fun unreadCountFor(localId: String): Int = unread[localId] ?: 0

        override fun observeContacts(): Flow<List<Contact>> = MutableStateFlow(emptyList())
        override suspend fun getContact(localId: String): Contact? = fakeContact(localId, unread[localId] ?: 0)
        override suspend fun addContact(contact: Contact) {}
        override suspend fun updateAlias(localId: String, alias: String) {}
        override suspend fun updateLastKnownAddress(localId: String, address: NetworkAddress) {}
        override suspend fun markKeyChanged(localId: String, newPublicKey: ByteArray) {}
        override suspend fun removeContact(localId: String) {}
        override suspend fun incrementUnreadCount(localId: String) {
            unread[localId] = (unread[localId] ?: 0) + 1
        }
        override suspend fun resetUnreadCount(localId: String) {
            unread[localId] = 0
        }

        private fun fakeContact(localId: String, unreadCount: Int) = Contact(
            localId = localId,
            alias = localId,
            publicKey = ByteArray(0),
            signingPublicKey = ByteArray(0),
            lastKnownAddress = null,
            verification = KeyVerificationStatus.VERIFIED_BY_QR,
            addedAtEpochDay = 0,
            unreadCount = unreadCount
        )
    }
}
