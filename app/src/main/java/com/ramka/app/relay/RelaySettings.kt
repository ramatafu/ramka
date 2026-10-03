package com.ramka.app.relay

import com.ramka.crypto.keys.KeyValueStore
import com.ramka.domain.relay.RelayConfig
import com.ramka.domain.relay.RelayConfigSource
import com.ramka.domain.relay.RelayEntry
import com.ramka.domain.relay.RelayPool
import com.ramka.domain.relay.RelayPoolResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Старые одиночные настройки релея (SharedPreferences, [com.ramka.app.preferences.AppPreferences]).
 * С появлением пула используются ТОЛЬКО для миграции ([RelayPoolSettings]): новые данные сюда не
 * пишутся, после переноса поля стираются.
 */
interface RelayPrefs {
    /** Пользоваться релеем. По умолчанию ВЫКЛ. */
    var relayEnabled: Boolean

    /** `host:port` в нормализованном виде; пусто — не задан. */
    var relayAddress: String

    /** Пин сертификата (64 hex); пусто — не задан. Публичная информация, не секрет. */
    var relayPin: String
}

/**
 * Переходный мост «один релей» поверх пула ([RelayPoolSettings]): старый экран настроек и
 * [com.ramka.domain.relay.SingleRelayProvider] продолжают работать с одним релеем, который теперь
 * хранится первой записью пула. Все остальные записи пула мост не трогает. Заменяется на шаге 3
 * контроллерами пула и удаляется вместе с [RelayConfig].
 */
class RelaySettings(private val poolSettings: RelayPoolSettings) : RelayConfigSource {

    /** Для существующего кода и тестов: пул создаётся внутри (миграция выполняется при создании). */
    constructor(prefs: RelayPrefs, secrets: KeyValueStore) : this(RelayPoolSettings(prefs, secrets))

    private val lock = Any()
    private val state = MutableStateFlow(toConfig(poolSettings.pool.value))

    override val config: StateFlow<RelayConfig> = state.asStateFlow()

    /** Сохраняет настройки целиком (уже проверенные [com.ramka.domain.relay.RelayConfigValidator]). */
    fun save(config: RelayConfig) = synchronized(lock) {
        poolSettings.update { pool ->
            val first = pool.entries.firstOrNull()
            val address = config.address
            val entries = when {
                address == null -> pool.entries.drop(1)
                first == null -> listOf(RelayEntry(poolSettings.newEntryId(), address, config.token, config.pinSha256))
                else -> listOf(first.copy(address = address, token = config.token, pinSha256 = config.pinSha256)) + pool.entries.drop(1)
            }
            RelayPoolResult.Changed(pool.copy(enabled = config.enabled, entries = entries))
        }
        state.value = toConfig(poolSettings.pool.value)
    }

    /** Меняет только переключатель, остальные настройки не трогает. */
    fun setEnabled(enabled: Boolean) = synchronized(lock) {
        poolSettings.setEnabled(enabled)
        state.value = toConfig(poolSettings.pool.value)
    }

    private fun toConfig(pool: RelayPool): RelayConfig {
        val first = pool.entries.firstOrNull()
        return RelayConfig(
            enabled = pool.enabled,
            address = first?.address,
            token = first?.token.orEmpty(),
            pinSha256 = first?.pinSha256
        )
    }
}
