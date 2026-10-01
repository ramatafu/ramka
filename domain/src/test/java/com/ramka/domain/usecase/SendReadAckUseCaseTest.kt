package com.ramka.domain.usecase

import com.ramka.domain.model.AppMessage
import com.ramka.domain.model.Message
import com.ramka.domain.model.MessageBody
import com.ramka.domain.model.MessageDirection
import com.ramka.domain.model.MessageStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class SendReadAckUseCaseTest {

    private val contactId = "contact-1"
    private val ackScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @After
    fun tearDown() = ackScope.cancel()

    private fun incoming(
        status: MessageStatus = MessageStatus.DELIVERED,
        contact: String = contactId,
        remoteId: String? = UUID.randomUUID().toString()
    ) = Message(
        localId = UUID.randomUUID().toString(),
        contactId = contact,
        direction = MessageDirection.INCOMING,
        body = MessageBody.Text("hi"),
        status = status,
        localCreatedAtEpochMillis = 1L,
        remoteMessageId = remoteId
    )

    /** Фейковая отправка: запоминает вызовы и ждёт [gate] — это и есть «отложенная отправка». */
    private class Sender(private val gate: CompletableDeferred<Unit>) {
        val started = AtomicInteger(0)
        val sent = CopyOnWriteArrayList<Pair<String, ByteArray>>()
        suspend fun send(contactId: String, payload: ByteArray): Boolean {
            started.incrementAndGet()
            gate.await()
            sent += contactId to payload
            return true
        }
    }

    private suspend fun awaitSent(sender: Sender, count: Int) = withTimeout(3_000) {
        while (sender.sent.size < count) delay(10)
    }

    @Test
    fun `flag is set before the delayed send happens`() = runBlocking {
        val repo = StubMessageRepository()
        val message = incoming().also(repo::add)
        val gate = CompletableDeferred<Unit>()
        val sender = Sender(gate)
        val useCase = SendReadAckUseCase(repo, ackScope, sender::send)

        useCase(contactId)

        // Отправка ещё «в задержке» (gate закрыт) — флаг уже стоит.
        assertTrue(repo.all().single { it.localId == message.localId }.readAckSent)
        assertEquals(0, sender.sent.size)

        gate.complete(Unit)
        awaitSent(sender, 1)
        val ack = AppMessage.decode(sender.sent.single().second)
        assertEquals(AppMessage.ReadAck(listOf(message.remoteMessageId!!)), ack)
    }

    @Test
    fun `reopening the chat during the delay does not send a duplicate`() = runBlocking {
        val repo = StubMessageRepository()
        repo.add(incoming())
        val gate = CompletableDeferred<Unit>()
        val sender = Sender(gate)
        val useCase = SendReadAckUseCase(repo, ackScope, sender::send)

        useCase(contactId)
        useCase(contactId) // повторное открытие чата, пока первая отправка ждёт
        useCase(contactId)
        gate.complete(Unit)
        awaitSent(sender, 1)
        delay(200)

        assertEquals("ровно одна отправка", 1, sender.started.get())
        assertEquals(1, sender.sent.size)
    }

    @Test
    fun `concurrent calls produce a single batch`() = runBlocking {
        val repo = StubMessageRepository(readDelayMillis = 30) // расширяем окно гонки
        repeat(3) { repo.add(incoming()) }
        val gate = CompletableDeferred<Unit>().also { it.complete(Unit) }
        val sender = Sender(gate)
        val useCase = SendReadAckUseCase(repo, ackScope, sender::send)

        List(6) { async(Dispatchers.Default) { useCase(contactId) } }.awaitAll()
        awaitSent(sender, 1)
        delay(300)

        assertEquals(1, sender.started.get())
        val ack = AppMessage.decode(sender.sent.single().second) as AppMessage.ReadAck
        assertEquals("все три ID одним батчем", 3, ack.messageLocalIds.size)
    }

    @Test
    fun `only delivered or read incoming messages are acknowledged`() = runBlocking {
        val repo = StubMessageRepository()
        val ok1 = incoming(MessageStatus.DELIVERED).also(repo::add)
        val ok2 = incoming(MessageStatus.READ).also(repo::add)
        val notReady = incoming(MessageStatus.SENT).also(repo::add)
        val otherContact = incoming(contact = "other").also(repo::add)
        val sender = Sender(CompletableDeferred<Unit>().also { it.complete(Unit) })

        SendReadAckUseCase(repo, ackScope, sender::send)(contactId)
        awaitSent(sender, 1)

        val ack = AppMessage.decode(sender.sent.single().second) as AppMessage.ReadAck
        assertEquals(listOf(ok1.remoteMessageId, ok2.remoteMessageId), ack.messageLocalIds)
        val byId = repo.all().associateBy { it.localId }
        assertTrue(byId.getValue(ok1.localId).readAckSent)
        assertTrue(byId.getValue(ok2.localId).readAckSent)
        assertFalse(byId.getValue(notReady.localId).readAckSent)
        assertFalse(byId.getValue(otherContact.localId).readAckSent)
    }

    @Test
    fun `nothing to acknowledge sends nothing and sets no flags`() = runBlocking {
        val repo = StubMessageRepository()
        repo.add(incoming(MessageStatus.SENT))
        val sender = Sender(CompletableDeferred<Unit>().also { it.complete(Unit) })

        SendReadAckUseCase(repo, ackScope, sender::send)(contactId)
        delay(200)

        assertEquals(0, sender.started.get())
        assertTrue(repo.all().none { it.readAckSent })
    }

    @Test
    fun `leaving the screen during the delay does not lose the ack`() = runBlocking {
        val repo = StubMessageRepository()
        repo.add(incoming())
        val gate = CompletableDeferred<Unit>()
        val sender = Sender(gate)
        val useCase = SendReadAckUseCase(repo, ackScope, sender::send)

        // «ViewModel scope»: вызов завершился, потом скоуп экрана отменили, пока ACK ещё ждёт.
        val screenScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        screenScope.launch { useCase(contactId) }.join()
        screenScope.cancel()
        gate.complete(Unit)

        awaitSent(sender, 1)
        assertEquals(1, sender.sent.size)
    }
}
