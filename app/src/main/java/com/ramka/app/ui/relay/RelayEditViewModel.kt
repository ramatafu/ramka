package com.ramka.app.ui.relay

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramka.app.relay.RelayCheckUi
import com.ramka.app.relay.RelayEditorController
import com.ramka.app.relay.RelayEditorState
import com.ramka.app.relay.RelayPoolController
import com.ramka.domain.relay.RelayPool
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Почему сохранение валидного черновика не состоялось. */
enum class RelaySaveError { DUPLICATE_ADDRESS, LIMIT_REACHED }

/**
 * Редактор одной записи пула. Аргумент навигации `entryId`: id записи для редактирования; пусто,
 * `null` или [NEW_ENTRY] — новая запись. Неизвестный id тоже открывает новую запись.
 */
@HiltViewModel
class RelayEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val controller: RelayEditorController,
    private val poolController: RelayPoolController
) : ViewModel() {

    /** Режим редактирования существующей записи (иначе — добавление новой). */
    val isEditing: Boolean

    val state: StateFlow<RelayEditorState> = controller.state

    private val _saveError = MutableStateFlow<RelaySaveError?>(null)
    val saveError: StateFlow<RelaySaveError?> = _saveError

    init {
        val entryId = savedStateHandle.get<String>(ARG_ENTRY_ID)?.takeIf { it.isNotBlank() && it != NEW_ENTRY }
        val entry = entryId?.let { id -> poolController.current().entries.firstOrNull { it.id == id } }
        isEditing = entry != null
        if (entry != null) controller.openEdit(entry) else controller.openNew()
    }

    fun onLabelChanged(value: String) = change { controller.onLabelChanged(value) }
    fun onAddressChanged(value: String) = change { controller.onAddressChanged(value) }
    fun onTokenChanged(value: String) = change { controller.onTokenChanged(value) }
    fun onPinChanged(value: String) = change { controller.onPinChanged(value) }

    /** @return true если запись сохранена в пул (экран тогда закрывается). */
    fun save(): Boolean {
        val st = state.value
        if (!st.canSave) return false
        val pool = poolController.current()
        val exists = pool.entries.any { it.id == st.editingId }
        if (!exists && pool.entries.size >= RelayPool.MAX_ENTRIES) {
            _saveError.value = RelaySaveError.LIMIT_REACHED
            return false
        }
        if (!controller.save()) {
            _saveError.value = RelaySaveError.DUPLICATE_ADDRESS
            return false
        }
        _saveError.value = null
        return true
    }

    /** Удаление доступно только при редактировании существующей записи. */
    fun delete(): Boolean = isEditing && controller.delete()

    fun check() {
        if (state.value.check == RelayCheckUi.Checking) return
        viewModelScope.launch { runCheck() }
    }

    /** Сама проверка подключения; вынесена, чтобы тесты не зависели от диспетчера viewModelScope. */
    internal suspend fun runCheck(): RelayCheckUi = controller.check()

    private inline fun change(block: () -> Unit) {
        _saveError.value = null
        block()
    }

    companion object {
        const val ARG_ENTRY_ID = "entryId"

        /** Значение аргумента для маршрута добавления: в путь нельзя подставить null. */
        const val NEW_ENTRY = "new"
    }
}
