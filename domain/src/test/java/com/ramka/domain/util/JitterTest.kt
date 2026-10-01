package com.ramka.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JitterTest {

    private val almostOne = Math.nextDown(1.0)

    private fun jitter(unit: Double, config: PrivacyTimingConfig = PrivacyTimingConfig.DEFAULT) = Jitter(config) { unit }

    @Test
    fun `default config matches the agreed constants`() {
        val c = PrivacyTimingConfig.DEFAULT
        assertTrue("по умолчанию джиттер включён", c.jitterEnabled)
        assertEquals(500L, c.ackJitterMinMillis)
        assertEquals(5_000L, c.ackJitterMaxMillis)
        assertEquals(0.20, c.backoffJitterFraction, 0.0)
    }

    @Test
    fun `ack delay hits both ends of the range inclusive`() {
        assertEquals(500L, jitter(0.0).ackDelayMillis())
        assertEquals(5_000L, jitter(almostOne).ackDelayMillis())
        assertEquals(2_750L, jitter(0.5).ackDelayMillis())
    }

    @Test
    fun `ack delay with real randomness always stays within bounds and varies`() {
        val jitter = Jitter()
        val samples = List(5_000) { jitter.ackDelayMillis() }
        assertTrue(samples.all { it in 500L..5_000L })
        assertTrue("значения должны различаться", samples.toSet().size > 100)
    }

    @Test
    fun `out of range random source is clamped`() {
        assertEquals(500L, jitter(-3.0).ackDelayMillis())
        assertEquals(5_000L, jitter(7.0).ackDelayMillis())
    }

    @Test
    fun `backoff factor spans minus 20 to plus 20 percent`() {
        assertEquals(48_000L, jitter(0.0).applyBackoff(60_000L))
        assertEquals(60_000L, jitter(0.5).applyBackoff(60_000L))
        val high = jitter(almostOne).applyBackoff(60_000L)
        assertTrue("high=$high", high in 71_990L..72_000L)
    }

    @Test
    fun `backoff jitter applies at the one hour cap too`() {
        val hour = 3_600_000L
        assertEquals(2_880_000L, jitter(0.0).applyBackoff(hour)) // 48 минут
        val high = jitter(almostOne).applyBackoff(hour)
        assertTrue("high=$high", high in 4_319_000L..4_320_000L) // 72 минуты
    }

    @Test
    fun `backoff with real randomness stays within plus minus 20 percent`() {
        val jitter = Jitter()
        repeat(5_000) {
            val result = jitter.applyBackoff(30_000L)
            assertTrue("result=$result", result in 24_000L..36_000L)
        }
    }

    @Test
    fun `disabled jitter gives zero ack delay and untouched backoff`() {
        val off = PrivacyTimingConfig(jitterEnabled = false)
        assertEquals(0L, jitter(0.7, off).ackDelayMillis())
        assertEquals(123_456L, jitter(0.7, off).applyBackoff(123_456L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `config rejects max below min`() {
        PrivacyTimingConfig(ackJitterMinMillis = 2_000, ackJitterMaxMillis = 1_000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `config rejects fraction of one or more`() {
        PrivacyTimingConfig(backoffJitterFraction = 1.0)
    }
}
