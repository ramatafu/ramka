package com.ramka.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryBackoffTest {

    @Test
    fun `sequence starts at 30 seconds and doubles each attempt`() {
        assertEquals(30_000L, RetryBackoff.nextDelayMillis(0))
        assertEquals(60_000L, RetryBackoff.nextDelayMillis(1))
        assertEquals(120_000L, RetryBackoff.nextDelayMillis(2))
        assertEquals(240_000L, RetryBackoff.nextDelayMillis(3))
        assertEquals(480_000L, RetryBackoff.nextDelayMillis(4))
        assertEquals(960_000L, RetryBackoff.nextDelayMillis(5))
        assertEquals(1_920_000L, RetryBackoff.nextDelayMillis(6))
    }

    @Test
    fun `delay is capped at one hour`() {
        val oneHour = 3_600_000L
        // 30с * 2^7 = 3840с > 3600с — потолок достигается на попытке 7
        assertEquals(oneHour, RetryBackoff.nextDelayMillis(7))
        assertEquals(oneHour, RetryBackoff.nextDelayMillis(8))
        assertEquals(oneHour, RetryBackoff.nextDelayMillis(20))
    }

    @Test
    fun `very large attempt counts do not overflow and stay at the cap`() {
        assertEquals(3_600_000L, RetryBackoff.nextDelayMillis(1_000))
        assertEquals(3_600_000L, RetryBackoff.nextDelayMillis(Int.MAX_VALUE))
    }

    @Test
    fun `sequence is monotonically non-decreasing`() {
        var previous = 0L
        for (attempt in 0..50) {
            val current = RetryBackoff.nextDelayMillis(attempt)
            assertTrue("attempt=$attempt: $current < $previous", current >= previous)
            previous = current
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative attempt count is rejected`() {
        RetryBackoff.nextDelayMillis(-1)
    }
}
