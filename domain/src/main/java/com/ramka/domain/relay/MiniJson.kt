package com.ramka.domain.relay

/**
 * Минимальный JSON для небольших настроек (пул релеёв, экспорт/импорт): без внешних библиотек, чтобы
 * формат хранения не зависел от версий библиотек и работал в чистых JVM-тестах.
 *
 * Значения: `Map<String, Any?>` (JSON-объект), `List<Any?>`, `String`, `Long`, `Double`, `Boolean`, `null`.
 * Разбор строгий (RFC 8259): лишний текст после значения, висячие запятые, сырые управляющие символы
 * в строках и слишком глубокая вложенность — ошибка [ParseException]. Вход ограничен по размеру,
 * потому что импортируемый текст приходит извне.
 */
internal object MiniJson {

    class ParseException(message: String) : Exception(message)

    const val MAX_DEPTH = 16
    const val MAX_LENGTH = 256 * 1024

    fun parse(text: String): Any? {
        if (text.length > MAX_LENGTH) throw ParseException("Слишком большой JSON")
        val parser = Parser(text)
        parser.skipWhitespace()
        val value = parser.value(0)
        parser.skipWhitespace()
        if (!parser.atEnd()) throw ParseException("Лишний текст после значения (позиция ${parser.position})")
        return value
    }

    fun stringify(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    private fun write(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is String -> writeString(out, value)
            is Boolean -> out.append(if (value) "true" else "false")
            is Int, is Long -> out.append(value.toString())
            is Double -> {
                require(value.isFinite()) { "NaN и бесконечность в JSON недопустимы" }
                out.append(value.toString())
            }
            is Map<*, *> -> {
                out.append('{')
                var first = true
                for ((k, v) in value) {
                    require(k is String) { "Ключ JSON-объекта должен быть строкой" }
                    if (!first) out.append(',')
                    first = false
                    writeString(out, k)
                    out.append(':')
                    write(out, v)
                }
                out.append('}')
            }
            is List<*> -> {
                out.append('[')
                value.forEachIndexed { i, v ->
                    if (i > 0) out.append(',')
                    write(out, v)
                }
                out.append(']')
            }
            else -> throw IllegalArgumentException("Неподдерживаемый тип для JSON: ${value::class.java.name}")
        }
    }

    private fun writeString(out: StringBuilder, s: String) {
        out.append('"')
        for (c in s) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (c < ' ') out.append("\\u%04x".format(c.code)) else out.append(c)
            }
        }
        out.append('"')
    }

    private class Parser(private val s: String) {
        var position = 0
            private set

        fun atEnd() = position >= s.length

        fun skipWhitespace() {
            while (position < s.length && (s[position] == ' ' || s[position] == '\n' || s[position] == '\r' || s[position] == '\t')) position++
        }

        fun value(depth: Int): Any? {
            if (depth > MAX_DEPTH) throw ParseException("Слишком глубокая вложенность")
            if (atEnd()) throw ParseException("Неожиданный конец текста")
            return when (val c = s[position]) {
                '{' -> obj(depth)
                '[' -> array(depth)
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c in '0'..'9') number() else throw ParseException("Неожиданный символ '$c' (позиция $position)")
            }
        }

        private fun literal(word: String, result: Any?): Any? {
            if (!s.startsWith(word, position)) throw ParseException("Ожидалось $word (позиция $position)")
            position += word.length
            return result
        }

        private fun obj(depth: Int): Map<String, Any?> {
            position++ // {
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                position++
                return map
            }
            while (true) {
                skipWhitespace()
                if (peek() != '"') throw ParseException("Ожидался ключ-строка (позиция $position)")
                val key = string()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                map[key] = value(depth + 1)
                skipWhitespace()
                when (peek()) {
                    ',' -> position++
                    '}' -> {
                        position++
                        return map
                    }
                    else -> throw ParseException("Ожидалась ',' или '}' (позиция $position)")
                }
            }
        }

        private fun array(depth: Int): List<Any?> {
            position++ // [
            val list = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                position++
                return list
            }
            while (true) {
                skipWhitespace()
                list.add(value(depth + 1))
                skipWhitespace()
                when (peek()) {
                    ',' -> position++
                    ']' -> {
                        position++
                        return list
                    }
                    else -> throw ParseException("Ожидалась ',' или ']' (позиция $position)")
                }
            }
        }

        private fun string(): String {
            position++ // открывающая кавычка
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw ParseException("Строка не закрыта")
                val c = s[position++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (atEnd()) throw ParseException("Оборванная escape-последовательность")
                        when (val e = s[position++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (position + 4 > s.length) throw ParseException("Оборванная \\u-последовательность")
                                val code = s.substring(position, position + 4).toIntOrNull(16)
                                    ?: throw ParseException("Некорректная \\u-последовательность (позиция $position)")
                                sb.append(code.toChar())
                                position += 4
                            }
                            else -> throw ParseException("Неизвестная escape-последовательность \\$e")
                        }
                    }
                    c < ' ' -> throw ParseException("Сырой управляющий символ в строке (позиция ${position - 1})")
                    else -> sb.append(c)
                }
            }
        }

        private fun number(): Any {
            val start = position
            if (peek() == '-') position++
            if (peek() == '0') {
                position++
            } else if (peek() in '1'..'9') {
                while (peek() in '0'..'9') position++
            } else {
                throw ParseException("Некорректное число (позиция $start)")
            }
            var integral = true
            if (peek() == '.') {
                integral = false
                position++
                if (peek() !in '0'..'9') throw ParseException("Некорректное число (позиция $start)")
                while (peek() in '0'..'9') position++
            }
            if (peek() == 'e' || peek() == 'E') {
                integral = false
                position++
                if (peek() == '+' || peek() == '-') position++
                if (peek() !in '0'..'9') throw ParseException("Некорректное число (позиция $start)")
                while (peek() in '0'..'9') position++
            }
            val text = s.substring(start, position)
            if (integral) text.toLongOrNull()?.let { return it }
            val d = text.toDouble()
            if (!d.isFinite()) throw ParseException("Число вне диапазона (позиция $start)")
            return d
        }

        /** Текущий символ или `\u0000` в конце текста (удобно для проверок `in`). */
        private fun peek(): Char = if (position < s.length) s[position] else '\u0000'

        private fun expect(c: Char) {
            if (peek() != c) throw ParseException("Ожидалось '$c' (позиция $position)")
            position++
        }
    }
}
