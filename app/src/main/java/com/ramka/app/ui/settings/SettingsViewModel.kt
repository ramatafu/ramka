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
import com.ramka.app.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val controller: BackgroundDeliveryController,
    prefs: AppPreferences
) : ViewModel() {

    val oem: Oem = OemDetector.detect(Build.MANUFACTURER, Build.FINGERPRINT)
    val hintText: String = OemHintTexts.forOem(oem)

    var backgroundDelivery by mutableStateOf(prefs.backgroundDeliveryEnabled)
        private set
    var persistentService by mutableStateOf(prefs.persistentServiceEnabled)
        private set
    var showHint by mutableStateOf(false)
        private set

    init {
        // Значение по умолчанию у главного тумблера — ВКЛ, поэтому «первое включение»
        // на практике — первое открытие настроек с включённой фоновой доставкой.
        showHintOnce()
    }

    fun onBackgroundDeliveryChanged(enabled: Boolean) {
        controller.setBackgroundDelivery(enabled)
        backgroundDelivery = enabled
        if (enabled) showHintOnce()
    }

    fun onPersistentServiceChanged(enabled: Boolean) {
        controller.setPersistentService(enabled)
        persistentService = enabled
    }

    /** Ручное повторное открытие подсказки — флаг «показано» не трогает. */
    fun openHintManually() { showHint = true }

    fun dismissHint() { showHint = false }

    private fun showHintOnce() {
        if (controller.shouldShowHint()) {
            showHint = true
            controller.markHintShown()
        }
    }
}
