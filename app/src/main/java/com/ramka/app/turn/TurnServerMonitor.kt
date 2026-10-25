package com.ramka.app.turn

import com.ramka.app.data.TurnSettingsDataStore
import com.ramka.app.data.TurnUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Глобальный монитор статуса TURN-серверов.
 * Проверяет первый включенный сервер из списка. Если он WORKING, считаем TURN готовым.
 */
@Singleton
class TurnServerMonitor @Inject constructor(
    private val store: TurnSettingsDataStore,
    private val checker: TurnServerChecker,
    appScope: CoroutineScope
) {
    val isTurnReady: StateFlow<Boolean> = store.serversFlow
        .map { list -> list.filter { it.isEnabled } }
        .flatMapLatest { enabledServers ->
            flow {
                if (enabledServers.isEmpty()) {
                    emit(false)
                } else {
                    // Проверяем первый включенный сервер. 
                    // Этого достаточно для глобального индикатора "готовности".
                    val url = TurnUrl.parse(enabledServers.first().url)
                    val isWorking = url?.let { checker.check(it) == TurnServerStatus.WORKING } == true
                    emit(isWorking)
                }
            }
        }
        .stateIn(appScope, SharingStarted.WhileSubscribed(5_000), false)
}