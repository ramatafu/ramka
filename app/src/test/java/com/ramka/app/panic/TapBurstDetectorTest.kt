package com.ramka.app.panic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapBurstDetectorTest {

    @Test
    fun `five taps within 3 seconds trigger`() {
        val d = TapBurstDetector()
        assertFalse(d.onTap(0)); assertFalse(d.onTap(500)); assertFalse(d.onTap(1000)); assertFalse(d.onTap(1500))
        assertTrue(d.onTap(3000))
    }

    @Test
    fun `five taps over more than 3 seconds do not trigger`() {
        val d = TapBurstDetector()
        assertFalse(d.onTap(0)); assertFalse(d.onTap(1000)); assertFalse(d.onTap(2000)); assertFalse(d.onTap(3000))
        assertFalse(d.onTap(3001))
    }

    @Test
    fun `four taps never trigger and counter resets after trigger`() {
        val d = TapBurstDetector()
        repeat(4) { assertFalse(d.onTap(it * 100L)) }
        assertTrue(d.onTap(400))
        assertFalse(d.onTap(500))
    }
}
