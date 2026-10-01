package com.ramka.app.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanVisibilityControllerTest {

    private class FakePrefs(override var mdnsEnabled: Boolean = true) : MdnsPrefs // как в AppPreferences: по умолчанию ВКЛ

    /** Фейк, считающий именно записи в хранилище (через setter интерфейса). */
    private class CountingPrefs(initial: Boolean = true) : MdnsPrefs {
        var writes = 0
        private var value = initial
        override var mdnsEnabled: Boolean
            get() = value
            set(v) { writes++; value = v }
    }

    @Test
    fun `enabled by default`() {
        assertTrue(LanVisibilityController(FakePrefs()).enabled.value)
    }

    @Test
    fun `initial value comes from stored preference`() {
        assertFalse(LanVisibilityController(FakePrefs(mdnsEnabled = false)).enabled.value)
    }

    @Test
    fun `setEnabled persists and updates the flow value`() {
        val prefs = CountingPrefs()
        val controller = LanVisibilityController(prefs)

        controller.setEnabled(false)

        assertFalse(prefs.mdnsEnabled)
        assertFalse(controller.enabled.value)

        controller.setEnabled(true)

        assertTrue(prefs.mdnsEnabled)
        assertTrue(controller.enabled.value)
        assertEquals(2, prefs.writes)
    }

    @Test
    fun `setting the same value does not write again`() {
        val prefs = CountingPrefs()
        val controller = LanVisibilityController(prefs)

        controller.setEnabled(true)
        controller.setEnabled(true)

        assertEquals(0, prefs.writes)
    }

    @Test
    fun `new controller restores the persisted choice`() {
        val prefs = CountingPrefs()
        LanVisibilityController(prefs).setEnabled(false)

        assertFalse(LanVisibilityController(prefs).enabled.value)
    }
}
