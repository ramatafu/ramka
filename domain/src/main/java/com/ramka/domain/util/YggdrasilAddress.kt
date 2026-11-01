package com.ramka.domain.util

/**
 * Разбор и проверка адреса контакта в сети Yggdrasil.
 *
 * Принимаются записи: `user@[200:1234::1]`, `user@200:1234::1`, `[200:1234::1]` и просто `200:1234::1`.
 * Адрес должен быть IPv6-литералом из диапазона Yggdrasil `200::/7` (первая группа от 0200 до 03ff,
 * то есть запись начинается с «2xx:» или «3xx:»). Всё остальное (доменные имена, IPv4, обычный IPv6, адреса
 * с `%зона`) отвергается: поле нельзя использовать, чтобы заставить приложение подключаться к
 * произвольному хосту. Парсер работает без DNS и без java.net — только разбор текста.
 */
object YggdrasilAddress {
    private const val MIN_FIRST_GROUP = 0x0200
    private const val MAX_FIRST_GROUP = 0x03FF

    /** @return IPv6-литерал без скобок в нижнем регистре или null, если запись не является адресом Yggdrasil. */
    fun parse(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        var host = trimmed
        val at = trimmed.lastIndexOf('@')
        if (at >= 0) {
            val local = trimmed.substring(0, at)
            if (local.isEmpty() || local.any { it.isWhitespace() || it == '[' || it == ']' || it == '@' }) return null
            host = trimmed.substring(at + 1)
        }
        if (host.startsWith("[") || host.endsWith("]")) {
            if (!(host.startsWith("[") && host.endsWith("]")) || host.length < 3) return null
            host = host.substring(1, host.length - 1)
        }
        host = host.lowercase()

        val groups = parseIpv6(host) ?: return null
        if (groups[0] !in MIN_FIRST_GROUP..MAX_FIRST_GROUP) return null
        return host
    }

    fun isValid(input: String): Boolean = parse(input) != null

    /** 8 групп по 16 бит или null. Поддерживается одно сжатие `::`; IPv4-вставки и зоны не поддерживаются. */
    private fun parseIpv6(text: String): IntArray? {
        if (text.isEmpty() || text.any { !(it in '0'..'9' || it in 'a'..'f' || it == ':') }) return null
        val doubleColon = text.indexOf("::")
        if (doubleColon >= 0 && text.indexOf("::", doubleColon + 1) >= 0) return null // `:::` и второе сжатие

        fun groupsOf(part: String): List<Int>? {
            if (part.isEmpty()) return emptyList()
            val result = ArrayList<Int>()
            for (chunk in part.split(':')) {
                if (chunk.isEmpty() || chunk.length > 4) return null
                result += chunk.toInt(16)
            }
            return result
        }

        val result = IntArray(8)
        if (doubleColon < 0) {
            val all = groupsOf(text) ?: return null
            if (all.size != 8) return null
            all.forEachIndexed { index, value -> result[index] = value }
        } else {
            val head = groupsOf(text.substring(0, doubleColon)) ?: return null
            val tail = groupsOf(text.substring(doubleColon + 2)) ?: return null
            if (head.size + tail.size > 7) return null
            head.forEachIndexed { index, value -> result[index] = value }
            tail.forEachIndexed { index, value -> result[8 - tail.size + index] = value }
        }
        return result
    }
}
