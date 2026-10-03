package com.ramka.domain.relay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayEntryValidatorTest {

    private val token = "test-token-0123456789abcdef"
    private val pin = "ab".repeat(32)

    private fun valid(label: String, address: String, token: String, pin: String): RelayEntry {
        val r = RelayEntryValidator.validate(RelayEntryDraft(label, address, token, pin), "id1")
        assertTrue("ожидалось Valid, получено $r", r is RelayEntryDraftResult.Valid)
        return (r as RelayEntryDraftResult.Valid).entry
    }

    private fun invalid(label: String, address: String, token: String, pin: String): RelayEntryErrors {
        val r = RelayEntryValidator.validate(RelayEntryDraft(label, address, token, pin), "id1")
        assertTrue("ожидалось Invalid, получено $r", r is RelayEntryDraftResult.Invalid)
        return (r as RelayEntryDraftResult.Invalid).errors
    }

    @Test
    fun `корректный ввод нормализуется`() {
        val e = valid("  Дом  ", " Relay.Example.COM ", "  $token  ", pin.uppercase())
        assertEquals("id1", e.id)
        assertEquals("Дом", e.label)
        assertEquals(RelayAddress("relay.example.com", 48766), e.address)
        assertEquals(token, e.token)
        assertEquals(pin, e.pinSha256)
    }

    @Test
    fun `имя необязательно`() {
        assertEquals("", valid("", "relay.example.com", token, "").label)
        assertEquals("", valid("   ", "relay.example.com", token, "").label)
    }

    @Test
    fun `ошибки по полям независимы`() {
        val e = invalid("x".repeat(41), "bad host", "short", "zz")
        assertEquals(RelayLabelError.TOO_LONG, e.label)
        assertEquals(RelayAddressError.FORBIDDEN_CHARACTERS, e.address)
        assertEquals(RelayConfigIssue.BAD_TOKEN_LENGTH, e.token)
        assertEquals(RelayPinError.BAD_FORMAT, e.pin)
        assertTrue(e.hasErrors)
    }

    @Test
    fun `граница длины имени и управляющие символы`() {
        assertEquals(RelayEntry.MAX_LABEL_LENGTH, valid("x".repeat(40), "relay.example.com", token, "").label.length)
        assertEquals(RelayLabelError.BAD_CHARACTERS, invalid("a\nb", "relay.example.com", token, "").label)
        assertEquals(RelayLabelError.BAD_CHARACTERS, invalid("a\tb", "relay.example.com", token, "").label)
    }

    @Test
    fun `IP без пина запрещён, с пином допустим`() {
        assertEquals(RelayPinError.REQUIRED_FOR_IP, invalid("", "203.0.113.7", token, "").pin)
        assertEquals(pin, valid("", "203.0.113.7:4000", token, pin).pinSha256)
    }

    @Test
    fun `пустой токен — ошибка`() {
        assertEquals(RelayConfigIssue.NO_TOKEN, invalid("", "relay.example.com", "   ", "").token)
        assertNull(invalid("", "", token, "").token)
        assertEquals(RelayAddressError.EMPTY, invalid("", "", token, "").address)
        assertFalse(RelayEntryErrors(null, null, null, null).hasErrors)
    }
}
