package com.ramka.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramka.app.data.ProxyDataStore
import com.ramka.network.proxy.ProxySettings
import com.ramka.network.proxy.ProxyType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Черновик формы прокси. Пароль живёт только в памяти формы; на диск (в Keystore) уходит по «Сохранить».
 * [toString] ничего секретного не печатает.
 */
data class ProxyFormState(
    val loaded: Boolean = false,
    val enabled: Boolean = false,
    val type: ProxyType = ProxyType.SOCKS5,
    val host: String = "",
    val portText: String = ProxySettings.DEFAULT_PORT.toString(),
    val username: String = "",
    val password: String = "",
    val error: String? = null,
    val dirty: Boolean = false,
    val saved: Boolean = false
) {
    override fun toString(): String = "ProxyFormState(loaded=$loaded, enabled=$enabled, type=$type, dirty=$dirty)"
}

@HiltViewModel
class ProxySettingsViewModel @Inject constructor(
    private val proxyDataStore: ProxyDataStore
) : ViewModel() {

    private val _form = MutableStateFlow(ProxyFormState())
    val form: StateFlow<ProxyFormState> = _form.asStateFlow()

    init {
        viewModelScope.launch {
            val s = proxyDataStore.settings.first()
            _form.value = ProxyFormState(
                loaded = true,
                enabled = s.enabled,
                type = s.type,
                host = s.host,
                portText = s.port.toString(),
                username = s.username,
                password = s.password
            )
        }
    }

    fun setType(type: ProxyType) = edit { it.copy(type = type) }
    fun setHost(host: String) = edit { it.copy(host = host) }
    fun setPort(text: String) = edit { it.copy(portText = text.filter(Char::isDigit).take(5)) }
    fun setUsername(username: String) = edit { it.copy(username = username) }
    fun setPassword(password: String) = edit { it.copy(password = password) }

    /**
     * Включение только меняет черновик (применится по «Сохранить», когда поля проверены).
     * Выключение применяется сразу: пользователь не должен ждать, чтобы перестать пользоваться прокси.
     */
    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            edit { it.copy(enabled = true) }
        } else {
            _form.update { it.copy(enabled = false, error = null) }
            persist(requireValid = false)
        }
    }

    fun save() = persist(requireValid = true)

    private fun edit(block: (ProxyFormState) -> ProxyFormState) {
        _form.update { block(it).copy(dirty = true, saved = false, error = null) }
    }

    private fun persist(requireValid: Boolean) {
        val current = _form.value
        val port = current.portText.toIntOrNull()
        if (requireValid && current.enabled && port == null) {
            _form.update { it.copy(error = "Порт должен быть числом от 1 до 65535") }
            return
        }
        val settings = ProxySettings(
            enabled = current.enabled,
            type = current.type,
            host = current.host.trim(),
            port = port ?: 0,
            username = current.username,
            password = current.password
        )
        if (requireValid && settings.enabled) {
            settings.validate()?.let { message ->
                _form.update { it.copy(error = message) }
                return
            }
        }
        viewModelScope.launch {
            try {
                proxyDataStore.save(settings)
                _form.update { it.copy(dirty = false, saved = true, error = null) }
            } catch (e: IllegalArgumentException) {
                _form.update { it.copy(error = e.message) }
            } catch (e: Exception) {
                // Текст исключения не показываем и не логируем: в нём могли оказаться данные прокси.
                _form.update { it.copy(error = "Не удалось сохранить настройки (защищённое хранилище недоступно)") }
            }
        }
    }
}
