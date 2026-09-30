@file:Suppress("DEPRECATION")

package com.ramka.crypto.securestorage

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ramka.crypto.keys.KeyValueStore

/**
 * Хранилище приватных ключей и других секретов на устройстве.
 * Мастер-ключ живёт в Android Keystore и не экспортируется;
 * сами значения хранятся зашифрованными AES-256-GCM.
 *
 * Реализует [KeyValueStore], чтобы [com.ramka.crypto.keys.KeyManager] можно было
 * тестировать pure-JVM тестами с in-memory реализацией вместо этого класса.
 *
 * ПОДАВЛЕНИЕ WARNING (`@file:Suppress("DEPRECATION")`) — ОСОЗНАННОЕ, не молчаливое:
 * начиная с версии 1.1.0 Google пометил ВЕСЬ пакет `androidx.security:security-crypto`
 * как deprecated (`MasterKey`, `EncryptedSharedPreferences` и его вложенные
 * `PrefKeyEncryptionScheme`/`PrefValueEncryptionScheme` — все до одного), без замены
 * первой стороной на момент написания. Библиотека при этом функционально не удалена
 * и продолжает работать — Google лишь сигнализирует «пересмотрите подход», но не
 * даёт готовой миграции. Подавление здесь — временное и предметно
 * задокументированное решение, а не способ спрятать проблему; см. DEVIATIONS.md.
 *
 * Никогда не логировать содержимое этого класса.
 */
class SecureKeyStore(context: Context) : KeyValueStore {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun putBytes(key: String, value: ByteArray) {
        prefs.edit().putString(key, Base64.encodeToString(value, Base64.NO_WRAP)).apply()
    }

    override fun getBytes(key: String): ByteArray? =
        prefs.getString(key, null)?.let { Base64.decode(it, Base64.NO_WRAP) }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    companion object {
        private const val PREFS_FILE_NAME = "ramka_secure_prefs"
    }
}
