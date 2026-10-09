package com.ramka.app.relay

import com.ramka.domain.relay.RelayEntryErrors

/** Проекция состояния для старого экрана настроек (шаг 2): все поля, которые читает
 *  SettingsScreen.kt. До шага 4 старый экран показывает пул как «первую запись». */
data class RelayFormState(
    val enabled: Boolean,
    val address: String,
    val token: String,
    val pin: String,
    val errors: RelayEntryErrors?,
    val check: RelayCheckUi,
    val active: Boolean,
    val dirty: Boolean
)