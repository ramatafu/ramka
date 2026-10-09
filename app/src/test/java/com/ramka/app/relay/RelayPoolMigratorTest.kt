package com.ramka.app.relay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelayPoolMigratorTest {

    private val good = LegacyRelaySnapshot(
        enabled = true,
        host = "relay.example.com",
        port = 443,
        token = "x".repeat(32),
        pin = "ab".repeat(32)
    )

    @Test
    fun `есть старое валидное настроечное - одна запись пула с теми же реквизитами`() {
        val entry = RelayPoolMigrator.migrate(good)!!
        assertEquals(RelayPoolMigrator.LEGACY_ENTRY_ID, entry.id)
        assertEquals("relay.example.com:443", entry.address.format())
        assertEquals("x".repeat(32), entry.token)
        assertEquals("ab".repeat(32), entry.pinSha256)
    }

    @Test
    fun `нет старого - пул пустой без падения`() {
        assertNull(RelayPoolMigrator.migrate(null))
    }

    @Test
    fun `повреждённое старое не мигрирует`() {
        assertNull(RelayPoolMigrator.migrate(good.copy(host = null)))
        assertNull(RelayPoolMigrator.migrate(good.copy(host = " ")))
        assertNull(RelayPoolMigrator.migrate(good.copy(port = null)))
        assertNull(RelayPoolMigrator.migrate(good.copy(token = null)))
        assertNull(RelayPoolMigrator.migrate(good.copy(token = "short")))          // < 16 байт
        assertNull(RelayPoolMigrator.migrate(good.copy(host = "not a host!!")))
        // IP без пина мигрировать не должен
        assertNull(RelayPoolMigrator.migrate(good.copy(host = "203.0.113.7", pin = null)))
        // IP с битым пином тоже
        assertNull(RelayPoolMigrator.migrate(good.copy(host = "203.0.113.7", pin = "zz")))
    }

    @Test
    fun `повторный запуск миграции на очищенном снимке безопасен`() {
        val first = RelayPoolMigrator.migrate(good)
        assertNull(RelayPoolMigrator.migrate(null)) // снимок уже очищен вызывающим
        assertEquals(RelayPoolMigrator.LEGACY_ENTRY_ID, first!!.id)
    }
}