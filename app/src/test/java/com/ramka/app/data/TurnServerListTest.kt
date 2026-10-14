package com.ramka.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnServerListTest {

    private fun entry(id: String, order: Int = 0, enabled: Boolean = true) =
        TurnServerEntry(id = id, url = "turn:h$id:3478", username = "u$id", isEnabled = enabled, order = order)

    private fun ids(list: List<TurnServerEntry>) = list.map { it.id }

    @Test
    fun addAppendsAndAssignsDenseOrder() {
        val list = TurnServerList.add(TurnServerList.add(emptyList(), entry("a")), entry("b"))
        assertEquals(listOf("a", "b"), ids(list))
        assertEquals(listOf(0, 1), list.map { it.order })
    }

    @Test
    fun normalizeSortsAndRenumbers() {
        val list = TurnServerList.normalize(listOf(entry("b", 7), entry("a", 3), entry("c", 9)))
        assertEquals(listOf("a", "b", "c"), ids(list))
        assertEquals(listOf(0, 1, 2), list.map { it.order })
    }

    @Test
    fun removeRenumbers() {
        val base = listOf(entry("a", 0), entry("b", 1), entry("c", 2))
        val list = TurnServerList.remove(base, "b")
        assertEquals(listOf("a", "c"), ids(list))
        assertEquals(listOf(0, 1), list.map { it.order })
    }

    @Test
    fun removeUnknownIdChangesNothing() {
        val base = listOf(entry("a", 0), entry("b", 1))
        assertEquals(base, TurnServerList.remove(base, "zzz"))
    }

    @Test
    fun moveUpSwapsWithPrevious() {
        val base = listOf(entry("a", 0), entry("b", 1), entry("c", 2))
        val list = TurnServerList.moveUp(base, "c")
        assertEquals(listOf("a", "c", "b"), ids(list))
        assertEquals(listOf(0, 1, 2), list.map { it.order })
    }

    @Test
    fun moveDownSwapsWithNext() {
        val base = listOf(entry("a", 0), entry("b", 1), entry("c", 2))
        assertEquals(listOf("b", "a", "c"), ids(TurnServerList.moveDown(base, "a")))
    }

    @Test
    fun moveAtEdgesAndUnknownIdIsNoOp() {
        val base = listOf(entry("a", 0), entry("b", 1))
        assertEquals(ids(base), ids(TurnServerList.moveUp(base, "a")))
        assertEquals(ids(base), ids(TurnServerList.moveDown(base, "b")))
        assertEquals(ids(base), ids(TurnServerList.moveUp(base, "zzz")))
    }

    @Test
    fun updateKeepsPositionEvenIfEntryCarriesAnotherOrder() {
        val base = listOf(entry("a", 0), entry("b", 1), entry("c", 2))
        val list = TurnServerList.update(base, entry("b", order = 99, enabled = false))
        assertEquals(listOf("a", "b", "c"), ids(list))
        assertEquals(1, list[1].order)
        assertTrue(!list[1].isEnabled)
    }
}
