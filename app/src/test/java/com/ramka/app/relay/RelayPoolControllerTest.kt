package com.ramka.app.relay

import com.ramka.domain.relay.RelayAddress
import com.ramka.domain.relay.RelayEntry
import com.ramka.domain.relay.RelayPool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class RelayPoolControllerTest {

    private class FakeStore(initial: RelayPool) : RelayPoolStore {
        private val _pool = MutableStateFlow(initial)
        override val pool: StateFlow<RelayPool> = _pool
        override fun save(pool: RelayPool) { _pool.value = pool }
    }

    private fun entry(id: String, host: String) = RelayEntry(
        id,
        (RelayAddress.parse("$host:443") as com.ramka.domain.relay.RelayAddressParseResult.Success).address,
        "x".repeat(32),
        "ab".repeat(32),
        ""
    )

    private fun controller(vararg entries: RelayEntry, enabled: Boolean = false) =
        RelayPoolController(FakeStore(RelayPool(entries = entries.toList(), enabled = enabled)))

    @Test
    fun `переключатель при пустом списке заблокирован и даёт подсказку`() {
        val c = controller()
        val ui = c.uiState()
        assertFalse(ui.toggleAvailable)
        assertEquals(RelayPoolUiState.TOGGLE_HINT_EMPTY, ui.toggleHint)
        assertFalse(c.setPoolEnabled(true))
        assertFalse(c.current().enabled)
    }

    @Test
    fun `включение непустого пула и выключение`() {
        val c = controller(entry("a", "relay.example.com"))
        assertTrue(c.setPoolEnabled(true))
        assertTrue(c.current().enabled)
        assertNull(c.uiState().toggleHint)
        assertTrue(c.setPoolEnabled(false))
        assertFalse(c.current().enabled)
    }

    @Test
    fun `add отвергает дубликат адреса`() {
        val c = controller()
        assertTrue(c.add(entry("a", "relay.example.com")))
        assertFalse(c.add(entry("b", "relay.example.com")))
        assertEquals(1, c.current().entries.size)
    }

    @Test
    fun `moveUp и moveDown меняют порядок`() {
        val c = controller(entry("a", "a.example.com"), entry("b", "b.example.com"), entry("c", "c.example.com"))
        assertTrue(c.moveDown("a"))
        assertEquals(listOf("b", "a", "c"), c.current().entries.map { it.id })
        assertTrue(c.moveUp("a"))
        assertEquals(listOf("a", "b", "c"), c.current().entries.map { it.id })
        assertFalse(c.moveUp("a"))   // уже первый
        assertFalse(c.moveDown("c")) // уже последний
        assertFalse(c.move("x".let { "nope" }))
    }

    private fun RelayPoolController.move(id: String): Boolean = moveUp(id) // вспомогательная проверка отсутствующего id

    @Test
    fun `удаление последнего релея выключает переключатель автоматически`() {
        val c = controller(entry("a", "a.example.com"), enabled = true)
        assertTrue(c.current().enabled)
        assertTrue(c.remove("a"))
        assertFalse(c.current().enabled)
        assertTrue(c.current().entries.isEmpty())
    }

    @Test
    fun `удаление несуществующей записи не меняет пул`() {
        val c = controller(entry("a", "a.example.com"), enabled = true)
        assertFalse(c.remove("nope"))
        assertEquals(1, c.current().entries.size)
        assertTrue(c.current().enabled)
    }

    @Test
    fun `replace заменяет запись и отвергает чужой адрес`() {
        val c = controller(entry("a", "a.example.com"), entry("b", "b.example.com"))
        val updated = entry("a", "c.example.com")
        assertTrue(c.replace(updated))
        assertEquals("c.example.com", c.current().entries.first().address.format().substringBefore(':'))
        assertFalse(c.replace(entry("a", "b.example.com"))) // адрес занят записью b
    }
}