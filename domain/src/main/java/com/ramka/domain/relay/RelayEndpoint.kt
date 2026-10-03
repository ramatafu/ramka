package com.ramka.domain.relay

/**
 * Релей, к которому можно подключиться: адрес, токен доступа и (необязательно) пин сертификата.
 *
 * @property token токен доступа к релею (RELAY_PROTOCOL.md §3, 16..64 байта в UTF-8). Секрет:
 *   не логируется, [toString] его скрывает.
 * @property pinSha256 пин сертификата — hex в нижнем регистре, SHA-256 от DER SubjectPublicKeyInfo
 *   (§1). Если задан, проверяется только он; если нет — системные CA и имя хоста (только для домена).
 */
data class RelayEndpoint(
    val address: RelayAddress,
    val token: String,
    val pinSha256: String?
) {
    override fun toString(): String =
        "RelayEndpoint(${address.format()}, token=***, pin=${if (pinSha256 != null) "set" else "none"})"
}

/** Разбор пина сертификата, введённого пользователем. */
object RelayPin {
    const val HEX_LENGTH = 64

    /**
     * Приводит ввод к каноническому виду: убирает пробелы и двоеточия (формат `AA:BB:...` из openssl),
     * переводит в нижний регистр. Возвращает null, если это не 64 hex-символа.
     * Пустой ввод («пина нет») вызывающий обрабатывает сам до вызова.
     */
    fun normalize(input: String): String? {
        val s = input.filter { !it.isWhitespace() && it != ':' }.lowercase()
        if (s.length != HEX_LENGTH) return null
        return if (s.all { it in '0'..'9' || it in 'a'..'f' }) s else null
    }
}
