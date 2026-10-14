package com.ramka.app.data

/**
 * Один TURN-сервер из списка пользователя. Пароль сюда НЕ входит: он лежит в SecureKeyStore под
 * ключом `turn_password_<id>` (см. [TurnSettingsDataStore]).
 */
data class TurnServerEntry(
    val id: String,
    /** Нормализованный адрес: `turn:host:port` или `turns:host:port`. */
    val url: String,
    val username: String,
    val isEnabled: Boolean = true,
    /** Позиция в списке (0 — самый приоритетный). Хранилище держит порядок плотным: 0..n-1. */
    val order: Int
)

/** Разобранный адрес `turn:host:port` / `turns:host:port` (TLS). */
data class TurnUrl(val secure: Boolean, val host: String, val port: Int) {
    val normalized: String get() = "${if (secure) "turns" else "turn"}:$host:$port"

    /**
     * Адреса для ICE. Для `turn:` — UDP (основной) и `?transport=tcp` (резервный, для операторов,
     * режущих UDP). `turns:` уже работает поверх TCP/TLS, второй адрес был бы дублем.
     */
    fun iceUrls(): List<String> =
        if (secure) listOf(normalized) else listOf(normalized, "$normalized?transport=tcp")

    companion object {
        private val PATTERN = Regex(
            "^(turns?):([A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?):(\\d{1,5})$",
            RegexOption.IGNORE_CASE
        )

        /** null — формат неверный. Пробелы по краям игнорируются; параметры (`?transport=`) не допускаются. */
        fun parse(raw: String): TurnUrl? {
            val match = PATTERN.matchEntire(raw.trim()) ?: return null
            val port = match.groupValues[3].toIntOrNull() ?: return null
            if (port !in 1..65535) return null
            return TurnUrl(
                secure = match.groupValues[1].equals("turns", ignoreCase = true),
                host = match.groupValues[2].lowercase(),
                port = port
            )
        }
    }
}

/** Всё, что нужно звонку для одного сервера. Пароль никогда не попадает в строку (случайный лог). */
data class TurnCredentials(
    val iceUrls: List<String>,
    val username: String,
    val password: String
) {
    override fun toString(): String =
        "TurnCredentials(iceUrls=$iceUrls, username=$username, password=***)"
}
