package com.ramka.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ramka.crypto.securestorage.SecureKeyStore
import com.ramka.network.proxy.ProxySettings
import com.ramka.network.proxy.ProxySettingsSource
import com.ramka.network.proxy.ProxyType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.proxyDataStore: DataStore<Preferences> by preferencesDataStore(name = "ramka_proxy")

/**
 * Настройки прокси. Флаг, тип, адрес, порт и логин лежат в Jetpack DataStore (как у TURN-серверов); ПАРОЛЬ — только
 * в [SecureKeyStore] (EncryptedSharedPreferences, Android Keystore, AES-256-GCM) под ключом `proxy_password` и
 * нигде не логируется. «Кнопка паники» стирает оба хранилища.
 */
@Singleton
class ProxyDataStore @Inject constructor(
    @ApplicationContext context: Context,
    private val secureKeyStore: SecureKeyStore
) : ProxySettingsSource {

    private val dataStore: DataStore<Preferences> = context.proxyDataStore

    /** Запись по одной: пароль и остальные поля сохраняются согласованно. */
    private val mutex = Mutex()

    /** Настройки по мере изменения; повреждённый файл читается как «прокси выключен». */
    override val settings: Flow<ProxySettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> decode(prefs) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    /**
     * Сохраняет настройки. Включённый прокси должен быть настроен верно ([ProxySettings.validate]); выключенный
     * сохраняется как введён (пользователь мог выключить переключатель, не закончив заполнять поля).
     * Пустой пароль удаляет сохранённый.
     *
     * @throws IllegalArgumentException включённый прокси с неверными настройками
     */
    suspend fun save(settings: ProxySettings) {
        if (settings.enabled) {
            settings.validate()?.let { throw IllegalArgumentException(it) }
        }
        mutex.withLock {
            withContext(Dispatchers.IO) {
                // Пароль первым: если защищённое хранилище откажет, остальные поля не успеют измениться.
                if (settings.password.isEmpty()) secureKeyStore.remove(KEY_PASSWORD)
                else secureKeyStore.putBytes(KEY_PASSWORD, settings.password.toByteArray())
                dataStore.edit { prefs ->
                    prefs[KEY_ENABLED] = settings.enabled
                    prefs[KEY_TYPE] = settings.type.name
                    prefs[KEY_HOST] = settings.host.trim()
                    prefs[KEY_PORT] = settings.port
                    prefs[KEY_USERNAME] = settings.username
                    // Счётчик: смена одного только пароля не меняет ничего в DataStore и иначе не дошла бы до подписчиков.
                    prefs[KEY_REVISION] = (prefs[KEY_REVISION] ?: 0) + 1
                }
            }
        }
    }

    /** Возвращает настройки по умолчанию и стирает пароль. */
    suspend fun clear() {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                secureKeyStore.remove(KEY_PASSWORD)
                dataStore.edit { it.clear() }
            }
        }
    }

    private fun decode(prefs: Preferences): ProxySettings {
        val type = prefs[KEY_TYPE]?.let { name -> ProxyType.values().firstOrNull { it.name == name } } ?: ProxyType.SOCKS5
        val password = try {
            secureKeyStore.getBytes(KEY_PASSWORD)?.toString(Charsets.UTF_8).orEmpty()
        } catch (e: Exception) {
            "" // Keystore недоступен: без пароля прокси с логином не подключится (соединение не уйдёт напрямую)
        }
        return ProxySettings(
            enabled = prefs[KEY_ENABLED] ?: false,
            type = type,
            host = prefs[KEY_HOST].orEmpty(),
            port = prefs[KEY_PORT] ?: ProxySettings.DEFAULT_PORT,
            username = prefs[KEY_USERNAME].orEmpty(),
            password = password
        )
    }

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("proxy_enabled")
        val KEY_TYPE = stringPreferencesKey("proxy_type")
        val KEY_HOST = stringPreferencesKey("proxy_host")
        val KEY_PORT = intPreferencesKey("proxy_port")
        val KEY_USERNAME = stringPreferencesKey("proxy_username")
        val KEY_REVISION = intPreferencesKey("proxy_revision")
        const val KEY_PASSWORD = "proxy_password"
    }
}
