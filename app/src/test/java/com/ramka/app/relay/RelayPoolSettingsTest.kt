package com.ramka.app.relay

import com.ramka.domain.relay.RelayPool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayPoolSettingsTest {

    private class FakeKV : RelayKeyValue {
        val map = mutableMapOf<String, String>()
        override fun get(key: String): String? = map[key]
        override fun put(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
    }

    private class FakeSecrets : RelaySecrets {
        val map = mutableMapOf<String, String>()
        override fun get(alias: String): String? = map[alias]
        override fun put(alias: String, value: String) { map[alias] = value }
        override fun remove(alias: String) { map.remove(alias) }
    }

    private class FakeLegacy(var snapshot: LegacyRelaySnapshot?) : LegacyRelayReader {
        var clearCalls = 0
        override fun snapshot(): LegacyRelaySnapshot? = snapshot
        override fun clear() { clearCalls++; snapshot = null }
    }

    private val good = LegacyRelaySnapshot(
        enabled = true,
        address = "relay.example.com:443",
        token = "x".repeat(32),
        pin = "ab".repeat(32)
    )

    @Test
    fun `первый старт со старыми настройками мигрирует их в одну запись и очищает старое`() {
        val legacy = FakeLegacy(good)
        val settings = RelayPoolSettings(FakeKV(), FakeSecrets(), legacy)
        val pool = settings.pool.value
        assertEquals(1, pool.entries.size)
        assertTrue(pool.enabled)
        assertEquals(RelayPoolMigrator.LEGACY_ENTRY_ID, pool.entries.first().id)
        assertEquals(1, legacy.clearCalls)
    }

    @Test
    fun `первый старт без старых настроек даёт пустой выключенный пул`() {
        val legacy = FakeLegacy(null)
        val settings = RelayPoolSettings(FakeKV(), FakeSecrets(), legacy)
        assertEquals(0, settings.pool.value.entries.size)
        assertFalse(settings.pool.value.enabled)
        assertEquals(1, legacy.clearCalls)
    }

    @Test
    fun `повреждённое старое очищается и не попадает в пул`() {
        val legacy = FakeLegacy(good.copy(address = "!!"))
        val settings = RelayPoolSettings(FakeKV(), FakeSecrets(), legacy)
        assertEquals(0, settings.pool.value.entries.size)
        assertEquals(1, legacy.clearCalls)
    }

    @Test
    fun `повторный запуск читает сохранённый пул и не трогает старое повторно`() {
        val kv = FakeKV()
        val secrets = FakeSecrets()
        val legacy = FakeLegacy(good)
        val first = RelayPoolSettings(kv, secrets, legacy)
        assertEquals(1, first.pool.value.entries.size)
        
        val legacy2 = FakeLegacy(null)
        val second = RelayPoolSettings(kv, secrets, legacy2)
        assertEquals(1, second.pool.value.entries.size)
        assertTrue(second.pool.value.enabled)
        assertEquals(0, legacy2.clearCalls)
    }

    @Test
    fun `save публикует и сохраняет пул круговым циклом кодека`() {
        val secrets = FakeSecrets()
        val settings = RelayPoolSettings(FakeKV(), secrets, FakeLegacy(null))
        val entry = RelayPoolMigrator.migrate(good)!!
        settings.save(RelayPool(entries = listOf(entry), enabled = true))
        
        val reloaded = RelayPoolSettings(FakeKV(), secrets, FakeLegacy(null))
        assertEquals(1, reloaded.pool.value.entries.size)
        assertEquals(entry.id, reloaded.pool.value.entries.first().id)
    }
}