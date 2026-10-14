package com.ramka.app.speedtest

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.speedTestDataStore: DataStore<Preferences> by preferencesDataStore(name = "ramka_speedtest")

/** Настройки теста скорости: «URL для теста скорости». По умолчанию пусто — тест меряет только Ping и Jitter. */
@Singleton
class SpeedTestSettings @Inject constructor(@ApplicationContext context: Context) {
    private val dataStore = context.speedTestDataStore

    val urlFlow: Flow<String> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[KEY_URL].orEmpty() }
        .distinctUntilChanged()

    /** Пустая строка очищает настройку. */
    suspend fun setUrl(url: String) {
        dataStore.edit { it[KEY_URL] = url }
    }

    private companion object {
        val KEY_URL = stringPreferencesKey("test_url")
    }
}
