package com.ramka.domain.relay

import kotlinx.coroutines.flow.StateFlow

/**
 * Настройки ОДНОГО домашнего релея (Этап 3). Релей общий для всех контактов и задаётся в настройках
 * приложения; в QR-приглашение он не попадает.
 *
 * Переходный тип: на шагах 1–2 пула релеёв на него опирается старый экран, а с шага 3 его заменяют
 * [RelayPool] и [RelayEntry].
 */
data class RelayConfig(
    val enabled: Boolean = false,
    val address: RelayAddress? = null,
    val token: String = "",
    val pinSha256: String? = null
) {
    /** Что мешает использовать эти настройки; null — настройки полные и корректные. */
    val issue: RelayConfigIssue?
        get() {
            val a = address ?: return RelayConfigIssue.NO_ADDRESS
            return relayCredentialsIssue(a, token, pinSha256)
        }

    /** Релей по этим настройкам независимо от переключателя (для проверки подключения). */
    val endpointOrNull: RelayEndpoint?
        get() {
            if (issue != null) return null
            val a = address ?: return null
            return RelayEndpoint(a, token, pinSha256)
        }

    /** Релей, которым приложение реально пользуется: включён и настроен. */
    val activeEndpoint: RelayEndpoint?
        get() = if (enabled) endpointOrNull else null

    override fun toString(): String =
        "RelayConfig(enabled=$enabled, address=$address, token=***, pin=${if (pinSha256 != null) "set" else "none"})"

    companion object {
        const val TOKEN_MIN_BYTES = 16 // RELAY_PROTOCOL.md §2
        const val TOKEN_MAX_BYTES = 64
    }
}

enum class RelayConfigIssue { NO_ADDRESS, NO_TOKEN, BAD_TOKEN_LENGTH, PIN_REQUIRED_FOR_IP }

/** Источник текущих настроек релея. Реализация в app: SharedPreferences + SecureKeyStore. */
interface RelayConfigSource {
    val config: StateFlow<RelayConfig>
}

/** Ввод пользователя с экрана настроек (как есть, до проверки). Пин пустой — «пина нет». */
data class RelayConfigDraft(val address: String, val token: String, val pin: String)

enum class RelayPinError { BAD_FORMAT, REQUIRED_FOR_IP }

/** Ошибки по полям; null — поле в порядке. */
data class RelayDraftErrors(
    val address: RelayAddressError?,
    val token: RelayConfigIssue?,
    val pin: RelayPinError?
) {
    val hasErrors: Boolean get() = address != null || token != null || pin != null
}

sealed interface RelayDraftResult {
    data class Valid(val config: RelayConfig) : RelayDraftResult
    data class Invalid(val errors: RelayDraftErrors) : RelayDraftResult
}

object RelayConfigValidator {

    /** Проверяет ввод и собирает [RelayConfig]. Токен обрезается по краям (вставка из буфера). */
    fun validate(draft: RelayConfigDraft, enabled: Boolean): RelayDraftResult {
        val parsed = RelayAddress.parse(draft.address)
        val address = (parsed as? RelayAddressParseResult.Success)?.address
        val addressError = (parsed as? RelayAddressParseResult.Failure)?.error

        val token = draft.token.trim()
        val tokenError = when {
            token.isEmpty() -> RelayConfigIssue.NO_TOKEN
            token.toByteArray(Charsets.UTF_8).size !in RelayConfig.TOKEN_MIN_BYTES..RelayConfig.TOKEN_MAX_BYTES ->
                RelayConfigIssue.BAD_TOKEN_LENGTH
            else -> null
        }

        var pin: String? = null
        var pinError: RelayPinError? = null
        if (draft.pin.isNotBlank()) {
            pin = RelayPin.normalize(draft.pin)
            if (pin == null) pinError = RelayPinError.BAD_FORMAT
        }
        if (pinError == null && pin == null && address != null && address.isIpLiteral) {
            pinError = RelayPinError.REQUIRED_FOR_IP
        }

        val errors = RelayDraftErrors(addressError, tokenError, pinError)
        if (errors.hasErrors || address == null) return RelayDraftResult.Invalid(errors)
        return RelayDraftResult.Valid(RelayConfig(enabled, address, token, pin))
    }
}
