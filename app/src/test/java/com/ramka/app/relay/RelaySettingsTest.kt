package com.ramka.app.relay

import com.ramka.crypto.keys.KeyValueStore
import com.ramka.domain.relay.RelayAddress
import com.ramka.domain.relay.RelayConfig
import com.ramka.domain.relay.RelayEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class FakeRelayPrefs : RelayPrefs {
    override var relayEnabled = false
    override var relayAddress = ""
    override var relayPin = ""
}

internal open class MemorySecretsBase : KeyValueStore {
    val map = HashMap<String, ByteArray>()
    override fun getBytes(key: String): ByteArray? = map[key]?.copyOf()
    override fun putBytes(key: String, value: ByteArray) {
        map[key] = value.copyOf()
    }
}

internal class MemorySecrets : MemorySecretsBase()

class RelaySettingsTest {

    private val token = "test-token-0123456789abcdef"
    private val pin = "ab".repeat(32)
    private val config = RelayConfig(true, RelayAddress("relay.example.com", 4000), token, pin)

    @Test
    fun `по умолчанию релей выключен и не настроен`() {
        val s = RelaySettings(FakeRelayPrefs(), MemorySecrets())
        assertEquals(RelayConfig(), s.config.value)
        assertNull(s.config.value.activeEndpoint)
    }

    @Test
    fun `save сохраняет и эмитит, повторная загрузка читает то же`() {
        val prefs = FakeRelayPrefs()
        val secrets = MemorySecrets()
        val s = RelaySettings(prefs, secrets)
        s.save(config)
        assertEquals(config, s.config.value)

        val reloaded = RelaySettings(prefs, secrets)
        assertEquals(config, reloaded.config.value)
        // Новые данные в старые поля не пишутся: всё живёт в документе пула.
        assertEquals("", prefs.relayAddress)
        assertEquals("", prefs.relayPin)
        assertFalse(prefs.relayEnabled)
    }

    @Test
    fun `токен лежит только в секретном хранилище`() {
        val prefs = FakeRelayPrefs()
        val secrets = MemorySecrets()
        RelaySettings(prefs, secrets).save(config)
        val document = secrets.map.getValue(RelayPoolSettings.POOL_KEY).toString(Charsets.UTF_8)
        assertTrue(document.contains(token))
        assertEquals("", prefs.relayAddress)
        assertEquals("", prefs.relayPin)
    }

    private fun secretsOf(s: RelaySettings): KeyValueStore = lastSecrets!!

    private var lastSecrets: MemorySecrets? = null

    @Test
    fun `setEnabled меняет только переключатель`() {
        val prefs = FakeRelayPrefs()
        lastSecrets = MemorySecrets()
        val s = RelaySettings(prefs, lastSecrets!!)
        s.save(config)
        s.setEnabled(false)
        assertFalse(s.config.value.enabled)
        assertEquals(config.copy(enabled = false), s.config.value)
        assertFalse(RelaySettings(prefs, secretsOf(s)).config.value.enabled) // переживает перезагрузку
        s.setEnabled(true)
        assertTrue(s.config.value.enabled)
    }

    @Test
    fun `пустой пин и сброс токена переживают перезагрузку`() {
        val prefs = FakeRelayPrefs()
        val secrets = MemorySecrets()
        val s = RelaySettings(prefs, secrets)
        s.save(config)
        s.save(RelayConfig(false, RelayAddress("relay.example.com", 4000), "", null))
        val reloaded = RelaySettings(prefs, secrets)
        assertEquals("", reloaded.config.value.token)
        assertNull(reloaded.config.value.pinSha256)
    }

    @Test
    fun `мост правит только первую запись пула, остальные не трогает`() {
        val prefs = FakeRelayPrefs()
        val secrets = MemorySecrets()
        val pool = RelayPoolSettings(prefs, secrets)
        pool.add(RelayEntry("a", RelayAddress("first.example.com", 1), token, null))
        pool.add(RelayEntry("b", RelayAddress("second.example.com", 2), token, null, "второй"))
        val s = RelaySettings(pool)

        s.save(RelayConfig(true, RelayAddress("edited.example.com", 3), token, pin))

        val entries = pool.pool.value.entries
        assertEquals(listOf("a", "b"), entries.map { it.id })
        assertEquals(RelayAddress("edited.example.com", 3), entries[0].address)
        assertEquals(pin, entries[0].pinSha256)
        assertEquals(RelayAddress("second.example.com", 2), entries[1].address)
        assertEquals("второй", entries[1].label)
        assertTrue(pool.pool.value.enabled)
    }

    @Test
    fun `сохранение без адреса убирает первую запись`() {
        val pool = RelayPoolSettings(FakeRelayPrefs(), MemorySecrets())
        val s = RelaySettings(pool)
        s.save(config)
        assertEquals(1, pool.pool.value.entries.size)
        s.save(RelayConfig(false, null, "", null))
        assertTrue(pool.pool.value.entries.isEmpty())
        assertNull(s.config.value.address)
    }

    @Test
    fun `испорченный адрес в настройках не ломает загрузку`() {
        val prefs = FakeRelayPrefs().also {
            it.relayAddress = "not a valid address"
            it.relayEnabled = true
        }
        val s = RelaySettings(prefs, MemorySecrets())
        assertNull(s.config.value.address)
        assertNull(s.config.value.activeEndpoint)
    }
}
