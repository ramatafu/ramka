package com.ramka.app.connection

import android.content.Context
import com.ramka.app.preferences.AppPreferences
import com.ramka.domain.repository.ContactRepository
import com.ramka.network.ygg.YggdrasilNetwork
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
data class LinkSnapshot(
    val network: NetworkKind,
    val relayConnected: Boolean,
    val turnReady: Boolean,
    /** Yggdrasil доступен: включён, на устройстве есть его интерфейс и есть контакты с Yggmail-адресом. */
    val yggdrasilActive: Boolean = false
)

@Singleton
class ConnectionLinkMonitor @Inject constructor(
    @ApplicationContext context: Context,
    relayProvider: PoolRelayProvider,
    turnMonitor: TurnServerMonitor, // <-- ДОБАВЛЕН ПАРАМЕТР
    prefs: AppPreferences,
    contactRepository: ContactRepository,
    appScope: CoroutineScope
) {
    /** Интерфейс Yggdrasil опрашивается раз в 5 с, пока кто-то смотрит на индикатор (WhileSubscribed ниже). */
    private val yggdrasilActive: Flow<Boolean> = combine(
        flow {
            while (true) {
                emit(prefs.yggdrasilEnabled && YggdrasilNetwork.hasActiveInterface())
                delay(YGGDRASIL_POLL_MS)
            }
        },
        contactRepository.observeContacts().map { contacts -> contacts.any { !it.yggmailAddress.isNullOrBlank() } }
    ) { interfaceUp, anyContact -> interfaceUp && anyContact }.distinctUntilChanged()

    val link: StateFlow<LinkSnapshot> = combine(
        NetworkKindMonitor(context).observe(),
        relayProvider.connectionStates
            .map { states -> states.values.any { it is RelayLinkState.Connected } }
            .distinctUntilChanged(),
        turnMonitor.isTurnReady, // <-- ДОБАВЛЕН ПОТОК
        yggdrasilActive
    ) { network, relay, turn, ygg ->
        LinkSnapshot(network, relay, turn, ygg)
    }
        .stateIn(appScope, SharingStarted.WhileSubscribed(5_000), LinkSnapshot(NetworkKind.NONE, false, false, false))

    private companion object {
        const val YGGDRASIL_POLL_MS = 5_000L
    }
}