package com.ramka.app.ui.turn

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramka.app.data.TurnServerEntry
import com.ramka.app.data.TurnSettingsDataStore
import com.ramka.app.data.TurnUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TurnServerEditState(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val saving: Boolean = false,
    val urlError: String? = null,
    val usernameError: String? = null,
    val passwordError: String? = null,
    val message: String? = null,
    /** true — запись сохранена (или её не нашлось): экран закрывается. */
    val finished: Boolean = false
)

/**
 * Добавление и редактирование одного TURN-сервера. Аргумент навигации `id`: есть — редактирование,
 * нет — добавление. Если записи с таким id уже нет, экран просто закрывается.
 */
@HiltViewModel
class TurnServerEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val store: TurnSettingsDataStore
) : ViewModel() {

    private val serverId: String? = savedStateHandle.get<String>(ARG_ID)?.takeIf { it.isNotBlank() }

    private val _state = MutableStateFlow(TurnServerEditState(isEditing = serverId != null, isLoading = serverId != null))
    val state: StateFlow<TurnServerEditState> = _state.asStateFlow()

    private var loadedEntry: TurnServerEntry? = null

    init {
        serverId?.let(::loadIfExists)
    }

    fun loadIfExists(id: String) {
        viewModelScope.launch {
            val entry = try {
                store.serversFlow.first().firstOrNull { it.id == id }
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Log.w(TAG, "Не удалось прочитать TURN-сервер: ${e.javaClass.simpleName}")
                null
            }
            if (entry == null) {
                _state.update { it.copy(isLoading = false, finished = true) }
            } else {
                loadedEntry = entry
                // ИСПРАВЛЕНИЕ: загружаем пароль через публичный метод getPassword
                val password = try {
                    store.getPassword(entry.id) ?: ""
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    Log.w(TAG, "Не удалось прочитать пароль TURN-сервера: ${e.javaClass.simpleName}")
                    ""
                }
                _state.update { it.copy(url = entry.url, username = entry.username, password = password, isLoading = false) }
            }
        }
    }

    fun onUrlChanged(value: String) = _state.update { it.copy(url = value, urlError = null) }
    fun onUsernameChanged(value: String) = _state.update { it.copy(username = value, usernameError = null) }
    fun onPasswordChanged(value: String) = _state.update { it.copy(password = value, passwordError = null) }
    fun onMessageShown() = _state.update { it.copy(message = null) }

    fun save() {
        val s = _state.value
        if (s.isLoading || s.saving) return
        val parsed = TurnUrl.parse(s.url)
        val urlError = if (parsed == null) URL_FORMAT_ERROR else null
        val usernameError = if (s.username.isBlank()) "Укажите логин" else null
        val passwordError = if (!s.isEditing && s.password.isBlank()) "Укажите пароль" else null
        if (parsed == null || usernameError != null || passwordError != null) {
            _state.update {
                it.copy(urlError = urlError, usernameError = usernameError, passwordError = passwordError)
            }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val existing = loadedEntry
                if (existing == null) {
                    store.addServer(parsed.normalized, s.username, s.password)
                } else {
                    store.updateServer(
                        existing.copy(url = parsed.normalized, username = s.username.trim()),
                        password = s.password.takeIf { it.isNotBlank() }
                    )
                }
                _state.update { it.copy(saving = false, finished = true) }
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                // Только тип исключения: ни адрес, ни логин, ни пароль в лог не попадают.
                Log.w(TAG, "Не удалось сохранить TURN-сервер: ${e.javaClass.simpleName}")
                _state.update { it.copy(saving = false, message = "Не удалось сохранить сервер") }
            }
        }
    }

    companion object {
        const val ARG_ID = "id"
        const val URL_FORMAT_ERROR = "Формат: turn:хост:порт или turns:хост:порт, например turn:203.0.113.5:3478"
        private const val TAG = "ramka"
    }
}