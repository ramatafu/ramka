package com.ramka.domain.usecase

import com.ramka.domain.util.Jitter
import com.ramka.domain.util.PrivacyTimingConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DelayedAckSenderTest {

    @Test
    fun `pauses once for the jitter delay and only then delivers`() = runBlocking {
        val events = mutableListOf<String>()
        val sender = DelayedAckSender(
            deliver = { contactId, _ -> events += "deliver:$contactId"; true },
            jitter = Jitter(PrivacyTimingConfig.DEFAULT) { 0.5 },
            pause = { events += "pause:$it" }
        )

        val result = sender.send("c1", byteArrayOf(1, 2))

        assertTrue(result)
        assertEquals(listOf("pause:2750", "deliver:c1"), events)
    }

    @Test
    fun `delay is within the configured range with real randomness`() = runBlocking {
        val pauses = mutableListOf<Long>()
        val sender = DelayedAckSender({ _, _ -> true }, Jitter(), { pauses += it })

        repeat(200) { sender.send("c", ByteArray(1)) }

        assertTrue(pauses.all { it in 500L..5_000L })
    }

    @Test
    fun `payload and result are passed through unchanged`() = runBlocking {
        var seen: ByteArray? = null
        val sender = DelayedAckSender({ _, payload -> seen = payload; false }, Jitter(), { })

        val result = sender.send("c", byteArrayOf(9, 8, 7))

        assertEquals(false, result)
        assertArrayEquals(byteArrayOf(9, 8, 7), seen)
    }

    @Test
    fun `disabled jitter delivers immediately without pausing`() = runBlocking {
        var paused = false
        val sender = DelayedAckSender(
            { _, _ -> true },
            Jitter(PrivacyTimingConfig(jitterEnabled = false)),
            { paused = true }
        )

        sender.send("c", ByteArray(1))

        assertEquals(false, paused)
    }
}
