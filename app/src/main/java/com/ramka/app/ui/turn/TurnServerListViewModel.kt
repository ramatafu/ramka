package com.ramka.app.ui.turn

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramka.app.data.TurnServerEntry
import com.ramka.app.data.TurnSettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Список TURN-серверов: показ, включение/выключение, порядок, удаление. */
@HiltViewModel
class TurnServerListViewModel @Inject constructor(
    private val store: TurnSettingsDataStore
) : ViewModel() {

    private val _loaded = MutableStateFlow(false)

    /** false, пока список не прочитан: экран не показывает подсказку «список пуст» раньше времени. */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    val servers: StateFlow<List<TurnServerEntry>> = store.serversFlow
        .catch { e ->
            Log.w(TAG, "Не удалось прочитать список TURN: ${e.javaClass.simpleName}")
            emit(emptyList())
        }
        .onEach { _loaded.value = true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)

    /** Одноразовое сообщение для Snackbar; экран вызывает [onMessageShown] после показа. */
    val message: StateFlow<String?> = _message.asStateFlow()

    fun onMessageShown() {
        _message.value = null
    }

    fun addServer(url: String, username: String, password: String) =
        launchOp("Не удалось добавить сервер") { store.addServer(url, username, password) }

    fun removeServer(id: String) =
        launchOp("Не удалось удалить сервер") { store.removeServer(id) }

    fun moveUp(id: String) = launchOp("Не удалось изменить порядок") { store.moveUp(id) }

    fun moveDown(id: String) = launchOp("Не удалось изменить порядок") { store.moveDown(id) }

    fun toggleEnabled(id: String) {
        val entry = servers.value.firstOrNull { it.id == id } ?: return
        launchOp("Не удалось сохранить настройку") {
            store.updateServer(entry.copy(isEnabled = !entry.isEnabled), password = null)
        }
    }

    private fun launchOp(errorText: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                // Только тип исключения: ни адрес, ни логин, ни пароль в лог не попадают.
                Log.w(TAG, "$errorText: ${e.javaClass.simpleName}")
                _message.value = errorText
            }
        }
    }

    private companion object {
        const val TAG = "ramka"
    }
}
