package com.ramka.domain.relay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayPoolTest {

    private val token = "test-token-0123456789abcdef"
    private val pin = "ab".repeat(32)

    private fun entry(id: String, host: String = "$id.example.com", port: Int = 48766, token: String = this.token, pin: String? = null, label: String = "") =
        RelayEntry(id, RelayAddress(host, port), token, pin, label)

    private fun pool(vararg ids: String, enabled: Boolean = false) =
        RelayPool(enabled = enabled, entries = ids.map { entry(it) })

    private fun RelayPoolResult.changed(): RelayPool {
        assertTrue("ожидалось Changed, получено $this", this is RelayPoolResult.Changed)
        return (this as RelayPoolResult.Changed).pool
    }

    private fun RelayPoolResult.rejected(): RelayPoolError {
        assertTrue("ожидалось Rejected, получено $this", this is RelayPoolResult.Rejected)
        return (this as RelayPoolResult.Rejected).reason
    }

    private fun RelayPool.ids() = entries.map { it.id }

    // ---- RelayEntry ----

    @Test
    fun `запись — issue, endpoint, displayName`() {
        val ok = entry("a")
        assertNull(ok.issue)
        assertEquals(RelayEndpoint(ok.address, token, null), ok.endpointOrNull)
        assertEquals("a.example.com:48766", ok.displayName)
        assertEquals("Дом", entry("a", label = "Дом").displayName)
        assertEquals("a.example.com:48766", entry("a", label = "   ").displayName)

        assertEquals(RelayConfigIssue.NO_TOKEN, entry("a", token = "").issue)
        assertNull(entry("a", token = "").endpointOrNull)
        assertEquals(RelayConfigIssue.BAD_TOKEN_LENGTH, entry("a", token = "short").issue)
        assertEquals(RelayConfigIssue.PIN_REQUIRED_FOR_IP, entry("a", host = "203.0.113.7").issue)
        assertNull(entry("a", host = "203.0.113.7", pin = pin).issue)
    }

    @Test
    fun `toString записи не раскрывает токен`() {
        assertFalse(entry("a").toString().contains(token))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `пустой id недопустим`() {
        entry(" ")
    }

    // ---- add ----

    @Test
    fun `add добавляет в конец`() {
        val p = pool("a", "b").add(entry("c")).changed()
        assertEquals(listOf("a", "b", "c"), p.ids())
    }

    @Test
    fun `add — лимит пяти записей`() {
        val full = pool("a", "b", "c", "d", "e")
        assertEquals(RelayPool.MAX_ENTRIES, full.entries.size)
        assertEquals(RelayPoolError.LIMIT_REACHED, full.add(entry("f")).rejected())
    }

    @Test
    fun `add — одинаковый адрес запрещён, разный порт допустим`() {
        val p = pool("a")
        assertEquals(RelayPoolError.DUPLICATE_ADDRESS, p.add(entry("x", host = "a.example.com")).rejected())
        assertEquals(listOf("a", "x"), p.add(entry("x", host = "a.example.com", port = 1)).changed().ids())
    }

    // ---- replace ----

    @Test
    fun `replace меняет запись на том же месте`() {
        val p = pool("a", "b", "c")
        val edited = entry("b", host = "new.example.com", label = "Новый")
        val r = p.replace(edited).changed()
        assertEquals(listOf("a", "b", "c"), r.ids())
        assertEquals(edited, r.entries[1])
    }

    @Test
    fun `replace — нет такой записи и конфликт адреса`() {
        val p = pool("a", "b")
        assertEquals(RelayPoolError.NOT_FOUND, p.replace(entry("zzz")).rejected())
        assertEquals(RelayPoolError.DUPLICATE_ADDRESS, p.replace(entry("b", host = "a.example.com")).rejected())
        // своё же значение адреса (смена только имени/токена) конфликтом не считается
        assertTrue(p.replace(entry("b", label = "ок")).let { it is RelayPoolResult.Changed })
    }

    // ---- remove ----

    @Test
    fun `remove убирает запись, порядок остальных сохраняется`() {
        val p = pool("a", "b", "c", enabled = true).remove("b").changed()
        assertEquals(listOf("a", "c"), p.ids())
        assertTrue(p.enabled)
    }

    @Test
    fun `remove последней записи выключает пул`() {
        val p = pool("a", enabled = true).remove("a").changed()
        assertTrue(p.entries.isEmpty())
        assertFalse(p.enabled)
    }

    @Test
    fun `remove — нет такой записи`() {
        assertEquals(RelayPoolError.NOT_FOUND, pool("a").remove("zzz").rejected())
    }

    // ---- move ----

    @Test
    fun `moveUp и moveDown меняют порядок`() {
        val p = pool("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), p.moveUp("b").changed().ids())
        assertEquals(listOf("a", "c", "b"), p.moveDown("b").changed().ids())
        assertEquals(listOf("c", "a", "b"), p.moveUp("c").changed().moveUp("c").changed().ids())
    }

    @Test
    fun `перестановка на краю отклоняется`() {
        val p = pool("a", "b", "c")
        assertEquals(RelayPoolError.CANNOT_MOVE, p.moveUp("a").rejected())
        assertEquals(RelayPoolError.CANNOT_MOVE, p.moveDown("c").rejected())
        assertEquals(RelayPoolError.NOT_FOUND, p.moveUp("zzz").rejected())
        assertEquals(RelayPoolError.CANNOT_MOVE, pool("a").moveDown("a").rejected())
    }

    @Test
    fun `переставленные записи не теряют данные`() {
        val p = RelayPool(entries = listOf(entry("a", label = "один", pin = pin), entry("b", label = "два")))
        val moved = p.moveDown("a").changed()
        assertEquals(listOf("b", "a"), moved.ids())
        assertEquals("один", moved.entries[1].label)
        assertEquals(pin, moved.entries[1].pinSha256)
    }

    // ---- usable / active ----

    @Test
    fun `usableEntries отсеивает неполные записи, activeEntries зависит от переключателя`() {
        val full1 = entry("a")
        val noToken = entry("b", token = "")
        val full2 = entry("c")
        val p = RelayPool(enabled = false, entries = listOf(full1, noToken, full2))
        assertEquals(listOf(full1, full2), p.usableEntries)
        assertTrue(p.activeEntries.isEmpty())
        assertFalse(p.isActive)

        val on = p.copy(enabled = true)
        assertEquals(listOf(full1, full2), on.activeEntries)
        assertTrue(on.isActive)
    }

    @Test
    fun `включённый пул без рабочих записей не активен`() {
        assertFalse(RelayPool(enabled = true).isActive)
        assertFalse(RelayPool(enabled = true, entries = listOf(entry("a", token = ""))).isActive)
    }

    @Test
    fun `значения по умолчанию — всё выключено`() {
        val p = RelayPool()
        assertFalse(p.enabled)
        assertFalse(p.batterySaver)
        assertTrue(p.entries.isEmpty())
    }
}
