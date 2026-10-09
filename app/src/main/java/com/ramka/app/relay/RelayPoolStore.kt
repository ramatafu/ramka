package com.ramka.app.relay

import android.content.SharedPreferences
import com.ramka.app.preferences.AppPreferences
import com.ramka.crypto.securestorage.SecureKeyStore
import com.ramka.domain.relay.RelayPool
import com.ramka.domain.relay.RelayPoolSource
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** Источник пула с возможностью записи. Продакшн-реализация — [RelayPoolSettings]. */
interface RelayPoolStore : RelayPoolSource {
    /** Атомарно сохраняет новый пул и публикует его в [pool]. */
    fun save(pool: RelayPool)
}

/** Минимальный key-value (обёртка SharedPreferences). */
interface RelayKeyValue {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

/** Хранилище секретов (обёртка SecureKeyStore). */
interface RelaySecrets {
    fun get(alias: String): String?
    fun put(alias: String, value: String)
    fun remove(alias: String)
}

/** Читатель старых одиночных настроек релея (шаги <= 2) для миграции. */
interface LegacyRelayReader {
    fun snapshot(): LegacyRelaySnapshot?
    fun clear()
}

/** [RelayKeyValue] поверх SharedPreferences. */
class PrefsKeyValue @Inject constructor(private val prefs: SharedPreferences) : RelayKeyValue {
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    override fun remove(key: String) { prefs.edit().remove(key).apply() }
}

/**
 * [RelaySecrets] поверх SecureKeyStore: у того API байтовый ([SecureKeyStore.getBytes]),
 * поэтому строки сериализуются в UTF-8 здесь, а не в вызывающем коде.
 */
class KeyStoreSecrets @Inject constructor(private val store: SecureKeyStore) : RelaySecrets {
    override fun get(alias: String): String? = store.getBytes(alias)?.toString(Charsets.UTF_8)
    override fun put(alias: String, value: String) { store.putBytes(alias, value.toByteArray(Charsets.UTF_8)) }
    override fun remove(alias: String) { store.remove(alias) }
}

/** [LegacyRelayReader] поверх AppPreferences + секрета старого токена. */
class AppPreferencesLegacyReader @Inject constructor(
    private val prefs: AppPreferences,
    private val secrets: RelaySecrets
) : LegacyRelayReader {
    override fun snapshot(): LegacyRelaySnapshot? = prefs.legacyRelaySnapshot(secrets)
    override fun clear() { prefs.clearLegacyRelay(secrets) }
}