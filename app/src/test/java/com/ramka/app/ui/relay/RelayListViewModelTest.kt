package com.ramka.app.ui.relay

import com.ramka.app.relay.RelayPoolController
import com.ramka.app.relay.RelayPoolStore
import com.ramka.app.relay.RelayPoolUiState
import com.ramka.domain.relay.PoolRelayProvider
import com.ramka.domain.relay.RelayAddress
import com.ramka.domain.relay.RelayEntry
import com.ramka.domain.relay.RelayFailure
import com.ramka.domain.relay.RelayLinkState
import com.ramka.domain.relay.RelayPool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayListViewModelTest {

    private class FakeStore(initial: RelayPool = RelayPool()) : RelayPoolStore {
        private val _pool = MutableStateFlow(initial)
        override val pool: StateFlow<RelayPool> = _pool
        override fun save(pool: RelayPool) { _pool.value = pool }
    }

    private fun entry(id: String) =
        RelayEntry(id = id, address = RelayAddress("$id.example.com", 48766), token = "t".repeat(32), pinSha256 = null)

    private fun vm(store: FakeStore) =
        RelayListViewModel(RelayPoolController(store), store, PoolRelayProvider(store))

    private fun ids(vm: RelayListViewModel) = vm.current().items.map { it.entry.id }

    @Test
    fun `пустой пул - подсказка и заблокированный переключатель`() {
        val vm = vm(FakeStore())
        val s = vm.current()
        assertFalse(s.toggleAvailable)
        assertEquals(RelayPoolUiState.TOGGLE_HINT_EMPTY, s.toggleHint)
        assertFalse(s.enabled)
        vm.onEnabledChanged(true)
        assertFalse(vm.current().enabled)
    }

    @Test
    fun `с записями переключатель доступен и включается без подсказки`() {
        val vm = vm(FakeStore(RelayPool(entries = listOf(entry("a")))))
        assertTrue(vm.current().toggleAvailable)
        assertNull(vm.current().toggleHint)
        vm.onEnabledChanged(true)
        assertTrue(vm.current().enabled)
    }

    @Test
    fun `moveUp и moveDown меняют порядок`() {
        val vm = vm(FakeStore(RelayPool(entries = listOf(entry("a"), entry("b"), entry("c")))))
        vm.onMoveDown("a")
        assertEquals(listOf("b", "a", "c"), ids(vm))
        vm.onMoveUp("c")
        assertEquals(listOf("b", "c", "a"), ids(vm))
        // крайние позиции: перестановка ничего не меняет, кнопки заблокированы
        vm.onMoveUp("b")
        assertEquals(listOf("b", "c", "a"), ids(vm))
        val items = vm.current().items
        assertFalse(items.first().canMoveUp)
        assertTrue(items.first().canMoveDown)
        assertFalse(items.last().canMoveDown)
    }

    @Test
    fun `удаление последней записи выключает переключатель`() {
        val vm = vm(FakeStore(RelayPool(enabled = true, entries = listOf(entry("a")))))
        assertTrue(vm.current().enabled)
        vm.onRemove("a")
        val s = vm.current()
        assertTrue(s.items.isEmpty())
        assertFalse(s.enabled)
        assertFalse(s.toggleAvailable)
        assertEquals(RelayPoolUiState.TOGGLE_HINT_EMPTY, s.toggleHint)
    }

    @Test
    fun `добавлять можно не больше пяти релеёв`() {
        val five = (1..RelayPool.MAX_ENTRIES).map { entry("r$it") }
        assertFalse(vm(FakeStore(RelayPool(entries = five))).current().canAdd)
        assertTrue(vm(FakeStore(RelayPool(entries = five.drop(1)))).current().canAdd)
    }

    @Test
    fun `бейдж берётся из флага пула и состояния связи`() {
        val e = entry("a")
        assertEquals(RelayBadgeState.Disabled, relayBadgeFor(false, e, RelayLinkState.Connected))
        assertEquals(RelayBadgeState.Unknown, relayBadgeFor(true, e, null))
        assertEquals(RelayBadgeState.Checking, relayBadgeFor(true, e, RelayLinkState.Connecting))
        assertEquals(RelayBadgeState.Ok, relayBadgeFor(true, e, RelayLinkState.Connected))
        assertEquals(RelayBadgeState.Ok, relayBadgeFor(true, e, RelayLinkState.Reachable(12)))
        assertEquals(
            RelayBadgeState.Error(RelayFailure.AUTH),
            relayBadgeFor(true, e, RelayLinkState.Failed(RelayFailure.AUTH))
        )
    }

    @Test
    fun `неполная запись в бейдже неизвестна даже при связи`() {
        val incomplete = RelayEntry("x", RelayAddress("x.example.com", 48766), token = "", pinSha256 = null)
        assertEquals(RelayBadgeState.Unknown, relayBadgeFor(true, incomplete, RelayLinkState.Connected))
    }
}
