package com.ramka.app.relay

import com.ramka.crypto.keys.KeyValueStore
import com.ramka.domain.relay.RelayAddress
import com.ramka.domain.relay.RelayConfig
import com.ramka.domain.relay.RelayConfigSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Несекретная часть настроек релея. Реализация — обычные SharedPreferences ([com.ramka.app.preferences.AppPreferences]). */
interface RelayPrefs {
    /** Пользоваться релеем. По умолчанию ВЫКЛ. */
    var relayEnabled: Boolean

    /** `host:port` в нормализованном виде; пусто — не задан. */
    var relayAddress: String

    /** Пин сертификата (64 hex); пусто — не задан. Публичная информация, не секрет. */
    var relayPin: String
}

/**
 * Единый источник настроек релея (Этап 3): и для транспорта ([com.ramka.domain.relay.SingleRelayProvider]),
 * и для экрана настроек. Токен доступа — секрет, поэтому лежит в [secrets]
 * ([com.ramka.crypto.securestorage.SecureKeyStore], зашифрован ключом Android Keystore), а не в
 * обычных SharedPreferences и не в БД. Значение сразу эмитится подписчикам: смена адреса или
 * выключение релея пересоздаёт регистрацию без перезапуска приложения.
 */
class RelaySettings(
    private val prefs: RelayPrefs,
    private val secrets: KeyValueStore
) : RelayConfigSource {

    private val lock = Any()
    private val state = MutableStateFlow(load())

    override val config: StateFlow<RelayConfig> = state.asStateFlow()

    /** Сохраняет настройки целиком (уже проверенные [com.ramka.domain.relay.RelayConfigValidator]). */
    fun save(config: RelayConfig) = synchronized(lock) {
        prefs.relayAddress = config.address?.format().orEmpty()
        secrets.putBytes(TOKEN_KEY, config.token.toByteArray(Charsets.UTF_8))
        prefs.relayPin = config.pinSha256.orEmpty()
        prefs.relayEnabled = config.enabled
        state.value = config
    }

    /** Меняет только переключатель, остальные настройки не трогает. */
    fun setEnabled(enabled: Boolean) = synchronized(lock) {
        if (state.value.enabled == enabled) return@synchronized
        prefs.relayEnabled = enabled
        state.value = state.value.copy(enabled = enabled)
    }

    private fun load(): RelayConfig = RelayConfig(
        enabled = prefs.relayEnabled,
        address = RelayAddress.parseOrNull(prefs.relayAddress),
        token = secrets.getBytes(TOKEN_KEY)?.toString(Charsets.UTF_8).orEmpty(),
        pinSha256 = prefs.relayPin.ifBlank { null }
    )

    private companion object {
        const val TOKEN_KEY = "relay_access_token"
    }
}
