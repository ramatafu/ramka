package com.ramka.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ramka.crypto.securestorage.SecureKeyStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Настройки собственного TURN-сервера для звонков. */
data class TurnSettings(
    val serverUrl: String,
    val username: String,
    val password: String,
    val isEnabled: Boolean
) {
    /** TURN можно использовать в звонке: включён, адрес корректен, логин и пароль заданы. */
    val isUsable: Boolean
        get() = isEnabled && TurnUrl.parse(serverUrl) != null && username.isNotBlank() && password.isNotBlank()

    /** Адреса для ICE: UDP (основной) и TCP (резервный, для операторов, блокирующих UDP). */
    fun iceUrls(): List<String> {
        val parsed = TurnUrl.parse(serverUrl) ?: return emptyList()
        return listOf(parsed.normalized, "${parsed.normalized}?transport=tcp")
    }

    /** Пароль никогда не попадает в строку (случайный лог, дамп состояния). */
    override fun toString(): String =
        "TurnSettings(serverUrl=$serverUrl, username=$username, password=***, isEnabled=$isEnabled)"

    companion object {
        // Значений по умолчанию нет: каждый пользователь поднимает свой TURN-сервер и вводит его данные
        // на экране «Настройки TURN-сервера». Пока ничего не введено, TURN выключен, звонки идут через STUN.
        const val DEFAULT_SERVER_URL = ""
        const val DEFAULT_USERNAME = ""
        const val DEFAULT_PASSWORD = ""
        const val DEFAULT_ENABLED = false

        val DEFAULT = TurnSettings(DEFAULT_SERVER_URL, DEFAULT_USERNAME, DEFAULT_PASSWORD, DEFAULT_ENABLED)
    }
}

/** Разобранный адрес вида `turn:host:port`. */
data class TurnUrl(val host: String, val port: Int) {
    val normalized: String get() = "turn:$host:$port"

    companion object {
        private val PATTERN = Regex("^turn:([A-Za-z0-9][A-Za-z0-9.-]*):(\\d{1,5})$", RegexOption.IGNORE_CASE)

        /** null — формат неверный. Пробелы по краям игнорируются. */
        fun parse(raw: String): TurnUrl? {
            val match = PATTERN.matchEntire(raw.trim()) ?: return null
            val port = match.groupValues[2].toIntOrNull() ?: return null
            if (port !in 1..65535) return null
            return TurnUrl(match.groupValues[1], port)
        }
    }
}

private val Context.turnDataStore: DataStore<Preferences> by preferencesDataStore(name = "ramka_turn_settings")

/**
 * Хранилище настроек TURN. Адрес, логин и переключатель лежат в Jetpack DataStore; пароль — в
 * [SecureKeyStore] (Android Keystore + AES-256-GCM, как и остальные секреты приложения), в DataStore
 * его нет. Пароль нигде не логируется.
 */
@Singleton
class TurnSettingsDataStore @Inject constructor(
    @ApplicationContext context: Context,
    private val secureKeyStore: SecureKeyStore
) {
    private val dataStore: DataStore<Preferences> = context.turnDataStore

    /** Меняется при каждом сохранении, чтобы смена одного лишь пароля тоже вызывала новое значение потока. */
    private val passwordRevision = MutableStateFlow(0)

    /** Текущие настройки; пока ничего не сохранено, отдаёт значения по умолчанию. */
    val settings: Flow<TurnSettings> = combine(
        dataStore.data.catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e },
        passwordRevision
    ) { prefs, _ ->
        TurnSettings(
            serverUrl = prefs[KEY_SERVER_URL] ?: TurnSettings.DEFAULT_SERVER_URL,
            username = prefs[KEY_USERNAME] ?: TurnSettings.DEFAULT_USERNAME,
            password = readPassword(),
            isEnabled = prefs[KEY_ENABLED] ?: TurnSettings.DEFAULT_ENABLED
        )
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    suspend fun saveSettings(settings: TurnSettings) {
        withContext(Dispatchers.IO) {
            secureKeyStore.putBytes(KEY_PASSWORD, settings.password.toByteArray(Charsets.UTF_8))
        }
        dataStore.edit { prefs ->
            prefs[KEY_SERVER_URL] = settings.serverUrl
            prefs[KEY_USERNAME] = settings.username
            prefs[KEY_ENABLED] = settings.isEnabled
        }
        passwordRevision.update { it + 1 }
    }

    private fun readPassword(): String =
        secureKeyStore.getBytes(KEY_PASSWORD)?.toString(Charsets.UTF_8) ?: TurnSettings.DEFAULT_PASSWORD

    private companion object {
        val KEY_SERVER_URL = stringPreferencesKey("server_url")
        val KEY_USERNAME = stringPreferencesKey("username")
        val KEY_ENABLED = booleanPreferencesKey("enabled")
        const val KEY_PASSWORD = "turn_password"
    }
}
