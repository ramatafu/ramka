package com.ramka.app.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.ramka.network.proxy.ProxySettings
import com.ramka.network.proxy.ProxyType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first

/**
 * Одноразовый перенос настроек прокси из СТАРОГО хранилища (DataStore `proxy_settings`, где пароль лежал открытым
 * текстом) в [ProxyDataStore] (пароль в Keystore). После успешного переноса старый файл удаляется.
 *
 * Если перенос не удался (например, Keystore недоступен), старый файл остаётся и перенос повторится при следующем
 * запуске: данные не теряются. В лог не попадает ничего, кроме имени класса исключения.
 */
internal object LegacyProxyMigration {

    private const val LEGACY_NAME = "proxy_settings"

    private val IS_ENABLED = booleanPreferencesKey("proxy_is_enabled")
    private val TYPE = stringPreferencesKey("proxy_type")
    private val HOST = stringPreferencesKey("proxy_host")
    private val PORT = intPreferencesKey("proxy_port")
    private val USERNAME = stringPreferencesKey("proxy_username")
    private val PASSWORD = stringPreferencesKey("proxy_password")

    /** Результат чтения старого файла. */
    sealed interface Legacy {
        /** Старого файла нет: переносить нечего. */
        data object Absent : Legacy
        /** Файл есть, но прочитать его нельзя (повреждён): настроек не вытащить, файл надо просто удалить. */
        data object Unreadable : Legacy
        data class Found(val settings: ProxySettings) : Legacy
    }

    /** Чистое преобразование — отдельно, чтобы проверять тестом. */
    fun toSettings(prefs: Preferences): ProxySettings = ProxySettings(
        enabled = prefs[IS_ENABLED] ?: false,
        type = prefs[TYPE]?.let { name -> ProxyType.values().firstOrNull { it.name == name } } ?: ProxyType.SOCKS5,
        host = prefs[HOST].orEmpty(),
        port = prefs[PORT] ?: ProxySettings.DEFAULT_PORT,
        username = prefs[USERNAME].orEmpty(),
        password = prefs[PASSWORD].orEmpty()
    )

    /**
     * Старый «включён», но данные непригодны (прокси при этом никогда не работал: [com.ramka.network.proxy.ProxyManager]
     * читал другое хранилище) → переносим выключенным: [ProxyDataStore.save] не принимает включённый неверный прокси.
     */
    fun normalize(settings: ProxySettings): ProxySettings =
        if (settings.enabled && settings.validate() != null) settings.copy(enabled = false) else settings

    suspend fun read(context: Context): Legacy {
        val file = context.preferencesDataStoreFile(LEGACY_NAME)
        if (!file.exists()) return Legacy.Absent
        // Отдельный короткоживущий экземпляр: старый класс удалён, а два DataStore на один файл в процессе недопустимы.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return try {
            val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            Legacy.Found(toSettings(store.data.first()))
        } catch (e: Exception) {
            Log.w("ramka", "Старые настройки прокси не прочитаны: ${e.javaClass.simpleName}")
            Legacy.Unreadable
        } finally {
            scope.cancel()
        }
    }

    /** Удаляет старый файл и его временную копию. */
    fun deleteFiles(context: Context) {
        val file = context.preferencesDataStoreFile(LEGACY_NAME)
        file.delete()
        java.io.File(file.path + ".tmp").delete()
    }
}
