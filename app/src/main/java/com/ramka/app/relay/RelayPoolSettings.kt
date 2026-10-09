package com.ramka.app.relay

import com.ramka.domain.relay.RelayPool
import com.ramka.domain.relay.RelayPoolCodec
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Singleton
class RelayPoolSettings @Inject constructor(
    private val kv: RelayKeyValue,
    private val secrets: RelaySecrets,
    private val legacy: LegacyRelayReader
) : RelayPoolStore {

    private val _pool = MutableStateFlow(load())
    override val pool: StateFlow<RelayPool> = _pool

    override fun save(pool: RelayPool) {
        persist(pool)
        _pool.value = pool
    }

    private fun load(): RelayPool {
        secrets.get(ALIAS_POOL)?.let { json ->
            RelayPoolCodec.decode(json, ::newId)?.let { return it }
        }
        val snapshot = legacy.snapshot()
        val entry = RelayPoolMigrator.migrate(snapshot)
        legacy.clear()
        kv.put(KEY_MIGRATED, "1")
        val initial = if (entry != null && snapshot != null) {
            RelayPool(entries = listOf(entry), enabled = snapshot.enabled)
        } else {
            RelayPool(entries = emptyList(), enabled = false)
        }
        persist(initial)
        return initial
    }

    private fun persist(pool: RelayPool) {
        secrets.put(ALIAS_POOL, RelayPoolCodec.encode(pool))
    }

    private fun newId(): String = UUID.randomUUID().toString()

    companion object {
        const val ALIAS_POOL = "relay_pool_v1"
        const val KEY_MIGRATED = "relay_pool_migrated"
    }
}