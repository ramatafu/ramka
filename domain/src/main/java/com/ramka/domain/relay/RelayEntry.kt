package com.ramka.domain.relay

/**
 * Один релей из пула (Этап 3, пул релеёв).
 *
 * @property id стабильный идентификатор записи (для редактирования, удаления и перестановки в UI;
 *   не меняется при смене адреса). Не передаётся на сервер и не попадает в экспорт.
 * @property address адрес `host:port`; в пуле адреса не повторяются.
 * @property token токен доступа к этому релею (RELAY_PROTOCOL.md §3). Секрет: [toString] его скрывает.
 *   Пустой токен допустим только у неполной записи (например, после импорта списка без токенов): такая
 *   запись хранится, но не используется ([issue] != null).
 * @property pinSha256 пин сертификата (hex, нижний регистр) или null.
 * @property label необязательное имя для списка в настройках (до [MAX_LABEL_LENGTH] символов).
 */
data class RelayEntry(
    val id: String,
    val address: RelayAddress,
    val token: String,
    val pinSha256: String?,
    val label: String = ""
) {
    init {
        require(id.isNotBlank()) { "Пустой id релея" }
    }

    /** Что мешает использовать запись; null — запись полная и корректная. */
    val issue: RelayConfigIssue?
        get() = relayCredentialsIssue(address, token, pinSha256)

    /** Готовый к подключению релей или null, если запись неполная. */
    val endpointOrNull: RelayEndpoint?
        get() = if (issue == null) RelayEndpoint(address, token, pinSha256) else null

    /** Что показывать в списке: имя, а если его нет — адрес. */
    val displayName: String
        get() = label.ifBlank { address.format() }

    override fun toString(): String =
        "RelayEntry($id, ${address.format()}, token=***, pin=${if (pinSha256 != null) "set" else "none"})"

    companion object {
        const val MAX_LABEL_LENGTH = 40
    }
}

/**
 * Общая проверка реквизитов релея (одинакова для [RelayEntry] и переходного [RelayConfig]):
 * токен 16..64 байта UTF-8 (§2), для IP-литерала обязателен пин (§1).
 */
internal fun relayCredentialsIssue(address: RelayAddress, token: String, pinSha256: String?): RelayConfigIssue? {
    if (token.isEmpty()) return RelayConfigIssue.NO_TOKEN
    if (token.toByteArray(Charsets.UTF_8).size !in RelayConfig.TOKEN_MIN_BYTES..RelayConfig.TOKEN_MAX_BYTES) {
        return RelayConfigIssue.BAD_TOKEN_LENGTH
    }
    if (address.isIpLiteral && pinSha256 == null) return RelayConfigIssue.PIN_REQUIRED_FOR_IP
    return null
}
