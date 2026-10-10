package com.ramka.app.ui.relay

import androidx.lifecycle.SavedStateHandle
import com.ramka.app.relay.RelayCheckUi
import com.ramka.app.relay.RelayEditorController
import com.ramka.app.relay.RelayPoolController
import com.ramka.app.relay.RelayPoolStore
import com.ramka.domain.relay.RelayAddress
import com.ramka.domain.relay.RelayCheckResult
import com.ramka.domain.relay.RelayChecker
import com.ramka.domain.relay.RelayEndpoint
import com.ramka.domain.relay.RelayEntry
import com.ramka.domain.relay.RelayFailure
import com.ramka.domain.relay.RelayPool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayEditViewModelTest {

    private class FakeStore(initial: RelayPool = RelayPool()) : RelayPoolStore {
        private val _pool = MutableStateFlow(initial)
        override val pool: StateFlow<RelayPool> = _pool
        override fun save(pool: RelayPool) { _pool.value = pool }
    }

    private class FakeChecker(var result: RelayCheckResult = RelayCheckResult.Ok) : RelayChecker {
        var calls = 0
        override suspend fun check(endpoint: RelayEndpoint): RelayCheckResult {
            calls++
            return result
        }
    }

    private val store = FakeStore()
    private val checker = FakeChecker()

    private fun vm(entryId: String? = null, s: FakeStore = store): RelayEditViewModel {
        val pool = RelayPoolController(s)
        val handle = if (entryId == null) SavedStateHandle() else SavedStateHandle(mapOf("entryId" to entryId))
        return RelayEditViewModel(handle, RelayEditorController(pool, checker), pool)
    }

    private fun RelayEditViewModel.fillValid(host: String = "relay.example.com") {
        onAddressChanged(host)
        onTokenChanged("x".repeat(32))
        onPinChanged("ab".repeat(32))
    }

    private fun existing(id: String, host: String) =
        RelayEntry(id, RelayAddress(host, 48766), "t".repeat(32), null, label = "Дом")

    @Test
    fun `save невалидного черновика не пишет в пул`() {
        val vm = vm()
        vm.onAddressChanged("не адрес")
        assertFalse(vm.save())
        assertTrue(store.pool.value.entries.isEmpty())
    }

    @Test
    fun `новая запись сохраняется в пул`() {
        val vm = vm()
        assertFalse(vm.isEditing)
        vm.fillValid()
        assertTrue(vm.state.value.canSave)
        assertTrue(vm.save())
        assertEquals(1, store.pool.value.entries.size)
        assertEquals("relay.example.com", store.pool.value.entries.first().address.host)
    }

    @Test
    fun `sentinel new открывает новую запись`() {
        assertFalse(vm("new").isEditing)
    }

    @Test
    fun `entryId открывает существующую запись в редакторе`() {
        val s = FakeStore(RelayPool(entries = listOf(existing("a", "a.example.com"))))
        val vm = vm("a", s)
        assertTrue(vm.isEditing)
        assertEquals("a", vm.state.value.editingId)
        assertEquals("a.example.com:48766", vm.state.value.address)
        assertEquals("Дом", vm.state.value.label)
        assertFalse(vm.state.value.dirty)
    }

    @Test
    fun `неизвестный entryId открывает новую запись`() {
        assertFalse(vm("нет-такой").isEditing)
    }

    @Test
    fun `delete удаляет открытую запись`() {
        val s = FakeStore(RelayPool(enabled = true, entries = listOf(existing("a", "a.example.com"))))
        val vm = vm("a", s)
        assertTrue(vm.delete())
        assertTrue(s.pool.value.entries.isEmpty())
        assertFalse(s.pool.value.enabled)
    }

    @Test
    fun `delete новой записи ничего не делает`() {
        val s = FakeStore(RelayPool(entries = listOf(existing("a", "a.example.com"))))
        val vm = vm(null, s)
        assertFalse(vm.delete())
        assertEquals(1, s.pool.value.entries.size)
    }

    @Test
    fun `дубликат адреса не сохраняется и даёт ошибку`() {
        val s = FakeStore(RelayPool(entries = listOf(existing("a", "relay.example.com"))))
        val vm = vm(null, s)
        vm.fillValid("relay.example.com")
        assertFalse(vm.save())
        assertEquals(RelaySaveError.DUPLICATE_ADDRESS, vm.saveError.value)
        assertEquals(1, s.pool.value.entries.size)
        vm.onLabelChanged("другая")
        assertNull(vm.saveError.value)
    }

    @Test
    fun `в полный пул новую запись не добавить`() {
        val full = (1..RelayPool.MAX_ENTRIES).map { existing("r$it", "r$it.example.com") }
        val s = FakeStore(RelayPool(entries = full))
        val vm = vm(null, s)
        vm.fillValid("new.example.com")
        assertFalse(vm.save())
        assertEquals(RelaySaveError.LIMIT_REACHED, vm.saveError.value)
        assertEquals(RelayPool.MAX_ENTRIES, s.pool.value.entries.size)
    }

    @Test
    fun `check при Failed-чекере даёт Failed с причиной`() = runBlocking {
        checker.result = RelayCheckResult.Failed(RelayFailure.AUTH)
        val vm = vm()
        vm.fillValid()
        assertEquals(RelayCheckUi.Failed(RelayFailure.AUTH), vm.runCheck())
        assertEquals(RelayCheckUi.Failed(RelayFailure.AUTH), vm.state.value.check)
        assertEquals(1, checker.calls)
    }

    @Test
    fun `check невалидного черновика в сеть не ходит`() = runBlocking {
        val vm = vm()
        vm.onAddressChanged("relay.example.com")
        assertEquals(RelayCheckUi.Idle, vm.runCheck())
        assertEquals(0, checker.calls)
    }
}
