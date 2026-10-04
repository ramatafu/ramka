package com.ramka.domain.relay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayConfigTest {

    private val goodToken = "test-token-0123456789abcdef"
    private val goodPin = "ab".repeat(32)
    private val domain = RelayAddress("relay.example.com", 48766)
    private val ip = RelayAddress("203.0.113.7", 48766)

    // ---- RelayPin ----

    @Test
    fun `пин нормализуется`() {
        assertEquals(goodPin, RelayPin.normalize(goodPin.uppercase()))
        assertEquals(goodPin, RelayPin.normalize(" " + goodPin.chunked(2).joinToString(":").uppercase() + "\n"))
        assertNull(RelayPin.normalize("abc"))
        assertNull(RelayPin.normalize("zz".repeat(32)))
        assertNull(RelayPin.normalize(goodPin + "ab"))
    }

    // ---- RelayConfig ----

    @Test
    fun `issue — пошаговая проверка`() {
        assertEquals(RelayConfigIssue.NO_ADDRESS, RelayConfig().issue)
        assertEquals(RelayConfigIssue.NO_TOKEN, RelayConfig(address = domain).issue)
        assertEquals(RelayConfigIssue.BAD_TOKEN_LENGTH, RelayConfig(address = domain, token = "short").issue)
        assertEquals(RelayConfigIssue.BAD_TOKEN_LENGTH, RelayConfig(address = domain, token = "x".repeat(65)).issue)
        assertEquals(RelayConfigIssue.PIN_REQUIRED_FOR_IP, RelayConfig(address = ip, token = goodToken).issue)
        assertNull(RelayConfig(address = ip, token = goodToken, pinSha256 = goodPin).issue)
        assertNull(RelayConfig(address = domain, token = goodToken).issue)
    }

    @Test
    fun `длина токена считается в байтах UTF-8`() {
        // 9 кириллических символов = 18 байт, 8 = 16 байт: граница включительно
        assertNull(RelayConfig(address = domain, token = "я".repeat(8)).issue)
        assertEquals(RelayConfigIssue.BAD_TOKEN_LENGTH, RelayConfig(address = domain, token = "я".repeat(7)).issue)
        assertNull(RelayConfig(address = domain, token = "я".repeat(32)).issue)
        assertEquals(RelayConfigIssue.BAD_TOKEN_LENGTH, RelayConfig(address = domain, token = "я".repeat(33)).issue)
    }

    @Test
    fun `activeEndpoint только при enabled и полных настройках`() {
        val full = RelayConfig(enabled = true, address = domain, token = goodToken, pinSha256 = goodPin)
        assertEquals(RelayEndpoint(domain, goodToken, goodPin), full.activeEndpoint)
        assertNull(full.copy(enabled = false).activeEndpoint)
        assertNotNull(full.copy(enabled = false).endpointOrNull) // для кнопки «Проверить»
        assertNull(full.copy(token = "").activeEndpoint)
    }

    @Test
    fun `toString не раскрывает токен`() {
        val cfg = RelayConfig(enabled = true, address = domain, token = goodToken, pinSha256 = goodPin)
        assertFalse(cfg.toString().contains(goodToken))
        assertFalse(RelayEndpoint(domain, goodToken, goodPin).toString().contains(goodToken))
    }

    // ---- RelayConfigValidator ----

    private fun valid(address: String, token: String, pin: String, enabled: Boolean = true): RelayConfig {
        val r = RelayConfigValidator.validate(RelayConfigDraft(address, token, pin), enabled)
        assertTrue("ожидалось Valid, получено $r", r is RelayDraftResult.Valid)
        return (r as RelayDraftResult.Valid).config
    }

    private fun invalid(address: String, token: String, pin: String): RelayDraftErrors {
        val r = RelayConfigValidator.validate(RelayConfigDraft(address, token, pin), true)
        assertTrue("ожидалось Invalid, получено $r", r is RelayDraftResult.Invalid)
        return (r as RelayDraftResult.Invalid).errors
    }

    @Test
    fun `валидатор — домен без пина`() {
        val c = valid(" Relay.Example.com ", "  $goodToken  ", "")
        assertEquals(domain, c.address)
        assertEquals(goodToken, c.token) // токен обрезан по краям
        assertNull(c.pinSha256)
        assertTrue(c.enabled)
    }

    @Test
    fun `валидатор — IP с пином в формате openssl`() {
        val c = valid("203.0.113.7", goodToken, goodPin.chunked(2).joinToString(":").uppercase())
        assertEquals(goodPin, c.pinSha256)
    }

    @Test
    fun `валидатор — IP без пина запрещён`() {
        val e = invalid("203.0.113.7", goodToken, "")
        assertEquals(RelayPinError.REQUIRED_FOR_IP, e.pin)
        assertNull(e.address)
        assertNull(e.token)
    }

    @Test
    fun `валидатор — ошибки по полям независимы`() {
        val e = invalid("bad host", "short", "xyz")
        assertEquals(RelayAddressError.FORBIDDEN_CHARACTERS, e.address)
        assertEquals(RelayConfigIssue.BAD_TOKEN_LENGTH, e.token)
        assertEquals(RelayPinError.BAD_FORMAT, e.pin)
        assertTrue(e.hasErrors)
        assertEquals(RelayConfigIssue.NO_TOKEN, invalid("relay.example.com", "   ", "").token)
    }

    @Test
    fun `валидатор — неверный пин при домене тоже ошибка, а не игнор`() {
        assertEquals(RelayPinError.BAD_FORMAT, invalid("relay.example.com", goodToken, "1234").pin)
    }

    @Test
    fun `валидатор — enabled передаётся как есть`() {
        assertFalse(valid("relay.example.com", goodToken, "", enabled = false).enabled)
    }
}
