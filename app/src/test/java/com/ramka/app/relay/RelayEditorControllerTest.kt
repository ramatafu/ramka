package com.ramka.app.relay

import com.ramka.domain.relay.RelayCheckResult
import com.ramka.domain.relay.RelayChecker
import com.ramka.domain.relay.RelayEndpoint
import com.ramka.domain.relay.RelayPool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayEditorControllerTest {

    private class FakeStore : RelayPoolStore {
        private val _pool = MutableStateFlow(RelayPool(entries = emptyList(), enabled = false))
        override val pool: StateFlow<RelayPool> = _pool
        override fun save(pool: RelayPool) { _pool.value = pool }
    }

    private class FakeChecker(var ok: Boolean = true) : RelayChecker {
        var calls = 0
        override suspend fun check(endpoint: RelayEndpoint): RelayCheckResult {
            calls++
            return if (ok) RelayCheckResult.Ok else RelayCheckResult.Err(com.ramka.domain.relay.RelayFailure.TIMEOUT)
        }
    }

    private val store = FakeStore()
    private val poolController = RelayPoolController(store)
    private val checker = FakeChecker()
    private val editor = RelayEditorController(poolController, checker)

    private fun fillValid(host: String = "relay.example.com") {
        editor.onAddressChanged(host)
        editor.onTokenChanged("x".repeat(32))
        editor.onPinChanged("ab".repeat(32))
    }

    @Test
    fun `новый черновик не грязный и без ошибок`() {
        editor.openNew()
        assertFalse(editor.state.value.dirty)
        assertNull(editor.state.value.errors)
        assertFalse(editor.state.value.canSave)
    }

    @Test
    fun `изменение поля грязнит и сбрасывает проверку`() = runBlocking {
        fillValid()
        assertTrue(editor.state.value.dirty)
        editor.check()
        assertEquals(RelayCheckUi.Ok, editor.state.value.check)
        editor.onPinChanged("cd".repeat(32))
        assertEquals(RelayCheckUi.Idle, editor.state.value.check)
        assertTrue(editor.state.value.dirty)
    }

    @Test
    fun `сохранение невалидного черновика не трогает пул`() {
        editor.onAddressChanged("не адрес")
        assertFalse(editor.save())
        assertTrue(store.pool.value.entries.isEmpty())
    }

    @Test
    fun `сохранение валидного добавляет запись и снимает грязность`() {
        fillValid()
        assertTrue(editor.save())
        assertEquals(1, store.pool.value.entries.size)
        assertFalse(editor.state.value.dirty)
        assertEquals(editor.state.value.editingId, store.pool.value.entries.first().id)
    }

    @Test
    fun `повторное сохранение заменяет запись а не добавляет`() {
        fillValid()
        assertTrue(editor.save())
        editor.onTokenChanged("y".repeat(32))
        assertTrue(editor.save())
        assertEquals(1, store.pool.value.entries.size)
        assertEquals("y".repeat(32), store.pool.value.entries.first().token)
    }

    @Test
    fun `проверка невалидного черновика не ходит в сеть и оставляет Idle`() = runBlocking {
        editor.onAddressChanged("relay.example.com") // без токена и пина
        assertEquals(RelayCheckUi.Idle, editor.check())
        assertEquals(0, checker.calls)
    }

    @Test
    fun `проверка валидного черновика отражает Ok и Failed`() = runBlocking {
        fillValid()
        assertEquals(RelayCheckUi.Ok, editor.check())
        checker.ok = false
        editor.onPinChanged("cd".repeat(32)) // сброс
        assertEquals(RelayCheckUi.Failed, editor.check())
        assertEquals(2, checker.calls)
    }

    @Test
    fun `delete удаляет запись и открывает пустую форму`() {
        fillValid()
        assertTrue(editor.save())
        val id = editor.state.value.editingId!!
        assertTrue(editor.delete())
        assertTrue(store.pool.value.entries.isEmpty())
        assertNull(editor.state.value.editingId)
        assertFalse(editor.state.value.dirty)
        assertEquals(id, id)
    }

    @Test
    fun `resetCheck возвращает Idle после Failed`() = runBlocking {
        fillValid()
        checker.ok = false
        editor.check()
        assertEquals(RelayCheckUi.Failed, editor.state.value.check)
        editor.resetCheck()
        assertEquals(RelayCheckUi.Idle, editor.state.value.check)
    }
}