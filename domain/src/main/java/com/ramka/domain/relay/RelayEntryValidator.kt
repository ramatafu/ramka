package com.ramka.domain.relay

/** Ввод пользователя для одной записи пула (как есть, до проверки). Пустой пин — «пина нет». */
data class RelayEntryDraft(val label: String, val address: String, val token: String, val pin: String)

enum class RelayLabelError {
    /** Длиннее [RelayEntry.MAX_LABEL_LENGTH]. */
    TOO_LONG,

    /** Управляющие символы (переводы строк, табуляция). */
    BAD_CHARACTERS
}

/** Ошибки по полям; null — поле в порядке. */
data class RelayEntryErrors(
    val label: RelayLabelError?,
    val address: RelayAddressError?,
    val token: RelayConfigIssue?,
    val pin: RelayPinError?
) {
    val hasErrors: Boolean get() = label != null || address != null || token != null || pin != null
}

sealed interface RelayEntryDraftResult {
    data class Valid(val entry: RelayEntry) : RelayEntryDraftResult
    data class Invalid(val errors: RelayEntryErrors) : RelayEntryDraftResult
}

object RelayEntryValidator {

    /**
     * Проверяет ввод и собирает [RelayEntry] с данным [id]. Адрес, токен и пин проверяются теми же
     * правилами, что и раньше ([RelayConfigValidator]); имя обрезается по краям.
     * Уникальность адреса в пуле проверяет сам пул ([RelayPool.add], [RelayPool.replace]).
     */
    fun validate(draft: RelayEntryDraft, id: String): RelayEntryDraftResult {
        val label = draft.label.trim()
        val labelError = when {
            label.length > RelayEntry.MAX_LABEL_LENGTH -> RelayLabelError.TOO_LONG
            label.any { it.isISOControl() } -> RelayLabelError.BAD_CHARACTERS
            else -> null
        }

        val base = RelayConfigValidator.validate(RelayConfigDraft(draft.address, draft.token, draft.pin), enabled = false)
        val baseErrors = (base as? RelayDraftResult.Invalid)?.errors
        val errors = RelayEntryErrors(labelError, baseErrors?.address, baseErrors?.token, baseErrors?.pin)
        if (errors.hasErrors) return RelayEntryDraftResult.Invalid(errors)

        val config = (base as RelayDraftResult.Valid).config
        val address = checkNotNull(config.address) { "Валидатор вернул Valid без адреса" }
        return RelayEntryDraftResult.Valid(RelayEntry(id, address, config.token, config.pinSha256, label))
    }
}
