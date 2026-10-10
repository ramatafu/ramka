package com.ramka.domain.usecase

import com.ramka.domain.model.AppMessage
import com.ramka.domain.model.Message
import com.ramka.domain.model.MessageBody
import com.ramka.domain.model.MessageDirection
import com.ramka.domain.model.MessageStatus
import com.ramka.domain.util.FileTransferConfig
import com.ramka.domain.util.FileTransferTracker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SendFileUseCaseTest {
    @get:Rule val tmp = TemporaryFolder()

    private val chunk = FileTransferConfig.CHUNK_SIZE
    private val repo = StubMessageRepository()
    private val tracker = FileTransferTracker()
    private val sent = mutableListOf<ByteArray>()
    private val pauses = mutableListOf<Long>()

    /** Сколько ближайших вызовов send должны провалиться; затем успех. */
    private var failuresLeft = 0

    /** Провалить все попытки пакета с этим номером в sent-последовательности (0 = метаданные). */
    private var alwaysFailFromPacket = Int.MAX_VALUE

    private fun useCase(): SendFileUseCase = SendFileUseCase(
        repo, tracker,
        send = { _, payload ->
            val packetNumber = sent.count { true }
            if (packetNumber >= alwaysFailFromPacket) {
                false
            } else if (failuresLeft > 0) {
                failuresLeft--; false
            } else {
                sent += payload; true
            }
        },
        pause = { pauses += it }
    )

    private fun fileOf(size: Int): Pair<File, ByteArray> {
        val data = ByteArray(size) { (it % 253).toByte() }
        val file = tmp.newFile().also { it.writeBytes(data) }
        return file to data
    }

    private suspend fun outgoing(uc: SendFileUseCase, file: File, size: Long = file.length()): Message =
        uc.createOutgoing("contact", "My File.bin", size, "application/octet-stream", file.toURI().toString())

    private fun fileBody(id: String) = repo.all().first { it.localId == id }.body as MessageBody.File
    private fun statusOf(id: String) = repo.all().first { it.localId == id }.status

    @Test fun createOutgoingSavesSendingMessageWithZeroProgress() = runBlocking {
        val (file, _) = fileOf(10)
        val msg = outgoing(useCase(), file)
        assertEquals(MessageDirection.OUTGOING, msg.direction)
        assertEquals(MessageStatus.SENDING, statusOf(msg.localId))
        assertEquals(0, fileBody(msg.localId).progress)
        assertEquals("My File.bin", fileBody(msg.localId).fileName)
    }

    @Test fun successSendsMetaThenChunksInOrderAndMarksSent() = runBlocking {
        val size = chunk * 2 + 100
        val (file, data) = fileOf(size)
        val uc = useCase()
        val msg = outgoing(uc, file)

        assertTrue(uc.transfer(msg.localId))

        assertEquals(4, sent.size)
        val meta = AppMessage.decode(sent[0]) as AppMessage.FileMeta
        assertEquals(msg.localId, meta.messageId)
        assertEquals(size.toLong(), meta.totalSize)
        assertEquals(3, meta.totalChunks)
        assertEquals(chunk, meta.chunkSize)
        assertEquals("My File.bin", meta.fileName)

        val chunks = sent.drop(1).map { AppMessage.decode(it) as AppMessage.FileChunk }
        assertEquals(listOf(0, 1, 2), chunks.map { it.index })
        assertEquals(listOf(chunk, chunk, 100), chunks.map { it.data.size })
        assertArrayEquals(data, chunks.flatMap { it.data.toList() }.toByteArray())

        assertEquals(MessageStatus.SENT, statusOf(msg.localId))
        assertEquals(100, fileBody(msg.localId).progress)
        assertFalse(tracker.isActive(msg.localId))
    }

    @Test fun singleByteFileIsOneChunk() = runBlocking {
        val (file, data) = fileOf(1)
        val uc = useCase()
        val msg = outgoing(uc, file)
        assertTrue(uc.transfer(msg.localId))
        assertEquals(2, sent.size)
        assertArrayEquals(data, (AppMessage.decode(sent[1]) as AppMessage.FileChunk).data)
    }

    @Test fun transientFailureIsRetriedWithPause() = runBlocking {
        val (file, _) = fileOf(chunk + 5)
        val uc = useCase()
        val msg = outgoing(uc, file)
        failuresLeft = 2 // метаданные: две неудачи, третья попытка успешна

        assertTrue(uc.transfer(msg.localId))
        assertEquals(3, sent.size)
        assertEquals(listOf(400L, 800L), pauses)
        assertEquals(MessageStatus.SENT, statusOf(msg.localId))
    }

    @Test fun permanentChunkFailureMarksFailedWithLastProgress() = runBlocking {
        val (file, _) = fileOf(chunk * 3)
        val uc = useCase()
        val msg = outgoing(uc, file)
        alwaysFailFromPacket = 2 // meta (0) и chunk0 (1) ушли, chunk1 не уходит никогда

        assertFalse(uc.transfer(msg.localId))
        assertEquals(2, sent.size)
        assertEquals(MessageStatus.FAILED, statusOf(msg.localId))
        assertEquals(33, fileBody(msg.localId).progress) // 1 из 3 чанков
        assertFalse(tracker.isActive(msg.localId))
    }

    @Test fun metaFailureMarksFailedAtZero() = runBlocking {
        val (file, _) = fileOf(100)
        val uc = useCase()
        val msg = outgoing(uc, file)
        alwaysFailFromPacket = 0

        assertFalse(uc.transfer(msg.localId))
        assertEquals(MessageStatus.FAILED, statusOf(msg.localId))
        assertEquals(0, fileBody(msg.localId).progress)
        assertEquals(listOf(400L, 800L), pauses) // 3 попытки -> 2 паузы
    }

    @Test fun retryAfterFailureRestartsFromTheBeginning() = runBlocking {
        val (file, data) = fileOf(chunk + 10)
        val uc = useCase()
        val msg = outgoing(uc, file)
        alwaysFailFromPacket = 1
        assertFalse(uc.transfer(msg.localId))
        assertEquals(MessageStatus.FAILED, statusOf(msg.localId))

        alwaysFailFromPacket = Int.MAX_VALUE
        sent.clear()
        assertTrue(uc.transfer(msg.localId))
        assertEquals(3, sent.size) // метаданные снова + оба чанка
        assertTrue(AppMessage.decode(sent[0]) is AppMessage.FileMeta)
        assertEquals(MessageStatus.SENT, statusOf(msg.localId))
        assertArrayEquals(data, sent.drop(1).flatMap { (AppMessage.decode(it) as AppMessage.FileChunk).data.toList() }.toByteArray())
    }

    @Test fun missingOrEmptyFileFailsWithoutSendingAnything() = runBlocking {
        val uc = useCase()
        val missing = outgoing(uc, File(tmp.root, "nope.bin"), size = 10)
        assertFalse(uc.transfer(missing.localId))
        assertEquals(MessageStatus.FAILED, statusOf(missing.localId))

        val empty = outgoing(uc, tmp.newFile())
        assertFalse(uc.transfer(empty.localId))
        assertEquals(MessageStatus.FAILED, statusOf(empty.localId))
        assertTrue(sent.isEmpty())
    }

    @Test fun nonFileOrIncomingMessagesAreRejected() = runBlocking {
        val uc = useCase()
        repo.add(Message("t", "c", MessageDirection.OUTGOING, MessageBody.Text("x"), MessageStatus.SENDING, 1L))
        repo.add(
            Message("i", "c", MessageDirection.INCOMING, MessageBody.File("a", 1, "x/y", null, 0), MessageStatus.SENDING, 1L)
        )
        assertFalse(uc.transfer("t"))
        assertFalse(uc.transfer("i"))
        assertFalse(uc.transfer("unknown"))
        assertEquals(MessageStatus.SENDING, statusOf("t")) // не тронуто
        assertTrue(sent.isEmpty())
    }

    @Test fun managerIgnoresSecondStartWhileRunning() = runBlocking {
        val (file, _) = fileOf(50)
        val gate = CompletableDeferred<Unit>()
        val uc = SendFileUseCase(repo, tracker, send = { _, _ -> gate.await(); true }, pause = {})
        val msg = outgoing(uc, file)
        val manager = FileTransferManager(this, uc)

        assertTrue(manager.start(msg.localId))
        assertFalse(manager.start(msg.localId))
        gate.complete(Unit)
        coroutineContext[Job]!!.children.forEach { it.join() }

        assertEquals(MessageStatus.SENT, statusOf(msg.localId))
        assertTrue(manager.start(msg.localId)) // после завершения снова можно (повтор)
    }
}
