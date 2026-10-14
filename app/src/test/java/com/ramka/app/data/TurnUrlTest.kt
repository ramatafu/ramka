package com.ramka.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnUrlTest {

    @Test
    fun parsesTurnAndTurns() {
        val turn = TurnUrl.parse("turn:203.0.113.5:3478")!!
        assertFalse(turn.secure)
        assertEquals("turn:203.0.113.5:3478", turn.normalized)

        val turns = TurnUrl.parse("turns:turn.example.com:5349")!!
        assertTrue(turns.secure)
        assertEquals("turns:turn.example.com:5349", turns.normalized)
    }

    @Test
    fun normalizesCaseAndTrimsSpaces() {
        assertEquals("turn:turn.example.com:3478", TurnUrl.parse("  TURN:Turn.Example.COM:3478 ")!!.normalized)
    }

    @Test
    fun rejectsInvalidFormats() {
        listOf(
            "", "turn:", "turn:host", "turn::3478", "turn:host:0", "turn:host:65536", "turn:host:abc",
            "stun:host:3478", "host:3478", "turn:host:3478?transport=tcp", "turn:ho st:3478",
            "turn:-host:3478", "turn:host.:3478", "turn://host:3478"
        ).forEach { assertNull("должен быть отклонён: '$it'", TurnUrl.parse(it)) }
    }

    @Test
    fun acceptsPortBounds() {
        assertNotNull(TurnUrl.parse("turn:host:1"))
        assertNotNull(TurnUrl.parse("turn:host:65535"))
    }

    @Test
    fun iceUrlsAddTcpFallbackOnlyForPlainTurn() {
        assertEquals(
            listOf("turn:203.0.113.5:3478", "turn:203.0.113.5:3478?transport=tcp"),
            TurnUrl.parse("turn:203.0.113.5:3478")!!.iceUrls()
        )
        assertEquals(listOf("turns:h.example:5349"), TurnUrl.parse("turns:h.example:5349")!!.iceUrls())
    }

    @Test
    fun credentialsToStringHidesPassword() {
        val text = TurnCredentials(listOf("turn:h:1"), "alice", "s3cret-pass").toString()
        assertFalse(text.contains("s3cret-pass"))
        assertTrue(text.contains("password=***"))
    }
}
