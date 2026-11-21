package com.ramka.domain.usecase

import com.ramka.domain.model.Message
import com.ramka.domain.model.MessageBody
import com.ramka.domain.model.MessageDirection
import com.ramka.domain.model.MessageStatus
import com.ramka.domain.model.OutboxEntry
import com.ramka.domain.model.Contact
import com.ramka.domain.repository.ContactRepository
import com.ramka.domain.repository.TransportRepository
import com.ramka.domain.util.Jitter
import com.ramka.domain.util.PrivacyTimingConfig
import com.ramka.domain.util.RetryBackoff
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessOutboxUseCaseTest {

    private val now = 1_000_000L

    /** Одна запись Outbox с [attemptCount] прошлых попыток; контакта нет -> попытка всегда неудачна. */
    private fun failedAttempt(attemptCount: Int, jitter: Jitter): StubOutboxRepository.Failure = runBlocking {
        val messages = StubMessageRepository().also {
            it.add(
                Message("m1", "c1", MessageDirection.OUTGOING, MessageBody.Text("x"), MessageStatus.SENDING, 1L)
            )
        }
        val outbox = StubOutboxRepository(listOf(OutboxEntry("m1", "c1", attemptCount, 0L, 0L)))
        val attempt = AttemptDeliveryUseCase(StubContactRepository(), StubTransportRepository())

        ProcessOutboxUseCase(outbox, messages, attempt, jitter).processDue(now)

        outbox.failures.single()
    }

    @Test
    fun `failed attempt schedules next try within plus minus 20 percent of backoff`() {
        for (attemptCount in listOf(0, 1, 3, 6)) {
            val base = RetryBackoff.nextDelayMillis(attemptCount + 1)
            for (unit in listOf(0.0, 0.25, 0.5, 0.75, 0.999999)) {
                val failure = failedAttempt(attemptCount, Jitter(PrivacyTimingConfig.DEFAULT) { unit })
                val delay = failure.nextAttemptAtEpochMillis - now
                assertEquals(attemptCount + 1, failure.attemptCount)
                assertTrue("attempt=$attemptCount unit=$unit delay=$delay base=$base", delay >= base * 0.8 - 1 && delay <= base * 1.2 + 1)
            }
        }
    }

    @Test
    fun `jitter extremes give exactly minus 20 and about plus 20 percent`() {
        val base = RetryBackoff.nextDelayMillis(1) // 60 с
        assertEquals(48_000L, failedAttempt(0, Jitter(PrivacyTimingConfig.DEFAULT) { 0.0 }).nextAttemptAtEpochMillis - now)
        assertEquals(base, failedAttempt(0, Jitter(PrivacyTimingConfig.DEFAULT) { 0.5 }).nextAttemptAtEpochMillis - now)
    }

    @Test
    fun `at the one hour cap the delay is between 48 and 72 minutes`() {
        val low = failedAttempt(20, Jitter(PrivacyTimingConfig.DEFAULT) { 0.0 }).nextAttemptAtEpochMillis - now
        val high = failedAttempt(20, Jitter(PrivacyTimingConfig.DEFAULT) { 0.999999 }).nextAttemptAtEpochMillis - now
        assertEquals(48 * 60_000L, low)
        assertTrue("high=$high", high in 71 * 60_000L..72 * 60_000L)
    }

    @Test
    fun `real randomness gives different delays for identical attempts`() {
        val jitter = Jitter()
        val delays = List(50) { failedAttempt(2, jitter).nextAttemptAtEpochMillis }.toSet()
        assertTrue("delays=${delays.size}", delays.size > 10)
    }

    @Test
    fun `disabled jitter keeps the deterministic backoff`() {
        val off = Jitter(PrivacyTimingConfig(jitterEnabled = false))
        val failure = failedAttempt(2, off)
        assertEquals(RetryBackoff.nextDelayMillis(3), failure.nextAttemptAtEpochMillis - now)
    }

    // ---- автоповтор при восстановлении связи: processAll и защита от дублей ----

    private class OneContactRepository(private val contact: Contact) : ContactRepository by StubContactRepository() {
        override suspend fun getContact(localId: String): Contact? = contact.takeIf { it.localId == localId }
    }

    private class CountingTransport(private val result: Boolean, private val pauseMillis: Long = 0) : TransportRepository {
        var sends = 0
        override suspend fun sendEncryptedPacket(contact: Contact, packet: ByteArray): Boolean {
            sends++
            if (pauseMillis > 0) delay(pauseMillis)
            return result
        }
        override fun incomingPackets(): Flow<Pair<ByteArray, ByteArray>> = emptyFlow()
    }

    private val peer: Contact = runBlocking { StubContactRepository().getOrCreateSelfContact().copy(localId = "c1", alias = "A", isSelf = false) }

    private fun textMessage(status: MessageStatus = MessageStatus.SENDING) =
        Message("m1", "c1", MessageDirection.OUTGOING, MessageBody.Text("x"), status, 1L)

    private fun useCase(
        outbox: StubOutboxRepository,
        messages: StubMessageRepository,
        transport: TransportRepository
    ) = ProcessOutboxUseCase(outbox, messages, AttemptDeliveryUseCase(OneContactRepository(peer), transport))

    @Test
    fun `processDue skips an entry whose time has not come but processAll sends it at once`() = runBlocking {
        val messages = StubMessageRepository().also { it.add(textMessage()) }
        val outbox = StubOutboxRepository(listOf(OutboxEntry("m1", "c1", 5, now + 3_600_000L, 0L)))
        val transport = CountingTransport(result = true)
        val uc = useCase(outbox, messages, transport)

        uc.processDue(now)
        assertEquals(0, transport.sends)

        uc.processAll(now)
        assertEquals(1, transport.sends)
        assertEquals(MessageStatus.SENT, messages.all().single().status)
        assertEquals(listOf("m1"), outbox.removed)
    }

    @Test
    fun `failed forced attempt does not change the backoff schedule`() = runBlocking {
        val messages = StubMessageRepository().also { it.add(textMessage()) }
        val outbox = StubOutboxRepository(listOf(OutboxEntry("m1", "c1", 2, now + 600_000L, 0L)))
        val uc = useCase(outbox, messages, CountingTransport(result = false))

        uc.processAll(now)

        assertTrue(outbox.failures.isEmpty())
        assertTrue(outbox.removed.isEmpty())
        assertEquals(MessageStatus.SENDING, messages.all().single().status)
    }

    @Test
    fun `parallel calls send one message only once`() = runBlocking {
        val messages = StubMessageRepository().also { it.add(textMessage()) }
        val outbox = StubOutboxRepository(listOf(OutboxEntry("m1", "c1", 0, 0L, 0L)))
        val transport = CountingTransport(result = true, pauseMillis = 100)
        val uc = useCase(outbox, messages, transport)

        coroutineScope {
            listOf(
                async { uc.processDue(now) },
                async { uc.processAll(now) },
                async { uc.processDue(now) }
            ).awaitAll()
        }

        assertEquals(1, transport.sends)
    }

    @Test
    fun `stale entry of an already sent message is dropped and not sent again`() = runBlocking {
        val messages = StubMessageRepository().also { it.add(textMessage(MessageStatus.SENT)) }
        val outbox = StubOutboxRepository(listOf(OutboxEntry("m1", "c1", 0, 0L, 0L)))
        val transport = CountingTransport(result = true)

        useCase(outbox, messages, transport).processAll(now)

        assertEquals(0, transport.sends)
        assertEquals(listOf("m1"), outbox.removed)
    }

    @Test
    fun `deleted message is removed from the queue and not sent`() = runBlocking {
        val outbox = StubOutboxRepository(listOf(OutboxEntry("gone", "c1", 0, 0L, 0L)))
        val transport = CountingTransport(result = true)

        useCase(outbox, StubMessageRepository(), transport).processAll(now)

        assertEquals(0, transport.sends)
        assertEquals(listOf("gone"), outbox.removed)
    }

    @Test
    fun `entry is released after processing so a later call can retry it`() = runBlocking {
        val messages = StubMessageRepository().also { it.add(textMessage()) }
        val outbox = StubOutboxRepository(listOf(OutboxEntry("m1", "c1", 0, 0L, 0L)))
        val transport = CountingTransport(result = false)
        val uc = useCase(outbox, messages, transport)

        uc.processDue(now)
        uc.processAll(now)

        assertEquals(2, transport.sends)
        assertFalse(outbox.failures.isEmpty())
    }
}
