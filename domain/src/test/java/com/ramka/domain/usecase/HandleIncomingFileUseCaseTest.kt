package com.ramka.domain.usecase

import com.ramka.domain.model.AppMessage
import com.ramka.domain.model.Message
import com.ramka.domain.model.MessageBody
import com.ramka.domain.model.MessageDirection
import com.ramka.domain.model.MessageStatus
import com.ramka.domain.repository.ContactRepository
import com.ramka.domain.repository.IncomingFileStore
import com.ramka.domain.util.FileTransferConfig
import com.ramka.domain.util.FileTransferTracker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

private class FakeStore : IncomingFileStore {
    val files = mutableMapOf<String, ByteArray>()
    val published = mutableMapOf<String, Triple<ByteArray, String, String>>()
    val discarded = mutableListOf<String>()
    var beginCalls = 0
    var failBegin = false
    var failWrite = false
    var failPublish = false

    override suspend fun begin(transferId: String): Boolean {
        if (failBegin) return false
        beginCalls++
        files[transferId] = ByteArray(0)
        return true
    }

    override suspend fun write(transferId: String, offset: Long, data: ByteArray): Boolean {
        if (failWrite) return false
        val current = files[transferId] ?: return false
        val needed = (offset + data.size).toInt()
        val target = if (current.size < needed) current.copyOf(needed) else current
        data.copyInto(target, offset.toInt())
        files[transferId] = target
        return true
    }

    override suspend fun publish(transferId: String, displayName: String, mimeType: String): String? {
        if (failPublish) return null
        published[transferId] = Triple(files.remove(transferId)!!, displayName, mimeType)
        return "content://test/$displayName"
    }

    override suspend fun discard(transferId: String) {
        discarded += transferId
        files.remove(transferId)
    }

    override suspend fun discardAll() = files.clear()
}

private class CountingContacts(private val delegate: ContactRepository = StubContactRepository()) :
    ContactRepository by delegate {
    var unread = 0
    override suspend fun incrementUnreadCount(localId: String) { unread++ }
}

class HandleIncomingFileUseCaseTest {
    private val repo = StubMessageRepository()
    private val contacts = CountingContacts()
    private val store = FakeStore()
    private val tracker = FileTransferTracker()
    private var now = 1_000L
    private var chatOpen = false
    private val acks = mutableListOf<String>()
    private val completed = mutableListOf<Message>()

    private val useCase = HandleIncomingFileUseCase(repo, contacts, store, tracker, clock = { now }) { chatOpen }

    private val contact = "contact-1"
    private val chunkSize = 10
    private val data = ByteArray(25) { (it + 1).toByte() } // чанки 10 + 10 + 5

    private fun meta(
        id: String,
        name: String = "photo.jpg",
        mime: String = "image/jpeg",
        size: Long = 25,
        chunks: Int = 3,
        chunk: Int = chunkSize
    ) = AppMessage.FileMeta(id, name, size, chunks, chunk, mime)

    private fun chunk(id: String, index: Int, bytes: ByteArray = slice(index)) = AppMessage.FileChunk(id, index, bytes)
    private fun slice(index: Int) = data.copyOfRange(index * chunkSize, minOf(data.size, (index + 1) * chunkSize))

    private suspend fun sendMeta(m: AppMessage.FileMeta) = useCase.onMeta(contact, m, now) { acks += it }
    private suspend fun sendChunk(c: AppMessage.FileChunk) =
        useCase.onChunk(contact, c, { acks += it }, { completed += it })

    private fun incoming(id: String) = repo.all().firstOrNull { it.remoteMessageId == id }
    private fun body(id: String) = incoming(id)!!.body as MessageBody.File

    @Test fun fullTransferInOrderPublishesAndAcknowledges() = runBlocking {
        val id = UUID.randomUUID().toString()
        sendMeta(meta(id))
        val row = incoming(id)!!
        assertEquals(MessageDirection.INCOMING, row.direction)
        assertEquals(MessageStatus.SENDING, row.status)
        assertEquals(0, body(id).progress)
        assertNull(body(id).uri)
        assertEquals(0, tracker.progress.value[row.localId])

        sendChunk(chunk(id, 0))
        assertEquals(33, tracker.progress.value[row.localId])
        sendChunk(chunk(id, 1))
        assertTrue(acks.isEmpty() && completed.isEmpty())
        sendChunk(chunk(id, 2))

        val done = incoming(id)!!
        assertEquals(MessageStatus.DELIVERED, done.status)
        assertEquals(100, body(id).progress)
        assertEquals("content://test/photo.jpg", body(id).uri)
        assertEquals(listOf(id), acks)
        assertEquals(1, completed.size)
        assertEquals(1, contacts.unread)
        assertFalse(tracker.isActive(row.localId))
        val (bytes, name, mime) = store.published.getValue(row.localId)
        assertArrayEquals(data, bytes)
        assertEquals("photo.jpg", name)
        assertEquals("image/jpeg", mime)
    }

