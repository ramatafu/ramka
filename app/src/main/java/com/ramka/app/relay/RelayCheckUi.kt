package com.ramka.app.relay

import com.ramka.domain.relay.RelayFailure

sealed interface RelayCheckUi {
    object Idle : RelayCheckUi
    object Checking : RelayCheckUi
    object Ok : RelayCheckUi
    data class Failed(val failure: RelayFailure) : RelayCheckUi
}