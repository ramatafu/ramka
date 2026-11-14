package com.ramka.network.proxy

/** Тип прокси: SOCKS5 или HTTP (для HTTP-прокси трафик идёт через CONNECT-туннель). */
enum class ProxyType { SOCKS5, HTTP }

/**
 * Настройки прокси. Пароль — секрет: в `toString()` нет ни пароля, ни логина, ни адреса (они попадают в логи и
 * отчёты о сбоях). Хранятся в `ProxyDataStore` (пароль — в Android Keystore), здесь только модель и проверка.
 */
data class ProxySettings(
    val enabled: Boolean = false,
    val type: ProxyType = ProxyType.SOCKS5,
    val host: String = "",
    val port: Int = DEFAULT_PORT,
    val username: String = "",
    val password: String = ""
) {
    /** Заданы ли логин и пароль (оба поля необязательны; пароль без логина не допускается). */
    val hasCredentials: Boolean get() = username.isNotEmpty()

    /** @return понятный текст ошибки для UI или null, если настройки пригодны для подключения. */
    fun validate(): String? = when {
        !isValidHost(host) -> "Укажите адрес прокси: IP или доменное имя"
        port !in 1..MAX_PORT -> "Порт должен быть от 1 до 65535"
        username.isEmpty() && password.isNotEmpty() -> "Пароль указан без логина"
        username.toByteArray().size > MAX_CREDENTIAL_BYTES || password.toByteArray().size > MAX_CREDENTIAL_BYTES ->
            "Логин и пароль не длиннее 255 байт"
        else -> null
    }

    /** Включён и настроен верно: только такой прокси можно применять. */
    val isUsable: Boolean get() = enabled && validate() == null

    override fun toString(): String = "ProxySettings(enabled=$enabled, type=$type, host=<hidden>, port=$port, credentials=${if (hasCredentials) "<hidden>" else "none"})"

    companion object {
        const val DEFAULT_PORT = 1080
        private const val MAX_PORT = 65535

        /** Лимит SOCKS5 (RFC 1929): логин и пароль по 255 байт. */
        private const val MAX_CREDENTIAL_BYTES = 255

        val DISABLED = ProxySettings()

        private val HOSTNAME = Regex("^(?=.{1,253}$)[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$")

        /** Доменное имя, IPv4 или IPv6-литерал (со скобками или без); схемы, пути и пробелы отвергаются. */
        fun isValidHost(host: String): Boolean {
            val h = stripBrackets(host.trim())
            if (h.isEmpty() || h.any { it.isWhitespace() }) return false
            val ipv6 = h.count { it == ':' } >= 2 && h.all { it in "0123456789abcdefABCDEF:" }
            return ipv6 || HOSTNAME.matches(h)
        }

        /** `[::1]` → `::1`; всё остальное без изменений. */
        fun stripBrackets(host: String): String =
            if (host.length > 2 && host.startsWith("[") && host.endsWith("]")) host.substring(1, host.length - 1) else host
    }
}

/** Источник настроек прокси (в приложении это `ProxyDataStore`); так сетевой модуль не зависит от приложения. */
interface ProxySettingsSource {
    val settings: kotlinx.coroutines.flow.Flow<ProxySettings>
}
