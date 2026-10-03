package com.ramka.app.ui.settings

import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramka.app.background.BackgroundDeliveryController
import com.ramka.app.background.Oem
import com.ramka.app.background.OemDetector
import com.ramka.app.background.OemHintTexts
import com.ramka.app.discovery.LanVisibilityController
import com.ramka.app.preferences.AppPreferences
import com.ramka.app.relay.RelayFormController
import com.ramka.domain.relay.SingleRelayProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val controller: BackgroundDeliveryController,
    private val lanVisibility: LanVisibilityController,
    prefs: AppPreferences,
    private val relayController: RelayFormController,
    relayProvider: SingleRelayProvider
) : ViewModel() {

    val oem: Oem = OemDetector.detect(Build.MANUFACTURER, Build.FINGERPRINT)
    val hintText: String = OemHintTexts.forOem(oem)

    var backgroundDelivery by mutableStateOf(prefs.backgroundDeliveryEnabled)
        private set
    var persistentService by mutableStateOf(prefs.persistentServiceEnabled)
        private set
    var mdnsEnabled by mutableStateOf(lanVisibility.enabled.value)
        private set
    var showHint by mutableStateOf(false)
        private set

    // ---- Домашний relay (этап 3): вся логика в RelayFormController, здесь только проброс ----

    /** Состояние формы relay (переключатель, поля, ошибки, результат проверки). */
    val relayForm = relayController.state

    /** Последняя ошибка при обращении к relay (null — исправен или обращений ещё не было). */
    val relayLastFailure = relayProvider.lastFailure

    fun onRelayEnabledChanged(enabled: Boolean) = relayController.onEnabledChanged(enabled)
    fun onRelayAddressChanged(value: String) = relayController.onAddressChanged(value)
    fun onRelayTokenChanged(value: String) = relayController.onTokenChanged(value)
    fun onRelayPinChanged(value: String) = relayController.onPinChanged(value)
    fun onRelaySave() = relayController.save()
    fun onRelayCheck() {
        viewModelScope.launch { relayController.check() }
    }

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

    fun onMdnsChanged(enabled: Boolean) {
        lanVisibility.setEnabled(enabled)
        mdnsEnabled = enabled
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
