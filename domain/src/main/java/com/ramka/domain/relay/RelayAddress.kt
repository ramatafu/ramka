package com.ramka.domain.relay

/**
 * Адрес релея: хост и порт (RELAY_PROTOCOL.md §1). Хост — доменное имя, IPv4 или IPv6-литерал
 * (хранится БЕЗ квадратных скобок, в нижнем регистре для доменов).
 *
 * Адрес хранится как текст и резолвится только в момент подключения (в network-модуле, через
 * `InetSocketAddress(host, port)` на IO-потоке, без кэша), поэтому смена IP за доменом
 * подхватывается сама. Создавайте адрес только через [parse]: он проверяет ввод пользователя.
 */
data class RelayAddress(val host: String, val port: Int) {

    init {
        require(host.isNotEmpty()) { "Пустой хост" }
        require(port in 1..65535) { "Порт вне диапазона: $port" }
    }

    /** IP-литерал (IPv4 или IPv6). Для него нет имени хоста для проверки сертификата — нужен пин (§1). */
    val isIpLiteral: Boolean
        get() = RelayAddressParser.isIpv4(host) || host.contains(':')

    /** `host:port`; IPv6 — в квадратных скобках (тот же вид понимает [parse]). */
    fun format(): String = if (host.contains(':')) "[$host]:$port" else "$host:$port"

    override fun toString(): String = format()

    companion object {
        /** Порт релея по умолчанию (одобрен в плане Этапа 3). */
        const val DEFAULT_PORT = 48766

        /**
         * Разбирает ввод пользователя: `host`, `host:port`, `1.2.3.4:port`, `[::1]:port`.
         * Без порта берётся [DEFAULT_PORT]. Схема (`https://`), путь и пробелы не допускаются.
         */
        fun parse(input: String): RelayAddressParseResult = RelayAddressParser.parse(input)

        fun parseOrNull(input: String): RelayAddress? =
            (parse(input) as? RelayAddressParseResult.Success)?.address
    }
}

sealed interface RelayAddressParseResult {
    data class Success(val address: RelayAddress) : RelayAddressParseResult
    data class Failure(val error: RelayAddressError) : RelayAddressParseResult
}

enum class RelayAddressError {
    /** Пустая строка. */
    EMPTY,

    /** Пробелы, схема (`https://`), путь, `@`, `?`, `#`. */
    FORBIDDEN_CHARACTERS,

    /** Не доменное имя, не IPv4 и не IPv6. */
    BAD_HOST,

    /** Порт пустой, не число или вне 1..65535. */
    BAD_PORT,

    /** IPv6 без квадратных скобок: `::1:48766` неоднозначно. */
    IPV6_NEEDS_BRACKETS
}

internal object RelayAddressParser {

    fun parse(input: String): RelayAddressParseResult {
        val s = input.trim()
        if (s.isEmpty()) return fail(RelayAddressError.EMPTY)
        if (s.any { it.isWhitespace() || it in "/\\@?#" }) return fail(RelayAddressError.FORBIDDEN_CHARACTERS)

        val host: String
        val portText: String?
        if (s.startsWith("[")) {
            val end = s.indexOf(']')
            if (end < 0) return fail(RelayAddressError.BAD_HOST)
            host = s.substring(1, end)
            val rest = s.substring(end + 1)
            portText = when {
                rest.isEmpty() -> null
                rest.startsWith(":") -> rest.substring(1)
                else -> return fail(RelayAddressError.BAD_HOST)
            }
            if (!isIpv6(host)) return fail(RelayAddressError.BAD_HOST)
        } else {
            when (s.count { it == ':' }) {
                0 -> {
                    host = s
                    portText = null
                }
                1 -> {
                    val i = s.indexOf(':')
                    host = s.substring(0, i)
                    portText = s.substring(i + 1)
                }
                else -> return fail(RelayAddressError.IPV6_NEEDS_BRACKETS)
            }
            if (host.isEmpty()) return fail(RelayAddressError.BAD_HOST)
        }

        val port = if (portText == null) RelayAddress.DEFAULT_PORT else (parsePort(portText) ?: return fail(RelayAddressError.BAD_PORT))

        val normalizedHost = when {
            host.contains(':') -> host.lowercase() // IPv6 уже проверен выше
            looksNumeric(host) -> if (isIpv4(host)) host else return fail(RelayAddressError.BAD_HOST)
            else -> normalizeDomain(host) ?: return fail(RelayAddressError.BAD_HOST)
        }
        return RelayAddressParseResult.Success(RelayAddress(normalizedHost, port))
    }

    private fun fail(error: RelayAddressError) = RelayAddressParseResult.Failure(error)

    private fun parsePort(text: String): Int? {
        if (text.isEmpty() || text.length > 5 || text.any { it !in '0'..'9' }) return null
        val value = text.toInt()
        return if (value in 1..65535) value else null
    }

    /** Только цифры и точки: такое значение обязано быть корректным IPv4, а не «доменом». */
    private fun looksNumeric(s: String): Boolean = s.all { it in '0'..'9' || it == '.' }

    /** Домен: метки 1..63 символа [a-z0-9-], не начинаются и не заканчиваются дефисом, всего ≤ 253. */
    private fun normalizeDomain(raw: String): String? {
        val host = raw.removeSuffix(".").lowercase()
        if (host.isEmpty() || host.length > 253) return null
        for (label in host.split('.')) {
            if (label.isEmpty() || label.length > 63) return null
            if (label.first() == '-' || label.last() == '-') return null
            if (label.any { it !in 'a'..'z' && it !in '0'..'9' && it != '-' }) return null
        }
        return host
    }

    /** Четыре десятичных октета 0..255 без ведущих нулей (чтобы не гадать, не восьмеричные ли они). */
    fun isIpv4(s: String): Boolean {
        val parts = s.split('.')
        if (parts.size != 4) return false
        return parts.all { p ->
            p.isNotEmpty() && p.length <= 3 && p.all { it in '0'..'9' } &&
                (p.length == 1 || p[0] != '0') && p.toInt() <= 255
        }
    }

    /** IPv6-литерал без зоны: 8 групп по 1..4 hex-цифр, допускается одно `::` и IPv4 в конце. */
    fun isIpv6(s: String): Boolean {
        if (!s.contains(':')) return false
        if (s.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' && it != ':' && it != '.' }) return false
        if (s.contains(":::")) return false
        val first = s.indexOf("::")
        if (first != s.lastIndexOf("::")) return false

        // Число 16-битных групп в части адреса или null, если часть некорректна.
        fun groups(part: String, allowIpv4Tail: Boolean): Int? {
            if (part.isEmpty()) return 0
            val items = part.split(':')
            var count = 0
            for ((i, item) in items.withIndex()) {
                if (item.isEmpty()) return null
                if (item.contains('.')) {
                    if (!allowIpv4Tail || i != items.lastIndex || !isIpv4(item)) return null
                    count += 2
                } else {
                    if (item.length > 4) return null
                    count += 1
                }
            }
            return count
        }

        return if (first >= 0) {
            val head = groups(s.substring(0, first), false) ?: return false
            val tail = groups(s.substring(first + 2), true) ?: return false
            head + tail <= 7
        } else {
            groups(s, true) == 8
        }
    }
}
