package com.ramka.app.yggmail

import com.ramka.crypto.securestorage.SecureKeyStore
import javax.inject.Inject
import javax.inject.Singleton

/** Откуда транспорт берёт настройки (интерфейс — чтобы подставлять заглушку в тестах). */
interface YggmailConfigSource {
    fun load(): YggmailConfig?
}

/**
 * Хранилище настроек Yggmail в [SecureKeyStore] — это EncryptedSharedPreferences с мастер-ключом в Android Keystore
 * (AES-256-GCM); тем же механизмом приложение хранит ключи устройства и пароли TURN. Пароль в открытом виде
 * нигде не лежит. Если хранилище недоступно (сбой Keystore), методы не бросают исключений: настроек «нет» —
 * функция просто не работает, остальное приложение не затрагивается.
 */
@Singleton
class YggmailConfigStore @Inject constructor(
    private val secureStore: SecureKeyStore
) : YggmailConfigSource {

    override fun load(): YggmailConfig? = try {
        secureStore.getBytes(KEY)?.let { YggmailConfigCodec.decode(it) }
    } catch (e: Exception) {
        null
    }

    /** @return false, если сохранить не удалось (хранилище недоступно). */
    fun save(config: YggmailConfig): Boolean = try {
        secureStore.putBytes(KEY, YggmailConfigCodec.encode(config))
        true
    } catch (e: Exception) {
        false
    }

    fun clear() {
        try {
            secureStore.remove(KEY)
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val KEY = "yggmail_config_v1"
    }
}
