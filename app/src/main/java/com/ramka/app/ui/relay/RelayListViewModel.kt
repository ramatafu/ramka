package com.ramka.app.ui.relay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramka.app.relay.RelayPoolController
import com.ramka.app.relay.RelayPoolStore
import com.ramka.app.relay.RelayPoolUiState
import com.ramka.domain.relay.PoolRelayProvider
import com.ramka.domain.relay.RelayAddress
import com.ramka.domain.relay.RelayEntry
import com.ramka.domain.relay.RelayFailure
import com.ramka.domain.relay.RelayLinkState
import com.ramka.domain.relay.RelayPool
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Состояние бейджа релея в списке. */
sealed interface RelayBadgeState {
    /** Пул выключен: приложение к релеям не обращается. */
    object Disabled : RelayBadgeState

    /** Данных нет: запись неполная или проверок ещё не было. */
    object Unknown : RelayBadgeState

    /** Идёт подключение или регистрация. */
    object Checking : RelayBadgeState

    /** Релей доступен (приём держится или фоновая проверка прошла). */
    object Ok : RelayBadgeState

    data class Error(val failure: RelayFailure) : RelayBadgeState
}

/** Одна карточка списка: запись, её статус и доступность кнопок перестановки. */
data class RelayListItem(
    val entry: RelayEntry,
    val status: RelayBadgeState,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean
)

data class RelayListUiState(
    val enabled: Boolean,
    val items: List<RelayListItem>,
    val toggleAvailable: Boolean,
    val toggleHint: String?
) {
    /** Больше [RelayPool.MAX_ENTRIES] релеёв в пул не добавить. */
    val canAdd: Boolean get() = items.size < RelayPool.MAX_ENTRIES
}

/** Статус бейджа из флага пула, записи и состояния связи, которое сообщает провайдер. */
internal fun relayBadgeFor(poolEnabled: Boolean, entry: RelayEntry, link: RelayLinkState?): RelayBadgeState = when {
    !poolEnabled -> RelayBadgeState.Disabled
    entry.issue != null -> RelayBadgeState.Unknown
    link == null -> RelayBadgeState.Unknown
    link is RelayLinkState.Connecting -> RelayBadgeState.Checking
    link is RelayLinkState.Connected || link is RelayLinkState.Reachable -> RelayBadgeState.Ok
    link is RelayLinkState.Failed -> RelayBadgeState.Error(link.failure)
    else -> RelayBadgeState.Unknown
}

/** Список релеёв: обёртка над [RelayPoolController] и публичными статусами [PoolRelayProvider]. */
@HiltViewModel
class RelayListViewModel @Inject constructor(
    private val controller: RelayPoolController,
    private val store: RelayPoolStore,
    private val provider: PoolRelayProvider
) : ViewModel() {

    /** Поток состояния для экрана. Создаётся при первом обращении (в тестах используется [current]). */
    val state: StateFlow<RelayListUiState> by lazy {
        combine(store.pool, provider.connectionStates) { pool, links -> build(pool, links) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), current())
    }

    /** Мгновенный снимок состояния. */
    fun current(): RelayListUiState = build(store.pool.value, provider.connectionStates.value)

    fun onEnabledChanged(enabled: Boolean) {
        controller.setPoolEnabled(enabled)
    }

    fun onMoveUp(id: String) {
        controller.moveUp(id)
    }

    fun onMoveDown(id: String) {
        controller.moveDown(id)
    }

    fun onRemove(id: String) {
        controller.remove(id)
    }

    private fun build(pool: RelayPool, links: Map<RelayAddress, RelayLinkState>): RelayListUiState {
        val toggle = RelayPoolUiState(pool.enabled, pool.entries)
        val last = pool.entries.lastIndex
        val items = pool.entries.mapIndexed { index, entry ->
            RelayListItem(
                entry = entry,
                status = relayBadgeFor(pool.enabled, entry, links[entry.address]),
                canMoveUp = index > 0,
                canMoveDown = index < last
            )
        }
        return RelayListUiState(
            enabled = pool.enabled,
            items = items,
            toggleAvailable = toggle.toggleAvailable,
            toggleHint = toggle.toggleHint
        )
    }
}
