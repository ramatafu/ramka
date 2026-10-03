package com.ramka.app.relay

import com.ramka.crypto.keys.KeyValueStore
import com.ramka.domain.relay.RelayAddress
import com.ramka.domain.relay.RelayEntry
import com.ramka.domain.relay.RelayPin
import com.ramka.domain.relay.RelayPool
import com.ramka.domain.relay.RelayPoolCodec
import com.ramka.domain.relay.RelayPoolResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Хранилище пула релеёв (Этап 3, пул). Единственный источник правды: весь пул (включая токены
 * доступа) лежит одним JSON-документом в [secrets] ([com.ramka.crypto.securestorage.SecureKeyStore],
 * зашифрован ключом Android Keystore), а не в обычных SharedPreferences и не в БД. Любое изменение
 * сразу сохраняется и эмитится в [pool], поэтому экран и транспорт обновляются без перезапуска.
 *
 * Миграция с одиночных настроек (до пула): при первом чтении, если пула ещё нет, а старые поля
 * ([RelayPrefs.relayAddress], [RelayPrefs.relayPin], [RelayPrefs.relayEnabled] и токен
 * [LEGACY_TOKEN_KEY]) заполнены, они превращаются в пул из одной записи. Старые поля стираются
 * только после того, как новый документ успешно записан и прочитан обратно. Повторный запуск ничего
 * не меняет (пул уже есть). Если документ пула повреждён, пул пустой и ничего не перезаписывается
 * до первого изменения пользователем.
 */
class RelayPoolSettings(
    private val prefs: RelayPrefs,
    private val secrets: KeyValueStore,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() }
) {
    private val lock = Any()
    private val state = MutableStateFlow(load())

    val pool: StateFlow<RelayPool> = state.asStateFlow()

    /** Новый уникальный id для записи пула. */
    fun newEntryId(): String = idGenerator()

    /**
     * Применяет операцию к пулу атомарно: результат [RelayPoolResult.Changed] сохраняется и
     * эмитится, [RelayPoolResult.Rejected] ничего не меняет.
     */
    fun update(transform: (RelayPool) -> RelayPoolResult): RelayPoolResult = synchronized(lock) {
        val result = transform(state.value)
        if (result is RelayPoolResult.Changed && result.pool != state.value) {
            persist(result.pool)
            state.value = result.pool
        }
        result
    }

    fun add(entry: RelayEntry) = update { it.add(entry) }
    fun replace(entry: RelayEntry) = update { it.replace(entry) }
    fun remove(id: String) = update { it.remove(id) }
    fun moveUp(id: String) = update { it.moveUp(id) }
    fun moveDown(id: String) = update { it.moveDown(id) }
    fun setEnabled(enabled: Boolean) = update { RelayPoolResult.Changed(it.copy(enabled = enabled)) }
    fun setBatterySaver(on: Boolean) = update { RelayPoolResult.Changed(it.copy(batterySaver = on)) }

    private fun persist(pool: RelayPool) {
        secrets.putBytes(POOL_KEY, RelayPoolCodec.encode(pool).toByteArray(Charsets.UTF_8))
    }

    private fun load(): RelayPool {
        val raw = secrets.getBytes(POOL_KEY)
        if (raw != null && raw.isNotEmpty()) {
            return RelayPoolCodec.decode(raw.toString(Charsets.UTF_8), idGenerator) ?: RelayPool()
        }
        return migrateLegacy()
    }

    /** Одиночные настройки → пул из одной записи (см. описание класса). */
    private fun migrateLegacy(): RelayPool {
        val rawAddress = prefs.relayAddress
        val rawPin = prefs.relayPin
        val legacyToken = secrets.getBytes(LEGACY_TOKEN_KEY)?.toString(Charsets.UTF_8).orEmpty()
        val address = RelayAddress.parseOrNull(rawAddress)

        val hadLegacy = rawAddress.isNotBlank() || rawPin.isNotBlank() || legacyToken.isNotEmpty() || prefs.relayEnabled
        if (!hadLegacy) return RelayPool() // чистая установка: документ появится при первом изменении

        val pool = if (address == null) {
            RelayPool() // от старых настроек остался мусор без адреса — нечего переносить
        } else {
            val pin = rawPin.takeIf { it.isNotBlank() }?.let { RelayPin.normalize(it) }
            RelayPool(enabled = prefs.relayEnabled, entries = listOf(RelayEntry(idGenerator(), address, legacyToken, pin)))
        }

        persist(pool)
        val written = secrets.getBytes(POOL_KEY)
        if (written != null && written.isNotEmpty()) clearLegacy() // не стираем старое, пока новое не подтверждено
        return pool
    }

    /** Токен затирается пустым значением: у [KeyValueStore] нет операции удаления. */
    private fun clearLegacy() {
        prefs.relayEnabled = false
        prefs.relayAddress = ""
        prefs.relayPin = ""
        secrets.putBytes(LEGACY_TOKEN_KEY, ByteArray(0))
    }

    companion object {
        /** Документ пула (JSON, формат — [RelayPoolCodec]). */
        const val POOL_KEY = "relay_pool_v1"

        /** Токен единственного релея до появления пула; нужен только для миграции. */
        const val LEGACY_TOKEN_KEY = "relay_access_token"
    }
}
