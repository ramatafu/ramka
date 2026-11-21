package com.ramka.domain.usecase

import com.ramka.domain.model.Contact
import com.ramka.domain.model.Message
import com.ramka.domain.model.MessageBody
import com.ramka.domain.model.MessageDirection
import com.ramka.domain.model.MessageStatus
import com.ramka.domain.repository.ContactRepository
import com.ramka.domain.util.FileRetryTracker
import com.ramka.domain.util.FileTransferTracker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AutoResendFailedFilesUseCaseTest {
    @get:Rule val tmp = TemporaryFolder()

    private val repo = StubMessageRepository()
    private val sentPackets = mutableListOf<String>()
    private val peerSignals = mutableListOf<String>()
    private var networkUp = true

    private val peer: Contact = runBlocking { StubContactRepository().getOrCreateSelfContact().copy(localId = "c1", alias = "A", isSelf = false) }
    private val selfContact: Contact = runBlocking { StubContactRepository().getOrCreateSelfContact() }

    private class Contacts(private val known: Map<String, Contact>) : ContactRepository by StubContactRepository() {
        override suspend fun getContact(localId: String): Contact? = known[localId]
    }

    private fun sendFile() = SendFileUseCase(
        repo, FileTransferTracker(),
        send = { contactId, _ -> sentPackets += contactId; networkUp },
        pause = { },
        onConnectionFailed = { _, id -> peerSignals += id }
    )

    private fun fileMessage(
        id: String, contactId: String = "c1", size: Long = 100, status: MessageStatus = MessageStatus.FAILED,
        direction: MessageDirection = MessageDirection.OUTGOING
    ): Message {
        val file = tmp.newFile().also { it.writeBytes(ByteArray(100)) }
        return Message(
            id, contactId, direction,
            MessageBody.File("f.bin", size, "application/octet-stream", file.toURI().toString(), 0),
            status, 1L
        ).also { repo.add(it) }
    }

    private fun CoroutineScopeHolder.useCase(contacts: Map<String, Contact>) =
        AutoResendFailedFilesUseCase(repo, Contacts(contacts), FileTransferManager(scope, sendFile(), tracker))

    private class CoroutineScopeHolder(val scope: kotlinx.coroutines.CoroutineScope, val tracker: FileRetryTracker = FileRetryTracker())

    private fun statusOf(id: String) = repo.all().first { it.localId == id }.status

    @Test fun `failed small files are resent quietly when the link is back`() = runBlocking {
        fileMessage("voice")
        val uc = CoroutineScopeHolder(this).useCase(mapOf("c1" to peer))

        assertEquals(1, uc())

        assertEquals(MessageStatus.SENT, statusOf("voice"))
        assertEquals(emptyList<String>(), peerSignals)
    }

    @Test fun `files above five megabytes are left for the manual retry`() = runBlocking {
        fileMessage("big", size = 5L * 1024 * 1024 + 1)
        val uc = CoroutineScopeHolder(this).useCase(mapOf("c1" to peer))

        assertEquals(0, uc())
        assertEquals(MessageStatus.FAILED, statusOf("big"))
        assertEquals(0, sentPackets.size)
    }

    @Test fun `file of exactly five megabytes is resent`() = runBlocking {
        fileMessage("edge", size = 5L * 1024 * 1024)
        assertEquals(1, CoroutineScopeHolder(this).useCase(mapOf("c1" to peer))())
    }

    @Test fun `deleted contact and favorites are skipped`() = runBlocking {
        fileMessage("orphan", contactId = "gone")
        fileMessage("note", contactId = selfContact.localId)
        val uc = CoroutineScopeHolder(this).useCase(mapOf(selfContact.localId to selfContact))

        assertEquals(0, uc())
        assertEquals(0, sentPackets.size)
    }

    @Test fun `only outgoing FAILED messages are taken`() = runBlocking {
        fileMessage("sending", status = MessageStatus.SENDING)
        fileMessage("sent", status = MessageStatus.SENT)
        fileMessage("incoming", direction = MessageDirection.INCOMING)
        val uc = CoroutineScopeHolder(this).useCase(mapOf("c1" to peer))

        assertEquals(0, uc())
        assertEquals(0, sentPackets.size)
    }

    @Test fun `after three failed auto attempts the message stays FAILED until manual retry`() = runBlocking {
        fileMessage("v")
        networkUp = false
        val uc = CoroutineScopeHolder(this).useCase(mapOf("c1" to peer))

        assertEquals(1, uc())
        assertEquals(1, uc())
        assertEquals(1, uc())
        assertEquals("лимит исчерпан", 0, uc())

        assertEquals(MessageStatus.FAILED, statusOf("v"))
        assertEquals("автоповтор не шлёт сигнал собеседнику", emptyList<String>(), peerSignals)
    }

    @Test fun `deleted message is not retried`() = runBlocking {
        fileMessage("x")
        repo.deleteMessage("x")
        assertEquals(0, CoroutineScopeHolder(this).useCase(mapOf("c1" to peer))())
    }
}
