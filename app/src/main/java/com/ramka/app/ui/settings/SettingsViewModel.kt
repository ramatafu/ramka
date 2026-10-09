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
import com.ramka.app.relay.RelayCheckUi
import com.ramka.app.relay.RelayEditorController
import com.ramka.app.relay.RelayFormState
import com.ramka.app.relay.RelayPoolController
import com.ramka.app.relay.RelayPoolStore
import com.ramka.domain.relay.PoolRelayProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val controller: BackgroundDeliveryController,
    private val lanVisibility: LanVisibilityController,
    prefs: AppPreferences,
    private val poolController: RelayPoolController,
    private val editorController: RelayEditorController,
    private val poolStore: RelayPoolStore,
    relayProvider: PoolRelayProvider
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

    // ---- Relay (шаг 3): вся логика в RelayPoolController/RelayEditorController, здесь проброс ----

    init {
        // Стартовый экран показывает пул как «первую запись»: открываем её в редакторе синхронно.
        editorController.openLegacyFirst(poolStore.pool.value)
        viewModelScope.launch {
            poolStore.pool.collect { pool ->
                val first = pool.entries.firstOrNull()
                if (first?.id != editorController.state.value.editingId) {
                    if (first == null) editorController.openNew() else editorController.openEdit(first)
                }
            }
        }
        showHintOnce()
    }

    /** Состояние «формы» для старого экрана: переключатель пула + черновик первой записи. */
    val relayForm: StateFlow<RelayFormState> = combine(poolStore.pool, editorController.state) { pool, ed ->
        RelayFormState(
            enabled = pool.enabled,
            address = ed.address,
            token = ed.token,
            pin = ed.pin,
            errors = ed.errors,
            check = ed.check,
            active = pool.enabled && pool.entries.isNotEmpty(),
            dirty = ed.dirty
        )
    }.stateIn(
        viewModelScope,
        kotlinx.coroutines.flow.SharingStarted.Eagerly,
        RelayFormState(
            enabled = poolStore.pool.value.enabled,
            address = editorController.state.value.address,
            token = editorController.state.value.token,
            pin = editorController.state.value.pin,
            errors = null,
            check = RelayCheckUi.Idle,
            active = false,
            dirty = false
        )
    )

    /** Последняя ошибка при обращении к relay (null — исправен или обращений ещё не было). */
    val relayLastFailure = relayProvider.lastFailure

    fun onRelayEnabledChanged(enabled: Boolean) {
        poolController.setPoolEnabled(enabled)
        if (!enabled) editorController.resetCheck()
    }

    fun onRelayAddressChanged(value: String) = editorController.onAddressChanged(value)
    fun onRelayTokenChanged(value: String) = editorController.onTokenChanged(value)
    fun onRelayPinChanged(value: String) = editorController.onPinChanged(value)
    fun onRelaySave() { editorController.save() }
    fun onRelayCheck() {
        viewModelScope.launch { editorController.check() }
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

    fun openHintManually() { showHint = true }
    fun dismissHint() { showHint = false }

    private fun showHintOnce() {
        if (controller.shouldShowHint()) {
            showHint = true
            controller.markHintShown()
        }
    }
}