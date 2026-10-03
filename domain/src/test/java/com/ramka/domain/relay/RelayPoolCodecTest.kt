package com.ramka.domain.relay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayPoolCodecTest {

    private val token = "test-token-0123456789abcdef"
    private val pin = "ab".repeat(32)

    private var counter = 0
    private fun newId() = "gen-${++counter}"

    private fun entry(id: String, host: String, port: Int = 48766, token: String = this.token, pin: String? = null, label: String = "") =
        RelayEntry(id, RelayAddress(host, port), token, pin, label)

    private fun decode(text: String) = RelayPoolCodec.decode(text, ::newId)

    @Test
    fun `круг encode-decode сохраняет всё`() {
        val pool = RelayPool(
            enabled = true,
            batterySaver = true,
            entries = listOf(
                entry("id-1", "relay1.example.com", label = "Дом"),
                entry("id-2", "203.0.113.7", port = 4000, pin = pin),
                entry("id-3", "2001:db8::7", pin = pin, token = "", label = "без токена")
            )
        )
        assertEquals(pool, decode(RelayPoolCodec.encode(pool)))
    }

    @Test
    fun `пустой пул и значения по умолчанию`() {
        assertEquals(RelayPool(), decode(RelayPoolCodec.encode(RelayPool())))
        val minimal = decode("""{"v":1}""")
        assertEquals(RelayPool(), minimal)
    }

    @Test
    fun `спецсимволы в имени и токене переживают круг`() {
        val nasty = "He said \"hi\" \\ \n ё 😀"
        val pool = RelayPool(entries = listOf(entry("a", "relay.example.com", token = "tok\"en\\0123456789abcdef", label = nasty.replace("\n", " "))))
        val back = decode(RelayPoolCodec.encode(pool))!!
        assertEquals(pool.entries[0].token, back.entries[0].token)
        assertEquals(pool.entries[0].label, back.entries[0].label)
    }

    @Test
    fun `формат содержит версию и ожидаемые поля`() {
        val json = RelayPoolCodec.encode(RelayPool(true, false, listOf(entry("a", "relay.example.com", pin = pin))))
        assertTrue(json.startsWith("""{"v":1,"enabled":true,"batterySaver":false,"entries":[{"id":"a","label":"","address":"relay.example.com:48766","token":"""))
        assertTrue(json.contains(""""pin":"$pin""""))
    }

    @Test
    fun `не JSON, не объект и неизвестная версия — null`() {
        for (bad in listOf("", "garbage", "[]", "\"x\"", "null", "42", """{"entries":[]}""", """{"v":2,"entries":[]}""", """{"v":"1"}""", """{"v":1.0}""", """{"v":1""")) {
            assertNull("«$bad»", decode(bad))
        }
    }

    @Test
    fun `битые записи пропускаются, остальные живут`() {
        val pool = decode(
            """{"v":1,"entries":[
                 "строка вместо объекта",
                 {"id":"a","address":"relay1.example.com","token":"$token"},
                 {"id":"b","address":"bad host","token":"$token"},
                 {"id":"c","token":"$token"},
                 {"id":"d","address":123},
                 {"id":"e","address":"relay2.example.com","token":"$token"}
               ]}"""
        )!!
        assertEquals(listOf("a", "e"), pool.entries.map { it.id })
    }

    @Test
    fun `дубликаты адреса и лимит записей`() {
        val dup = decode(
            """{"v":1,"entries":[
                 {"id":"a","address":"Relay.Example.com:48766","token":"$token"},
                 {"id":"b","address":"relay.example.com","token":"$token"}
               ]}"""
        )!!
        assertEquals(listOf("a"), dup.entries.map { it.id })

        val many = (1..8).joinToString(",") { """{"id":"r$it","address":"relay$it.example.com","token":"$token"}""" }
        val limited = decode("""{"v":1,"entries":[$many]}""")!!
        assertEquals(RelayPool.MAX_ENTRIES, limited.entries.size)
        assertEquals((1..5).map { "r$it" }, limited.entries.map { it.id })
    }

    @Test
    fun `отсутствующий, пустой и повторяющийся id заменяются новыми`() {
        counter = 0
        val pool = decode(
            """{"v":1,"entries":[
                 {"address":"relay1.example.com","token":"$token"},
                 {"id":"","address":"relay2.example.com","token":"$token"},
                 {"id":"same","address":"relay3.example.com","token":"$token"},
                 {"id":"same","address":"relay4.example.com","token":"$token"}
               ]}"""
        )!!
        val ids = pool.entries.map { it.id }
        assertEquals(4, ids.size)
        assertEquals(4, ids.toSet().size)
        assertEquals("same", ids[2])
        assertTrue(ids.all { it.isNotBlank() })
    }

    @Test
    fun `адрес нормализуется, пин приводится к каноническому виду, плохой пин отбрасывается`() {
        val pool = decode(
            """{"v":1,"entries":[
                 {"id":"a","address":"RELAY.example.com","token":"$token","pin":"${pin.uppercase()}"},
                 {"id":"b","address":"relay2.example.com","token":"$token","pin":"не пин"},
                 {"id":"c","address":"relay3.example.com","token":"$token","pin":null}
               ]}"""
        )!!
        assertEquals("relay.example.com", pool.entries[0].address.host)
        assertEquals(pin, pool.entries[0].pinSha256)
        assertNull(pool.entries[1].pinSha256)
        assertNull(pool.entries[2].pinSha256)
    }

    @Test
    fun `имя обрезается, управляющие символы убираются, токен по умолчанию пустой`() {
        val pool = decode(
            """{"v":1,"entries":[
                 {"id":"a","address":"relay.example.com","label":"  ${"x".repeat(60)}  "},
                 {"id":"b","address":"relay2.example.com","label":"a\nb\tc"}
               ]}"""
        )!!
        assertEquals(RelayEntry.MAX_LABEL_LENGTH, pool.entries[0].label.length)
        assertEquals("abc", pool.entries[1].label)
        assertEquals("", pool.entries[0].token)
        assertNotNull(pool.entries[0].issue)
    }

    @Test
    fun `неизвестные поля игнорируются, неверные типы флагов дают значения по умолчанию`() {
        val pool = decode("""{"v":1,"enabled":"yes","batterySaver":1,"future":{"x":[1,2]},"entries":[]}""")!!
        assertFalse(pool.enabled)
        assertFalse(pool.batterySaver)
    }

    @Test
    fun `кодированный пул не содержит неожиданного`() {
        val json = RelayPoolCodec.encode(RelayPool(entries = listOf(entry("a", "relay.example.com"))))
        assertFalse(json.contains("null,") && json.contains("undefined"))
        assertEquals(1, decode(json)!!.entries.size)
    }
}
