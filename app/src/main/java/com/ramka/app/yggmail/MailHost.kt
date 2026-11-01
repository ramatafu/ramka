package com.ramka.app.yggmail

/**
 * Хосты почтовых серверов. JavaMail на Android плохо переносит «голые» IPv6-литералы (двоеточия путаются с
 * разделителем порта), поэтому в библиотеку IPv6-адрес всегда отдаётся в квадратных скобках: `[200:db8::1]`.
 */
object MailHost {
    private const val HEX_AND_SEPARATORS = "0123456789abcdefABCDEF:"
    private val HOSTNAME = Regex("^(?=.{1,253}$)[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$")

    /** Литерал IPv6 без скобок и без `%зоны`: только hex и двоеточия, минимум два двоеточия. */
    fun isIpv6Literal(host: String): Boolean {
        val h = host.trim()
        return h.count { it == ':' } >= 2 && h.all { it in HEX_AND_SEPARATORS }
    }

    /** Убирает обрамляющие скобки: `[200::1]` → `200::1` (для проверок и не-JavaMail кода). */
    fun stripBrackets(host: String): String {
        val h = host.trim()
        return if (h.length > 2 && h.startsWith("[") && h.endsWith("]")) h.substring(1, h.length - 1) else h
    }

    /**
     * Адрес сервера в виде, который понимает JavaMail: IPv6-литерал оборачивается в квадратные скобки, если их
     * ещё нет; доменные имена, IPv4 и уже обёрнутые IPv6 возвращаются как есть (после trim).
     */
    fun formatIPv6ForJavaMail(host: String): String {
        val h = host.trim()
        if (h.length > 2 && h.startsWith("[") && h.endsWith("]")) return h
        return if (isIpv6Literal(h)) "[$h]" else h
    }

    /** Доменное имя, IPv4 или IPv6 (со скобками или без). */
    fun isValid(host: String): Boolean {
        val h = stripBrackets(host)
        if (h.isEmpty() || h.any { it.isWhitespace() }) return false
        return isIpv6Literal(h) || HOSTNAME.matches(h)
    }
}

/** Шифрование соединения «в зависимости от порта»: 465/993 — SSL сразу, 587/143 — STARTTLS, остальные — без TLS. */
enum class MailSecurity { SSL, STARTTLS, NONE }

object MailSecurityPolicy {
    fun forSmtp(port: Int): MailSecurity = when (port) {
        465 -> MailSecurity.SSL
        587 -> MailSecurity.STARTTLS
        else -> MailSecurity.NONE // например 1025 у Yggmail: трафик и так идёт внутри сети Yggdrasil
    }

    fun forImap(port: Int): MailSecurity = when (port) {
        993 -> MailSecurity.SSL
        143 -> MailSecurity.STARTTLS
        else -> MailSecurity.NONE // например 1143 у Yggmail
    }
}