    @Test fun outOfOrderChunksAssembleCorrectly() = runBlocking {
        val id = UUID.randomUUID().toString()
        sendMeta(meta(id))
        sendChunk(chunk(id, 2)); sendChunk(chunk(id, 0)); sendChunk(chunk(id, 1))
        assertEquals(MessageStatus.DELIVERED, incoming(id)!!.status)
        assertArrayEquals(data, store.published.values.single().first)
    }

    @Test fun duplicateChunkIsIgnored() = runBlocking {
        val id = UUID.randomUUID().toString()
        sendMeta(meta(id))
        sendChunk(chunk(id, 0)); sendChunk(chunk(id, 0)); sendChunk(chunk(id, 1))
        assertEquals(MessageStatus.SENDING, incoming(id)!!.status) // дубликат не засчитан как чанк 2
        sendChunk(chunk(id, 2))
        assertEquals(MessageStatus.DELIVERED, incoming(id)!!.status)
        assertEquals(1, completed.size)
    }

    @Test fun openChatDoesNotIncrementUnread() = runBlocking {
        chatOpen = true
        val id = UUID.randomUUID().toString()
        sendMeta(meta(id)); (0..2).forEach { sendChunk(chunk(id, it)) }
        assertEquals(0, contacts.unread)
        assertEquals(listOf(id), acks)
    }

    @Test fun invalidMetaIsDropped() = runBlocking {
        val id = UUID.randomUUID().toString()
        sendMeta(meta(id, size = 0, chunks = 0))
        sendMeta(meta(id, size = FileTransferConfig.MAX_FILE_SIZE_BYTES + 1, chunks = 1, chunk = 1))
        sendMeta(meta(id, chunks = 5)) // число чанков не сходится с размером
        sendMeta(meta(id, chunk = FileTransferConfig.MAX_ACCEPTED_CHUNK_SIZE + 1, chunks = 1))
        assertTrue(repo.all().isEmpty())
        assertEquals(0, store.beginCalls)
    }

    @Test fun chunkOfUnknownTransferIsDroppedSilently() = runBlocking {
        sendChunk(chunk(UUID.randomUUID().toString(), 0))
        assertTrue(repo.all().isEmpty())
        assertTrue(acks.isEmpty())
    }

    @Test fun wrongLengthChunkFailsTheTransfer() = runBlocking {
        val id = UUID.randomUUID().toString()
        sendMeta(meta(id))
        val localId = incoming(id)!!.localId
        sendChunk(chunk(id, 0, ByteArray(9)))
        assertEquals(MessageStatus.FAILED, incoming(id)!!.status)
        assertTrue(localId in store.discarded)
        assertFalse(tracker.isActive(localId))
        sendChunk(chunk(id, 1)) // передача уже снята — молча игнорируется
        assertEquals(MessageStatus.FAILED, incoming(id)!!.status)
    }

    @Test fun lastChunkMustHaveExactRemainderLength() = runBlocking {
        val id = UUID.randomUUID().toString()
        sendMeta(meta(id))
        sendChunk(chunk(id, 2, ByteArray(10))) // у последнего должно быть 5
        assertEquals(MessageStatus.FAILED, incoming(id)!!.status)
    }

    @Test fun indexOutOfRangeFailsTheTransfer() = runBlocking {
        val id = UUID.randomUUID().toString()
        sendMeta(meta(id))
        sendChunk(chunk(id, 7))
        assertEquals(MessageStatus.FAILED, incoming(id)!!.status)
    }

    @Test fun writeFailureFailsTheTransfer() = runBlocking {
        val id = UUID.randomUUID().toString()
        sendMeta(meta(id))
        store.failWrite = true
        sendChunk(chunk(id, 0))
        assertEquals(MessageStatus.FAILED, incoming(id)!!.status)
    }

