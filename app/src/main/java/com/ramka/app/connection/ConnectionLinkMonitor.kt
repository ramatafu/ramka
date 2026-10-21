package com.ramka.app.connection

import android.content.Context
import com.ramka.domain.relay.PoolRelayProvider
import com.ramka.domain.relay.RelayLinkState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

data class LinkSnapshot(val network: NetworkKind, val relayConnected: Boolean)

/**
 * Собирает из существующих источников «сырое» состояние связи: вид сети и наличие активной регистрации
 * на релее ([PoolRelayProvider.connectionStates]). Ничего не меняет и не запрашивает у серверов.
 * Работает, только пока на него кто-то подписан (WhileSubscribed).
 */
@Singleton
class ConnectionLinkMonitor @Inject constructor(
    @ApplicationContext context: Context,
    relayProvider: PoolRelayProvider,
    appScope: CoroutineScope
) {
    val link: StateFlow<LinkSnapshot> = combine(
        NetworkKindMonitor(context).observe(),
        relayProvider.connectionStates
            .map { states -> states.values.any { it is RelayLinkState.Connected } }
            .distinctUntilChanged()
    ) { network, relay -> LinkSnapshot(network, relay) }
        .stateIn(appScope, SharingStarted.WhileSubscribed(5_000), LinkSnapshot(NetworkKind.NONE, false))
}
