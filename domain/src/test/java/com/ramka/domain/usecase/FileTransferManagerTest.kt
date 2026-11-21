package com.ramka.domain.usecase

import com.ramka.domain.model.MessageStatus
import com.ramka.domain.util.FileRetryTracker
import com.ramka.domain.util.FileTransferTracker
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileTransferManagerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val repo = StubMessageRepository()
    private val connectionFailures = mutableListOf<String>()
    private var networkUp = false
    private var sendCalls = 0

    private fun sendFile() = SendFileUseCase(
        repo, FileTransferTracker(),
        send = { _, _ -> sendCalls++; networkUp },
        pause = { },
        onConnectionFailed = { _, id -> connectionFailures += id }
    )

    private suspend fun failedFile(uc: SendFileUseCase): String {
        val file = tmp.newFile().also { it.writeBytes(ByteArray(100) { b -> b.toByte() }) }
        val m = uc.createOutgoing("c1", "v.ogg", 100, "audio/ogg", file.toURI().toString())
        uc.transfer(m.localId) // сети нет -> FAILED
        connectionFailures.clear()
        return m.localId
    }

    private fun statusOf(id: String) = repo.all().first { it.localId == id }.status

    @Test
    fun `auto retries are limited to three and stay silent`() = runBlocking {
        val uc = sendFile()
        val id = failedFile(uc)
        val tracker = FileRetryTracker()
        val manager = FileTransferManager(this, uc, tracker)

        repeat(3) { assertNotNull(manager.startAutoRetry(id)!!.also { it.join() }) }
        assertNull(manager.startAutoRetry(id)) // четвёртой автопопытки нет

        assertEquals(3, tracker.attempts(id))
        assertEquals(MessageStatus.FAILED, statusOf(id))
        assertTrue("автоповтор не шлёт сигнал собеседнику", connectionFailures.isEmpty())
    }

    @Test
    fun `manual start resets the counter and keeps the peer signal`() = runBlocking {
        val uc = sendFile()
        val id = failedFile(uc)
        val tracker = FileRetryTracker()
        val manager = FileTransferManager(this, uc, tracker)
        repeat(3) { manager.startAutoRetry(id)!!.join() }
        assertNull(manager.startAutoRetry(id)) // лимит исчерпан

        assertTrue(manager.start(id))
        delay(200) // даём ручной передаче отработать (сети нет, она провалится)

        assertEquals("при ручном повторе сигнал собеседнику уходит, как раньше", 1, connectionFailures.size)
        assertEquals("ручной запуск сбросил счёт", 0, tracker.attempts(id))
        assertNotNull("после ручного повтора снова разрешены автопопытки", manager.startAutoRetry(id))
    }

    @Test
    fun `successful auto retry sends the file and resets the counter`() = runBlocking {
        val uc = sendFile()
        val id = failedFile(uc)
        val tracker = FileRetryTracker()
        val manager = FileTransferManager(this, uc, tracker)

        networkUp = true
        manager.startAutoRetry(id)!!.join()

        assertEquals(MessageStatus.SENT, statusOf(id))
        assertEquals(0, tracker.attempts(id))
    }

    @Test
    fun `a busy transfer does not spend an auto attempt`() = runBlocking {
        val uc = sendFile()
        val id = failedFile(uc)
        val tracker = FileRetryTracker()
        val manager = FileTransferManager(this, uc, tracker)

        val first = manager.startAutoRetry(id)
        val second = manager.startAutoRetry(id) // первая передача ещё идёт
        first!!.join()

        assertNull(second)
        assertEquals(1, tracker.attempts(id))
    }
}