    @Test fun publishFailureFailsWithoutAck() = runBlocking {
        val id = UUID.randomUUID().toString()
        sendMeta(meta(id))
        store.failPublish = true
        (0..2).forEach { sendChunk(chunk(id, it)) }
        assertEquals(MessageStatus.FAILED, incoming(id)!!.status)
        assertTrue(acks.isEmpty())
        assertEquals(0, contacts.unread)
        assertTrue(completed.isEmpty())
    }

    @Test fun repeatedMetaAfterCompletionOnlyReAcknowledges() = runBlocking {
        val id = UUID.randomUUID().toString()
        sendMeta(meta(id)); (0..2).forEach { sendChunk(chunk(id, it)) }
        val beginsBefore = store.beginCalls
        acks.clear()

        sendMeta(meta(id)) // отправитель не получил ACK и повторяет
        assertEquals(listOf(id), acks)
        assertEquals(1, repo.all().size)
        assertEquals(beginsBefore, store.beginCalls)
        assertEquals(MessageStatus.DELIVERED, incoming(id)!!.status)
    }

    @Test fun repeatedMetaDuringTransferRestartsSameMessage() = runBlocking {
        val id = UUID.randomUUID().toString()
        sendMeta(meta(id))
        val localId = incoming(id)!!.localId
        sendChunk(chunk(id, 0)); sendChunk(chunk(id, 1))

        sendMeta(meta(id)) // отправитель начал заново
        assertEquals(1, repo.all().size)
        assertEquals(localId, incoming(id)!!.localId)
        assertEquals(0, body(id).progress)
        assertEquals(MessageStatus.SENDING, incoming(id)!!.status)

        sendChunk(chunk(id, 2)) // старые чанки забыты: одного последнего мало
        assertEquals(MessageStatus.SENDING, incoming(id)!!.status)
        sendChunk(chunk(id, 0)); sendChunk(chunk(id, 1))
        assertEquals(MessageStatus.DELIVERED, incoming(id)!!.status)
        assertArrayEquals(data, store.published.getValue(localId).first)
    }

    @Test fun metaForFailedTransferRestartsIt() = runBlocking {
        val id = UUID.randomUUID().toString()
        sendMeta(meta(id)); sendChunk(chunk(id, 5))
        assertEquals(MessageStatus.FAILED, incoming(id)!!.status)

        sendMeta(meta(id))
        assertEquals(1, repo.all().size)
        assertEquals(MessageStatus.SENDING, incoming(id)!!.status)
        (0..2).forEach { sendChunk(chunk(id, it)) }
        assertEquals(MessageStatus.DELIVERED, incoming(id)!!.status)
    }

    @Test fun limitOfConcurrentTransfersAndStaleEviction() = runBlocking {
        val ids = List(4) { UUID.randomUUID().toString() }
        ids.take(3).forEach { sendMeta(meta(it)) }
        sendMeta(meta(ids[3]))
        assertNull(incoming(ids[3])) // четвёртый не принят: все слоты заняты

        now += FileTransferConfig.STALE_INCOMING_MILLIS + 1
        sendMeta(meta(ids[3]))
        assertNotNull(incoming(ids[3])) // брошенные освободили слоты
        assertEquals(MessageStatus.SENDING, incoming(ids[3])!!.status)
        assertEquals(3, repo.all().count { it.status == MessageStatus.FAILED })
    }

    @Test fun nameAndMimeAreSanitized() = runBlocking {
        val id = UUID.randomUUID().toString()
        sendMeta(meta(id, name = "../../etc/evil.sh", mime = "not a mime"))
        assertEquals("evil.sh", body(id).fileName)
        assertEquals("application/octet-stream", body(id).mimeType)
    }

    @Test fun beginFailureForNewTransferCreatesNothing() = runBlocking {
        store.failBegin = true
        sendMeta(meta(UUID.randomUUID().toString()))
        assertTrue(repo.all().isEmpty())
    }

    @Test fun transfersFromDifferentContactsDoNotMix() = runBlocking {
        val id = UUID.randomUUID().toString()
        sendMeta(meta(id))
        // чанк с тем же messageId, но от другого контакта, не относится к этой передаче
        useCase.onChunk("other-contact", chunk(id, 0), { acks += it }, { completed += it })
        assertEquals(MessageStatus.SENDING, incoming(id)!!.status)
        assertEquals(0, tracker.progress.value[incoming(id)!!.localId])
    }
}
