package com.ramka.domain.relay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniJsonTest {

    private fun fails(text: String) {
        try {
            MiniJson.parse(text)
            throw AssertionError("ожидалась ошибка разбора: «$text»")
        } catch (e: MiniJson.ParseException) {
            // ожидаемо
        }
    }

    @Test
    fun `скаляры`() {
        assertEquals(true, MiniJson.parse("true"))
        assertEquals(false, MiniJson.parse(" false "))
        assertNull(MiniJson.parse("null"))
        assertEquals(42L, MiniJson.parse("42"))
        assertEquals(-7L, MiniJson.parse("-7"))
        assertEquals(0L, MiniJson.parse("0"))
        assertEquals(1.5, MiniJson.parse("1.5"))
        assertEquals(1000.0, MiniJson.parse("1e3"))
        assertEquals(-0.25, MiniJson.parse("-2.5E-1"))
        assertEquals("текст", MiniJson.parse("\"текст\""))
    }

    @Test
    fun `объекты и массивы`() {
        val v = MiniJson.parse("""{"a":1,"b":[true,null,"x",{"c":[]}],"d":{}}""") as Map<*, *>
        assertEquals(1L, v["a"])
        assertEquals(listOf(true, null, "x", mapOf("c" to emptyList<Any?>())), v["b"])
        assertEquals(emptyMap<String, Any?>(), v["d"])
        assertEquals(listOf("a", "b", "d"), v.keys.toList()) // порядок ключей сохраняется
    }

    @Test
    fun `escape-последовательности`() {
        assertEquals("\"\\/\b\u000C\n\r\t", MiniJson.parse("\"\\\"\\\\\\/\\b\\f\\n\\r\\t\""))
        assertEquals("Aб", MiniJson.parse("\"\\u0041\\u0431\""))
        assertEquals("😀", MiniJson.parse("\"\\ud83d\\ude00\"")) // суррогатная пара
    }

    @Test
    fun `некорректный JSON отклоняется`() {
        for (bad in listOf(
            "", "   ", "{", "}", "[", "]", "{\"a\":}", "{\"a\" 1}", "{a:1}", "{'a':1}", "[1,]", "{\"a\":1,}", "[1 2]",
            "tru", "nul", "01", "1.", ".5", "-", "+1", "1e", "\"abc", "\"a\\x\"", "\"\\u12\"", "\"\\u12zz\"",
            "\"a\nb\"", "{} {}", "[] x", "1 2", "\"a\" \"b\""
        )) fails(bad)
    }

    @Test
    fun `глубокая вложенность и огромный вход отклоняются`() {
        fails("[".repeat(MiniJson.MAX_DEPTH + 5) + "]".repeat(MiniJson.MAX_DEPTH + 5))
        MiniJson.parse("[".repeat(MiniJson.MAX_DEPTH) + "]".repeat(MiniJson.MAX_DEPTH)) // на границе — можно
        fails("\"" + "a".repeat(MiniJson.MAX_LENGTH) + "\"")
    }

    @Test
    fun `stringify и parse взаимно обратны, в том числе для спецсимволов`() {
        val original = linkedMapOf<String, Any?>(
            "text" to "кавычки \" и слеш \\ и перенос\nи таб\t и юникод ё 😀 и управляющий \u0001",
            "n" to 5, "big" to 9_000_000_000L, "d" to 2.5, "t" to true, "f" to false, "nil" to null,
            "list" to listOf(1, "two", listOf(3), linkedMapOf("k" to "v")),
            "empty" to emptyList<Any?>(), "emptyObj" to emptyMap<String, Any?>()
        )
        val text = MiniJson.stringify(original)
        val back = MiniJson.parse(text) as Map<*, *>
        assertEquals(original["text"], back["text"])
        assertEquals(5L, back["n"])
        assertEquals(9_000_000_000L, back["big"])
        assertEquals(2.5, back["d"])
        assertEquals(true, back["t"])
        assertNull(back["nil"])
        assertTrue(back.containsKey("nil"))
        assertEquals(listOf(1L, "two", listOf(3L), mapOf("k" to "v")), back["list"])
        assertEquals(text, MiniJson.stringify(back)) // второй круг даёт тот же текст
    }

    @Test
    fun `stringify экранирует управляющие символы`() {
        assertEquals("\"a\\u0001b\"", MiniJson.stringify("a\u0001b"))
        assertEquals("\"\\n\\r\\t\\b\\f\"", MiniJson.stringify("\n\r\t\b\u000C"))
        assertEquals("\"\\\"\\\\\"", MiniJson.stringify("\"\\"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `stringify отклоняет неподдерживаемые типы`() {
        MiniJson.stringify(Any())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `stringify отклоняет NaN`() {
        MiniJson.stringify(Double.NaN)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `stringify отклоняет нестроковые ключи`() {
        MiniJson.stringify(mapOf(1 to "x"))
    }
}
