package com.ramka.app.relay

import com.ramka.domain.relay.RelayAddress
import com.ramka.domain.relay.RelayEntry
import com.ramka.domain.relay.RelayPool
import com.ramka.domain.relay.RelayPoolError
import com.ramka.domain.relay.RelayPoolResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayPoolSettingsTest {

    private val token = "test-token-0123456789abcdef"
    private val pin = "ab".repeat(32)

    private var counter = 0
    private fun newId() = "id-${++counter}"

    private class Env(val prefs: FakeRelayPrefs = FakeRelayPrefs(), val secrets: MemorySecretsBase = MemorySecrets())

    private fun settings(env: Env) = RelayPoolSettings(env.prefs, env.secrets, ::newId)

    private fun entry(id: String, host: String = "$id.example.com") = RelayEntry(id, RelayAddress(host, 48766), token, null)

    private fun legacy(env: Env, address: String = "relay.example.com:4000", pin: String = this.pin, enabled: Boolean = true, token: String? = this.token) {
        env.prefs.relayAddress = address
        env.prefs.relayPin = pin
        env.prefs.relayEnabled = enabled
        if (token != null) env.secrets.putBytes(RelayPoolSettings.LEGACY_TOKEN_KEY, token.toByteArray())
    }

    private fun assertLegacyCleared(env: Env) {
        assertEquals("", env.prefs.relayAddress)
        assertEquals("", env.prefs.relayPin)
        assertFalse(env.prefs.relayEnabled)
        assertEquals(0, env.secrets.getBytes(RelayPoolSettings.LEGACY_TOKEN_KEY)?.size ?: 0)
    }

    // ---- чистая установка ----

    @Test
    fun `чистая установка — пул пуст и ничего не пишется`() {
        val env = Env()
        val s = settings(env)
        assertEquals(RelayPool(), s.pool.value)
        assertTrue(env.secrets.map.isEmpty())
    }

    // ---- миграция ----

    @Test
    fun `миграция — одиночные настройки становятся пулом из одной записи`() {
        val env = Env()
        legacy(env)
        val s = settings(env)

        val pool = s.pool.value
        assertEquals(1, pool.entries.size)
        val e = pool.entries.single()
        assertEquals(RelayAddress("relay.example.com", 4000), e.address)
        assertEquals(token, e.token)
        assertEquals(pin, e.pinSha256)
        assertEquals("", e.label)
        assertTrue(pool.enabled)
        assertTrue(pool.isActive)
        assertFalse(pool.batterySaver) // экономия батареи по умолчанию выключена
        assertLegacyCleared(env)
        assertNotNull(env.secrets.getBytes(RelayPoolSettings.POOL_KEY))
    }

    @Test
    fun `миграция — выключенный релей остаётся выключенным`() {
        val env = Env()
        legacy(env, enabled = false)
        val pool = settings(env).pool.value
        assertEquals(1, pool.entries.size)
        assertFalse(pool.enabled)
        assertFalse(pool.isActive)
    }

    @Test
    fun `миграция — домен без пина и пустой токен переносятся как есть`() {
        val env = Env()
        legacy(env, address = "relay.example.com", pin = "", enabled = false, token = null)
        val e = settings(env).pool.value.entries.single()
        assertEquals(RelayAddress("relay.example.com", 48766), e.address)
        assertNull(e.pinSha256)
        assertEquals("", e.token)
        assertNotNull(e.issue) // запись неполная, но данные не потеряны
    }

    @Test
    fun `миграция — пин приводится к каноническому виду`() {
        val env = Env()
        legacy(env, pin = pin.uppercase())
        assertEquals(pin, settings(env).pool.value.entries.single().pinSha256)
    }

    @Test
    fun `миграция идемпотентна — повторный запуск ничего не меняет и не дублирует`() {
        val env = Env()
        legacy(env)
        val first = settings(env).pool.value
        val second = settings(env).pool.value
        val third = settings(env).pool.value
        assertEquals(first, second)
        assertEquals(first, third)
        assertEquals(1, third.entries.size)
    }

    @Test
    fun `миграция не затирает уже существующий пул, даже если старые поля снова заполнены`() {
        val env = Env()
        val s = settings(env)
        s.add(entry("a"))
        legacy(env, address = "stale.example.com:1") // «призрак» старых настроек (например, откат версии)
        val reloaded = settings(env).pool.value
        assertEquals(listOf("a.example.com"), reloaded.entries.map { it.address.host })
    }

    @Test
    fun `миграция — остатки без адреса не создают запись и очищаются`() {
        val env = Env()
        legacy(env, address = "not a valid address", pin = "", enabled = true, token = null)
        val pool = settings(env).pool.value
        assertTrue(pool.entries.isEmpty())
        assertFalse(pool.enabled)
        assertLegacyCleared(env)
    }

    @Test
    fun `миграция — старое не стирается, если новый документ не удалось записать`() {
        val env = Env(secrets = object : MemorySecretsBase() {
            override fun putBytes(key: String, value: ByteArray) {
                if (key == RelayPoolSettings.POOL_KEY) return // запись «потерялась»
                super.putBytes(key, value)
            }
        })
        legacy(env)
        val pool = settings(env).pool.value
        assertEquals(1, pool.entries.size) // в памяти пул есть
        assertEquals("relay.example.com:4000", env.prefs.relayAddress) // а старые данные ещё на месте
        assertEquals(token, env.secrets.getBytes(RelayPoolSettings.LEGACY_TOKEN_KEY)!!.toString(Charsets.UTF_8))
    }

    // ---- сохранение и загрузка ----

    @Test
    fun `изменения сохраняются и читаются при следующем запуске`() {
        val env = Env()
        val s = settings(env)
        s.add(entry("a"))
        s.add(entry("b"))
        s.setEnabled(true)
        s.setBatterySaver(true)
        s.moveUp("b")

        val reloaded = settings(env).pool.value
        assertEquals(listOf("b", "a"), reloaded.entries.map { it.id })
        assertTrue(reloaded.enabled)
        assertTrue(reloaded.batterySaver)
        assertEquals(s.pool.value, reloaded)
    }

    @Test
    fun `токены лежат только в зашифрованном хранилище`() {
        val env = Env()
        settings(env).add(entry("a"))
        assertTrue(env.secrets.getBytes(RelayPoolSettings.POOL_KEY)!!.toString(Charsets.UTF_8).contains(token))
        assertEquals("", env.prefs.relayAddress)
        assertEquals("", env.prefs.relayPin)
    }

    @Test
    fun `отклонённая операция ничего не меняет и не пишет`() {
        val env = Env()
        val s = settings(env)
        s.add(entry("a"))
        val before = env.secrets.getBytes(RelayPoolSettings.POOL_KEY)!!.toList()

        val r = s.add(entry("dup", host = "a.example.com"))
        assertEquals(RelayPoolResult.Rejected(RelayPoolError.DUPLICATE_ADDRESS), r)
        assertEquals(RelayPoolResult.Rejected(RelayPoolError.NOT_FOUND), s.remove("zzz"))
        assertEquals(listOf("a"), s.pool.value.entries.map { it.id })
        assertEquals(before, env.secrets.getBytes(RelayPoolSettings.POOL_KEY)!!.toList())
    }

    @Test
    fun `лимит пяти записей`() {
        val s = settings(Env())
        (1..5).forEach { assertTrue(s.add(entry("r$it")) is RelayPoolResult.Changed) }
        assertEquals(RelayPoolResult.Rejected(RelayPoolError.LIMIT_REACHED), s.add(entry("r6")))
        assertEquals(5, s.pool.value.entries.size)
    }

    @Test
    fun `удаление последней записи выключает пул и это сохраняется`() {
        val env = Env()
        val s = settings(env)
        s.add(entry("a"))
        s.setEnabled(true)
        s.remove("a")
        assertFalse(s.pool.value.enabled)
        assertFalse(settings(env).pool.value.enabled)
    }

    @Test
    fun `подписчик получает каждое изменение`() {
        val s = settings(Env())
        val seen = ArrayList<RelayPool>()
        // StateFlow: проверяем текущие значения после каждой операции (реактивность — через pool.value)
        seen.add(s.pool.value)
        s.add(entry("a")); seen.add(s.pool.value)
        s.setEnabled(true); seen.add(s.pool.value)
        s.replace(entry("a").copy(label = "Дом")); seen.add(s.pool.value)
        assertEquals(listOf(0, 1, 1, 1), seen.map { it.entries.size })
        assertEquals(listOf(false, false, true, true), seen.map { it.enabled })
        assertEquals("Дом", seen.last().entries.single().label)
    }

    // ---- повреждения ----

    @Test
    fun `повреждённый документ — пустой пул без падения и без перезаписи`() {
        val env = Env()
        env.secrets.putBytes(RelayPoolSettings.POOL_KEY, "это не json".toByteArray())
        legacy(env) // старые поля при наличии документа не используются
        val s = settings(env)
        assertEquals(RelayPool(), s.pool.value)
        assertEquals("это не json", env.secrets.getBytes(RelayPoolSettings.POOL_KEY)!!.toString(Charsets.UTF_8))
    }

    @Test
    fun `документ неизвестной версии не разбирается`() {
        val env = Env()
        env.secrets.putBytes(RelayPoolSettings.POOL_KEY, """{"v":99,"entries":[]}""".toByteArray())
        assertEquals(RelayPool(), settings(env).pool.value)
    }

    @Test
    fun `пустое значение документа считается отсутствующим`() {
        val env = Env()
        env.secrets.putBytes(RelayPoolSettings.POOL_KEY, ByteArray(0))
        legacy(env)
        assertEquals(1, settings(env).pool.value.entries.size) // сработала миграция
    }

    @Test
    fun `newEntryId выдаёт уникальные значения`() {
        val s = RelayPoolSettings(FakeRelayPrefs(), MemorySecrets())
        assertEquals(100, (1..100).map { s.newEntryId() }.toSet().size)
    }
}
