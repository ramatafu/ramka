package com.ramka.domain.usecase

import com.ramka.domain.model.Message
import com.ramka.domain.model.MessageBody
import com.ramka.domain.model.MessageDirection
import com.ramka.domain.model.MessageStatus
import com.ramka.domain.model.OutboxEntry
import com.ramka.domain.util.Jitter
import com.ramka.domain.util.PrivacyTimingConfig
import com.ramka.domain.util.RetryBackoff
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
}
