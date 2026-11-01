package com.ramka.app.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Уникальный экземпляр DataStore для прокси
val Context.proxyDataStore: DataStore<Preferences> by preferencesDataStore(name = "proxy_settings")

object ProxyPreferences {
    val IS_ENABLED = booleanPreferencesKey("proxy_is_enabled")
    val TYPE = stringPreferencesKey("proxy_type")
    val HOST = stringPreferencesKey("proxy_host")
    val PORT = intPreferencesKey("proxy_port")
    val USERNAME = stringPreferencesKey("proxy_username")
    val PASSWORD = stringPreferencesKey("proxy_password")
}

data class ProxyConfig(
    val isEnabled: Boolean = false,
    val type: String = "SOCKS5",
    val host: String = "",
    val port: Int = 1080,
    val username: String = "",
    val password: String = ""
)

class ProxyDataStore(private val context: Context) {
    
    val proxyConfigFlow: Flow<ProxyConfig> = context.proxyDataStore.data.map { preferences ->
        ProxyConfig(
            isEnabled = preferences[ProxyPreferences.IS_ENABLED] ?: false,
            type = preferences[ProxyPreferences.TYPE] ?: "SOCKS5",
            host = preferences[ProxyPreferences.HOST] ?: "",
            port = preferences[ProxyPreferences.PORT] ?: 1080,
            username = preferences[ProxyPreferences.USERNAME] ?: "",
            password = preferences[ProxyPreferences.PASSWORD] ?: ""
        )
    }

    suspend fun updateEnabled(isEnabled: Boolean) {
        context.proxyDataStore.edit { it[ProxyPreferences.IS_ENABLED] = isEnabled }
    }

    suspend fun updateType(type: String) {
        context.proxyDataStore.edit { it[ProxyPreferences.TYPE] = type }
    }

    suspend fun updateHost(host: String) {
        context.proxyDataStore.edit { it[ProxyPreferences.HOST] = host }
    }

    suspend fun updatePort(port: Int) {
        context.proxyDataStore.edit { it[ProxyPreferences.PORT] = port }
    }

    suspend fun updateUsername(username: String) {
        context.proxyDataStore.edit { it[ProxyPreferences.USERNAME] = username }
    }

    suspend fun updatePassword(password: String) {
        context.proxyDataStore.edit { it[ProxyPreferences.PASSWORD] = password }
    }

    /**
     * Точка доступа для сетевого слоя (OkHttp / WebRTC).
     * Позволяет получить актуальную конфигурацию синхронно (в корутине).
     */
    suspend fun getProxyConfig(): ProxyConfig {
        return proxyConfigFlow.first()
    }
}