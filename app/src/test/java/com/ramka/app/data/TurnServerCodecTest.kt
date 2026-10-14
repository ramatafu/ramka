package com.ramka.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnServerCodecTest {

    @Test
    fun roundTripKeepsAllFields() {
        val list = listOf(
            TurnServerEntry("id-1", "turn:203.0.113.5:3478", "alice", true, 0),
            TurnServerEntry("id-2", "turns:t.example.com:5349", "bob", false, 1)
        )
        assertEquals(list, TurnServerCodec.decode(TurnServerCodec.encode(list)))
    }

    @Test
    fun encodedJsonHasNoPasswordField() {
        val json = TurnServerCodec.encode(listOf(TurnServerEntry("id-1", "turn:h:1", "alice", true, 0)))
        assertFalse(json.contains("password", ignoreCase = true))
    }

    @Test
    fun decodeOfNullBlankAndGarbageIsEmpty() {
        assertTrue(TurnServerCodec.decode(null).isEmpty())
        assertTrue(TurnServerCodec.decode("  ").isEmpty())
        assertTrue(TurnServerCodec.decode("{not json").isEmpty())
    }

    @Test
    fun decodeSkipsBrokenItemsAndUsesDefaults() {
        val raw = """[{"id":"a","url":"turn:h:1"},{"url":"turn:x:2"},{"id":"c"},42,{"id":"d","url":"turn:d:4","enabled":false,"order":5}]"""
        val list = TurnServerCodec.decode(raw)
        assertEquals(listOf("a", "d"), list.map { it.id })
        assertTrue(list[0].isEnabled)
        assertEquals("", list[0].username)
        assertFalse(list[1].isEnabled)
        assertEquals(5, list[1].order)
    }
}
