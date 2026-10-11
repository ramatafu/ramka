package com.ramka.app.ui.settings

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramka.app.data.TurnSettings
import com.ramka.app.data.TurnSettingsDataStore
import com.ramka.app.data.TurnUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Состояние формы «Настройки TURN-сервера»: читает значения из [TurnSettingsDataStore] и сохраняет их обратно. */
@HiltViewModel
class TurnSettingsViewModel @Inject constructor(
    private val store: TurnSettingsDataStore
) : ViewModel() {

    var loaded by mutableStateOf(false)
        private set
    var serverUrl by mutableStateOf("")
        private set
    var username by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var isEnabled by mutableStateOf(true)
        private set
    var saving by mutableStateOf(false)
        private set

    /** Одноразовое сообщение для Snackbar; экран вызывает [onMessageShown] после показа. */
    var message by mutableStateOf<String?>(null)
        private set

    private var showRequiredErrors by mutableStateOf(false)

    val urlError: String?
        get() = when {
            serverUrl.isBlank() -> if (showRequiredErrors && isEnabled) "Укажите адрес сервера" else null
            TurnUrl.parse(serverUrl) == null -> "Формат адреса: turn:IP:порт, например turn:203.0.113.5:3478"
            else -> null
        }

    val usernameError: String?
        get() = if (showRequiredErrors && isEnabled && username.isBlank()) "Укажите логин" else null

    val passwordError: String?
        get() = if (showRequiredErrors && isEnabled && password.isBlank()) "Укажите пароль" else null

    private val isFormValid: Boolean
        get() = urlError == null && usernameError == null && passwordError == null

    init {
        viewModelScope.launch {
            val current = try {
                store.settings.first()
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Log.w(TAG, "Не удалось прочитать настройки TURN: ${e.javaClass.simpleName}")
                TurnSettings.DEFAULT
            }
            serverUrl = current.serverUrl
            username = current.username
            password = current.password
            isEnabled = current.isEnabled
            loaded = true
        }
    }

    fun onServerUrlChanged(value: String) { serverUrl = value }
    fun onUsernameChanged(value: String) { username = value }
    fun onPasswordChanged(value: String) { password = value }
    fun onEnabledChanged(value: Boolean) { isEnabled = value }
    fun onMessageShown() { message = null }

    /** Очищает форму и выключает TURN; чтобы применить, нужно нажать «Сохранить». */
    fun onClear() {
        serverUrl = ""
        username = ""
        password = ""
        isEnabled = false
        showRequiredErrors = false
    }

    fun onSave() {
        if (!loaded || saving) return
        showRequiredErrors = true
        if (!isFormValid) {
            message = "Проверьте поля формы"
            return
        }
        val normalizedUrl = TurnUrl.parse(serverUrl)?.normalized ?: ""
        val toSave = TurnSettings(
            serverUrl = normalizedUrl,
            username = username.trim(),
            password = password.trim(),
            isEnabled = isEnabled
        )
        saving = true
        viewModelScope.launch {
            try {
                store.saveSettings(toSave)
                serverUrl = toSave.serverUrl
                username = toSave.username
                password = toSave.password
                message = "Сохранено. Настройки применятся при следующем звонке"
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                // Только тип исключения: ни адрес, ни логин, ни пароль в лог не попадают.
                Log.w(TAG, "Не удалось сохранить настройки TURN: ${e.javaClass.simpleName}")
                message = "Не удалось сохранить настройки"
            } finally {
                saving = false
            }
        }
    }

    private companion object {
        const val TAG = "ramka"
    }
}
