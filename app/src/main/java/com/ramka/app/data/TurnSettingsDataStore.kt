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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.turnServersDataStore: DataStore<Preferences> by preferencesDataStore(name = "ramka_turn_servers")

/** Старое хранилище одного сервера (до списка): читается один раз для миграции, затем очищается. */
private val Context.legacyTurnDataStore: DataStore<Preferences> by preferencesDataStore(name = "ramka_turn_settings")

/**
 * Список TURN-серверов пользователя. Адреса, логины, флаги и порядок лежат в Jetpack DataStore (JSON);
 * пароли — ТОЛЬКО в [SecureKeyStore] (Android Keystore + AES-256-GCM) под ключом `turn_password_<id>`.
 * Пароль нигде не логируется и не попадает в `toString`.
 *
 * Пустой список — звонки идут только через STUN (прямое соединение P2P).
 */
@Singleton
class TurnSettingsDataStore @Inject constructor(
    @ApplicationContext context: Context,
    private val secureKeyStore: SecureKeyStore
) {
    private val dataStore: DataStore<Preferences> = context.turnServersDataStore
    private val legacyStore: DataStore<Preferences> = context.legacyTurnDataStore

    /** Все операции записи идут по одной, чтобы чтение-изменение-запись списка не затирали друг друга. */
    private val mutex = Mutex()

    @Volatile
    private var migrated = false

    /** Список серверов по порядку приоритета. Первая подписка один раз выполняет миграцию со старого формата. */
    val serversFlow: Flow<List<TurnServerEntry>> = flow {
        ensureMigrated()
        emitAll(
            dataStore.data
                .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
                .map { prefs -> TurnServerList.normalize(TurnServerCodec.decode(prefs[KEY_SERVERS])) }
                .distinctUntilChanged()
        )
    }.flowOn(Dispatchers.IO)

    /**
     * Добавляет сервер в конец списка (включённым).
     * @throws IllegalArgumentException неверный адрес, пустой логин или пароль
     */
    suspend fun addServer(url: String, username: String, password: String): TurnServerEntry =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                migrateLocked()
                val parsed = requireNotNull(TurnUrl.parse(url)) { "Неверный формат адреса TURN" }
                require(username.isNotBlank()) { "Пустой логин TURN" }
                require(password.isNotBlank()) { "Пустой пароль TURN" }
                val entry = TurnServerEntry(
                    id = UUID.randomUUID().toString(),
                    url = parsed.normalized,
                    username = username.trim(),
                    isEnabled = true,
                    order = 0
                )
                putPassword(entry.id, password.trim())
                try {
                    writeList(TurnServerList.add(readList(), entry))
                } catch (e: Throwable) {
                    secureKeyStore.remove(passwordKey(entry.id))
                    throw e
                }
                entry
            }
        }

    suspend fun removeServer(id: String) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                migrateLocked()
                writeList(TurnServerList.remove(readList(), id))
                secureKeyStore.remove(passwordKey(id))
            }
        }
    }

    /**
     * Обновляет адрес, логин и флаг `isEnabled`; позиция в списке не меняется ([moveUp]/[moveDown]).
     * @param password новый пароль; null — оставить прежний
     * @throws NoSuchElementException записи с таким id нет
     * @throws IllegalArgumentException неверный адрес, пустой логин или пустой новый пароль
     */
    suspend fun updateServer(entry: TurnServerEntry, password: String?) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                migrateLocked()
                val list = readList()
                if (list.none { it.id == entry.id }) throw NoSuchElementException("TURN-сервер не найден")
                val parsed = requireNotNull(TurnUrl.parse(entry.url)) { "Неверный формат адреса TURN" }
                require(entry.username.isNotBlank()) { "Пустой логин TURN" }
                if (password != null) {
                    require(password.isNotBlank()) { "Пустой пароль TURN" }
                    putPassword(entry.id, password.trim())
                }
                writeList(
                    TurnServerList.update(list, entry.copy(url = parsed.normalized, username = entry.username.trim()))
                )
            }
        }
    }

    suspend fun moveUp(id: String) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                migrateLocked()
                writeList(TurnServerList.moveUp(readList(), id))
            }
        }
    }

    suspend fun moveDown(id: String) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                migrateLocked()
                writeList(TurnServerList.moveDown(readList(), id))
            }
        }
    }

    /**
     * Серверы для звонка: только включённые, с корректным адресом, логином и паролем, в порядке
     * приоритета. Пустой результат — звонок идёт только через STUN.
     */
    suspend fun activeServers(): List<TurnCredentials> = withContext(Dispatchers.IO) {
        serversFlow.first().mapNotNull { entry ->
            if (!entry.isEnabled || entry.username.isBlank()) return@mapNotNull null
            val parsed = TurnUrl.parse(entry.url) ?: return@mapNotNull null
            val password = readPassword(entry.id)
            if (password.isNullOrBlank()) return@mapNotNull null
            TurnCredentials(parsed.iceUrls(), entry.username, password)
        }
    }

    /**
     * Читает пароль для конкретного сервера из SecureKeyStore.
     * Используется ViewModel при редактировании для заполнения поля ввода.
     */
    suspend fun getPassword(id: String): String? = withContext(Dispatchers.IO) {
        readPassword(id)
    }

    // ---- внутреннее ---------------------------------------------------------------------

    private suspend fun ensureMigrated() {
        if (migrated) return
        mutex.withLock { migrateLocked() }
    }

    /**
     * Одноразовый перенос одиночного сервера из прежнего формата в список (новый id, пароль переезжает
     * в `turn_password_<id>`). Неполные старые настройки (нет адреса, логина или пароля) не переносятся.
     * Вызывать только под [mutex].
     */
    private suspend fun migrateLocked() {
        if (migrated) return
        if (readPrefs()[KEY_MIGRATED] == true) {
            migrated = true
            return
        }
        val legacy = try {
            legacyStore.data.first()
        } catch (e: IOException) {
            emptyPreferences()
        }
        val parsed = legacy[LEGACY_URL]?.let { TurnUrl.parse(it) }
        val username = legacy[LEGACY_USERNAME]?.trim().orEmpty()
        val password = secureKeyStore.getBytes(LEGACY_PASSWORD_KEY)?.toString(Charsets.UTF_8)?.trim().orEmpty()
        val entry = if (parsed != null && username.isNotBlank() && password.isNotBlank()) {
            TurnServerEntry(
                id = UUID.randomUUID().toString(),
                url = parsed.normalized,
                username = username,
                isEnabled = legacy[LEGACY_ENABLED] ?: false,
                order = 0
            ).also { putPassword(it.id, password) }
        } else {
            null
        }
        dataStore.edit { prefs ->
            if (entry != null && prefs[KEY_SERVERS] == null) prefs[KEY_SERVERS] = TurnServerCodec.encode(listOf(entry))
            prefs[KEY_MIGRATED] = true
        }
        // Старые данные удаляем только после успешной записи нового формата.
        secureKeyStore.remove(LEGACY_PASSWORD_KEY)
        try {
            legacyStore.edit { it.clear() }
        } catch (e: IOException) {
            // Не критично: флаг миграции уже стоит, старые настройки больше не читаются.
        }
        migrated = true
    }

    private suspend fun readPrefs(): Preferences =
        try {
            dataStore.data.first()
        } catch (e: IOException) {
            emptyPreferences()
        }

    private suspend fun readList(): List<TurnServerEntry> =
        TurnServerList.normalize(TurnServerCodec.decode(readPrefs()[KEY_SERVERS]))

    private suspend fun writeList(list: List<TurnServerEntry>) {
        dataStore.edit { prefs -> prefs[KEY_SERVERS] = TurnServerCodec.encode(TurnServerList.normalize(list)) }
    }

    private fun putPassword(id: String, password: String) {
        secureKeyStore.putBytes(passwordKey(id), password.toByteArray(Charsets.UTF_8))
    }

    private fun readPassword(id: String): String? =
        secureKeyStore.getBytes(passwordKey(id))?.toString(Charsets.UTF_8)

    private companion object {
        val KEY_SERVERS = stringPreferencesKey("servers_json")
        val KEY_MIGRATED = booleanPreferencesKey("migrated_from_single")

        // Ключи прежнего формата (один сервер).
        val LEGACY_URL = stringPreferencesKey("server_url")
        val LEGACY_USERNAME = stringPreferencesKey("username")
        val LEGACY_ENABLED = booleanPreferencesKey("enabled")
        const val LEGACY_PASSWORD_KEY = "turn_password"

        fun passwordKey(id: String) = "turn_password_$id"
    }
}