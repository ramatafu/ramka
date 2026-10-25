package com.ramka.app.connection

import android.content.Context
import com.ramka.domain.relay.PoolRelayProvider
import com.ramka.domain.relay.RelayLinkState
import com.ramka.app.turn.TurnServerMonitor // <-- ДОБАВЛЕН ИМПОРТ
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

// <-- ДОБАВЛЕНО turnReady: Boolean
data class LinkSnapshot(val network: NetworkKind, val relayConnected: Boolean, val turnReady: Boolean)

@Singleton
class ConnectionLinkMonitor @Inject constructor(
    @ApplicationContext context: Context,
    relayProvider: PoolRelayProvider,
    turnMonitor: TurnServerMonitor, // <-- ДОБАВЛЕН ПАРАМЕТР
    appScope: CoroutineScope
) {
    val link: StateFlow<LinkSnapshot> = combine(
        NetworkKindMonitor(context).observe(),
        relayProvider.connectionStates
            .map { states -> states.values.any { it is RelayLinkState.Connected } }
            .distinctUntilChanged(),
        turnMonitor.isTurnReady // <-- ДОБАВЛЕН ПОТОК
    ) { network, relay, turn -> 
        LinkSnapshot(network, relay, turn) 
    }
        .stateIn(appScope, SharingStarted.WhileSubscribed(5_000), LinkSnapshot(NetworkKind.NONE, false, false))
}