package com.ramka.domain.relay

import kotlinx.coroutines.flow.StateFlow

/** Источник текущего пула релеёв. Реализация в app: хранилище настроек ([RelayPool]). */
interface RelayPoolSource {
    val pool: StateFlow<RelayPool>
}
