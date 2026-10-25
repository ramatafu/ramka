package com.ramka.app.ui.settings

import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.ramka.app.background.BackgroundDeliveryController
import com.ramka.app.background.Oem
import com.ramka.app.background.OemDetector
import com.ramka.app.background.OemHintTexts
import com.ramka.app.discovery.LanVisibilityController
import com.ramka.app.preferences.AppPreferences
import com.ramka.app.relay.RelayPoolStore
import com.ramka.domain.relay.RelayPool
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val controller: BackgroundDeliveryController,
    private val lanVisibility: LanVisibilityController,
    private val prefs: AppPreferences,
    poolStore: RelayPoolStore
) : ViewModel() {

    val oem: Oem = OemDetector.detect(Build.MANUFACTURER, Build.FINGERPRINT)
    val hintText: String = OemHintTexts.forOem(oem)

    var backgroundDelivery by mutableStateOf(prefs.backgroundDeliveryEnabled)
        private set
    var persistentService by mutableStateOf(prefs.persistentServiceEnabled)
        private set
    var mdnsEnabled by mutableStateOf(lanVisibility.enabled.value)
        private set
    var callRingtone by mutableStateOf(prefs.callRingtoneEnabled)
        private set

    var callVibration by mutableStateOf(prefs.callVibrationEnabled)
        private set

    var showHint by mutableStateOf(false)
        private set

    /** Пул релеёв для строки «Релеи: N»; сами релеи настраиваются на отдельных экранах. */
    val relayPool: StateFlow<RelayPool> = poolStore.pool

    init {
        showHintOnce()
    }

    // ---- Фоновая доставка / OEM-подсказки: без изменений ----

    fun onBackgroundDeliveryChanged(enabled: Boolean) {
        controller.setBackgroundDelivery(enabled)
        backgroundDelivery = enabled
        if (enabled) showHintOnce()
    }

    fun onPersistentServiceChanged(enabled: Boolean) {
        controller.setPersistentService(enabled)
        persistentService = enabled
    }

    fun onMdnsChanged(enabled: Boolean) {
        lanVisibility.setEnabled(enabled)
        mdnsEnabled = enabled
    }

    fun onCallRingtoneChanged(enabled: Boolean) {
        prefs.callRingtoneEnabled = enabled
        callRingtone = enabled
    }

    fun onCallVibrationChanged(enabled: Boolean) {
        prefs.callVibrationEnabled = enabled
        callVibration = enabled
    }

    fun openHintManually() { showHint = true }
    fun dismissHint() { showHint = false }

    private fun showHintOnce() {
        if (controller.shouldShowHint()) {
            showHint = true
            controller.markHintShown()
        }
    }
}
