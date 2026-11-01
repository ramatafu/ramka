package com.ramka.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramka.app.datastore.ProxyConfig
import com.ramka.app.datastore.ProxyDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProxySettingsViewModel @Inject constructor(
    private val proxyDataStore: ProxyDataStore
) : ViewModel() {

    val proxyConfig: StateFlow<ProxyConfig> = proxyDataStore.proxyConfigFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ProxyConfig()
        )

    fun setEnabled(isEnabled: Boolean) {
        viewModelScope.launch { proxyDataStore.updateEnabled(isEnabled) }
    }

    fun setType(type: String) {
        viewModelScope.launch { proxyDataStore.updateType(type) }
    }

    fun setHost(host: String) {
        viewModelScope.launch { proxyDataStore.updateHost(host) }
    }

    fun setPort(port: String) {
        viewModelScope.launch {
            val portInt = port.toIntOrNull() ?: 0
            proxyDataStore.updatePort(portInt)
        }
    }

    fun setUsername(username: String) {
        viewModelScope.launch { proxyDataStore.updateUsername(username) }
    }

    fun setPassword(password: String) {
        viewModelScope.launch { proxyDataStore.updatePassword(password) }
    }
}