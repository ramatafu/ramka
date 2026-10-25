package com.ramka.app.discovery

import com.ramka.network.local.DiscoveredPeer
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

    // --- Состояние обнаруженного пира в локальной сети ---
    private val _activeLanPeer = MutableStateFlow<DiscoveredPeer?>(null)
    val activeLanPeer: StateFlow<DiscoveredPeer?> = _activeLanPeer.asStateFlow()

    fun setEnabled(value: Boolean) {
        if (state.value == value) return
        prefs.mdnsEnabled = value
        state.value = value
        
        // Если mDNS выключили, сбрасываем активного пира
        if (!value) {
            _activeLanPeer.value = null
        }
    }

    /**
     * Вызывается из RamkaApp, когда LanPresenceCoordinator находит устройство в сети.
     */
    fun notifyPeerDiscovered(peer: DiscoveredPeer) {
        _activeLanPeer.value = peer
    }

    /**
     * Сбрасывает активного пира.
     */
    fun clearActivePeer() {
        _activeLanPeer.value = null
    }
}