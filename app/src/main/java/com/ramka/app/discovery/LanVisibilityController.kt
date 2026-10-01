package com.ramka.app.discovery

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Хранилище флага mDNS. Реализация — обычные SharedPreferences ([com.ramka.app.preferences.AppPreferences]). */
interface MdnsPrefs {
    /** Вещать и искать сервис `_ramka._tcp.` через mDNS. По умолчанию ВКЛ. */
    var mdnsEnabled: Boolean
}

/**
 * Тумблер mDNS-видимости (этап 2.5). Единый источник правды для экрана настроек и для
 * [LanPresenceCoordinator]: значение сохраняется в настройках и сразу эмитится подписчикам.
 */
class LanVisibilityController(private val prefs: MdnsPrefs) {
    private val state = MutableStateFlow(prefs.mdnsEnabled)

    val enabled: StateFlow<Boolean> = state.asStateFlow()

    fun setEnabled(value: Boolean) {
        if (state.value == value) return
        prefs.mdnsEnabled = value
        state.value = value
    }
}
