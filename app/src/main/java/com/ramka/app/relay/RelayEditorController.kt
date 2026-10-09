package com.ramka.app.relay

import com.ramka.domain.relay.RelayCheckResult
import com.ramka.domain.relay.RelayChecker
import com.ramka.domain.relay.RelayEndpoint
import com.ramka.domain.relay.RelayEntry
import com.ramka.domain.relay.RelayEntryDraft
import com.ramka.domain.relay.RelayEntryDraftResult
import com.ramka.domain.relay.RelayEntryErrors
import com.ramka.domain.relay.RelayPool
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Форма одного релея: черновик, живые ошибки, грязность, статус проверки. */
data class RelayEditorState(
    val editingId: String? = null,
    val label: String = "",
    val address: String = "",
    val token: String = "",
    val pin: String = "",
    val dirty: Boolean = false,
    val check: RelayCheckUi = RelayCheckUi.Idle
) {
    val draft: RelayEntryDraft get() = RelayEntryDraft(label, address, token, pin)
    val validationResult: RelayEntryDraftResult get() = RelayEntryValidatorBridge.validate(draft, editingId)
    val errors: RelayEntryErrors? get() = (validationResult as? RelayEntryDraftResult.Invalid)?.errors
    val canSave: Boolean get() = dirty && validationResult is RelayEntryDraftResult.Valid
    val canCheck: Boolean get() = validationResult is RelayEntryDraftResult.Valid
}

/** Тонкая прослойка, чтобы тесты могли подменить валидатор при необходимости. */
internal object RelayEntryValidatorBridge {
    fun validate(draft: RelayEntryDraft, id: String?): RelayEntryDraftResult =
        com.ramka.domain.relay.RelayEntryValidator.validate(draft, id ?: "")
}

/**
 * Редактор одной записи пула. Логика валидации и проверки подключения переехала из
 * RelayFormController без изменения поведения: ошибки считаются живо от черновика,
 * проверка ходит в [RelayChecker] только по валидному черновику, любое изменение
 * полей сбрасывает результат проверки.
 */
@Singleton
class RelayEditorController @Inject constructor(
    private val poolController: RelayPoolController,
    private val checker: RelayChecker
) {

    private val _state = MutableStateFlow(RelayEditorState())
    val state: StateFlow<RelayEditorState> = _state

    private var saved = RelayEditorState()

    fun openNew() = commit(RelayEditorState())

    fun openEdit(entry: RelayEntry) = commit(
        RelayEditorState(
            editingId = entry.id,
            label = entry.label,
            address = entry.address.format(),
            token = entry.token,
            pin = entry.pinSha256.orEmpty()
        )
    )

    /** Режим старого экрана: пул как «первая запись». */
    fun openLegacyFirst(pool: RelayPool) {
        val first = pool.entries.firstOrNull()
        if (first == null) openNew() else openEdit(first)
    }

    fun onLabelChanged(v: String) = edit { it.copy(label = v) }
    fun onAddressChanged(v: String) = edit { it.copy(address = v) }
    fun onTokenChanged(v: String) = edit { it.copy(token = v) }
    fun onPinChanged(v: String) = edit { it.copy(pin = v) }

    fun save(): Boolean {
        val st = _state.value
        val valid = st.validationResult as? RelayEntryDraftResult.Valid ?: return false
        val entry = valid.entry
        val ok = if (st.editingId == null) poolController.add(entry) else poolController.replace(entry)
        if (!ok) return false
        commit(st.copy(dirty = false, check = RelayCheckUi.Idle))
        return true
    }

    fun delete(): Boolean {
        val id = _state.value.editingId ?: return false
        if (!poolController.remove(id)) return false
        openNew()
        return true
    }

    suspend fun check(): RelayCheckUi {
        val st = _state.value
        val valid = st.validationResult as? RelayEntryDraftResult.Valid ?: return RelayCheckUi.Idle
        _state.value = st.copy(check = RelayCheckUi.Checking)
        val endpoint = RelayEndpoint(valid.entry.address, valid.entry.token, valid.entry.pinSha256)
        val result = checker.check(endpoint)
        val ui: RelayCheckUi = when (result) {
            is RelayCheckResult.Ok -> RelayCheckUi.Ok
            is RelayCheckResult.Failed -> RelayCheckUi.Failed(result.failure)
        }
        _state.value = _state.value.copy(check = ui)
        return ui
    }

    fun resetCheck() {
        _state.value = _state.value.copy(check = RelayCheckUi.Idle)
    }

    private fun edit(f: (RelayEditorState) -> RelayEditorState) {
        val next = f(_state.value)
        _state.value = next.copy(
            dirty = next.core() != saved.core(),
            check = RelayCheckUi.Idle
        )
    }

    private fun commit(s: RelayEditorState) {
        _state.value = s
        saved = s
    }

    private fun RelayEditorState.core(): List<String?> = listOf(label, address, token, pin)
}