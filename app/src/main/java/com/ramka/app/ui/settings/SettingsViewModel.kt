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
import com.ramka.app.relay.RelayPoolStore
import com.ramka.app.yggmail.YggmailConfig
import com.ramka.app.yggmail.YggmailConfigStore
import com.ramka.app.yggmail.YggmailController
import com.ramka.app.yggmail.YggmailDiagnostics
import com.ramka.app.yggmail.YggmailErrors
import com.ramka.app.yggmail.YggmailMail
import com.ramka.domain.relay.RelayPool
import com.ramka.network.ygg.YggdrasilNetwork
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val controller: BackgroundDeliveryController,
    private val lanVisibility: LanVisibilityController,
    private val prefs: AppPreferences,
    poolStore: RelayPoolStore,
    private val yggmailController: YggmailController,
    private val yggmailStore: YggmailConfigStore,
    private val yggmailMail: YggmailMail
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

    var yggdrasil by mutableStateOf(prefs.yggdrasilEnabled)
        private set

    /** Есть ли на устройстве поднятый системный интерфейс Yggdrasil (проверяется при открытии настроек). */
    var yggdrasilInterfaceFound by mutableStateOf(YggdrasilNetwork.hasActiveInterface())
        private set

    // ---- Yggmail (необязательный канал; по умолчанию выключен) ----

    var yggmailEnabled by mutableStateOf(prefs.yggmailEnabled)
        private set

    /** Поля формы. Пароль из хранилища в форму НЕ подставляется: пустое поле = «не менять». */
    var yggmailForm by mutableStateOf(YggmailForm.from(yggmailStore.load()))
        private set

    var yggmailPasswordSaved by mutableStateOf(yggmailStore.load() != null)
        private set

    var yggmailStatus by mutableStateOf<String?>(null)
        private set

    var yggmailBusy by mutableStateOf(false)
        private set

    /** Журнал обмена с сервером после «Проверить» (пароль скрыт): по нему видно, что именно ответил сервер. */
    var yggmailLog by mutableStateOf<List<String>>(emptyList())
        private set

    /** Есть ли схема шифрования офлайн-пакетов; без неё канал ничего не отправляет и не принимает. */
    val yggmailSealerAvailable: Boolean = yggmailController.sealerAvailable

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

    fun onYggdrasilChanged(enabled: Boolean) {
        prefs.yggdrasilEnabled = enabled
        yggdrasil = enabled
        yggdrasilInterfaceFound = YggdrasilNetwork.hasActiveInterface()
    }

    fun onYggmailEnabledChanged(enabled: Boolean) {
        prefs.yggmailEnabled = enabled
        yggmailEnabled = enabled
        yggmailController.refreshSchedule()
    }

    fun onYggmailFormChanged(form: YggmailForm) {
        yggmailForm = form
        yggmailStatus = null
    }

    fun saveYggmail() {
        val config = buildYggmailConfig() ?: return
        if (yggmailStore.save(config)) {
            yggmailPasswordSaved = true
            yggmailForm = yggmailForm.copy(password = "")
            yggmailStatus = "Сохранено"
            yggmailController.refreshSchedule()
        } else {
            yggmailStatus = "Не удалось сохранить: защищённое хранилище недоступно"
        }
    }

    fun testYggmail() {
        val config = buildYggmailConfig() ?: return
        yggmailBusy = true
        yggmailStatus = "Проверка…"
        yggmailLog = emptyList()
        viewModelScope.launch {
            val result = yggmailMail.test(config)
            yggmailStatus = result.fold(
                onSuccess = { "Подключение работает: вход на SMTP и IMAP выполнен" },
                onFailure = { YggmailErrors.describe(it) }
            )
            yggmailLog = YggmailDiagnostics.lines()
            yggmailBusy = false
        }
    }

    /** Собирает настройки из формы; при ошибке выставляет текст в [yggmailStatus] и возвращает null. */
    private fun buildYggmailConfig(): YggmailConfig? {
        val form = yggmailForm
        val config = YggmailConfig(
            smtpHost = form.smtpHost.trim(),
            smtpPort = form.smtpPort.trim().toIntOrNull() ?: -1,
            imapHost = form.imapHost.trim(),
            imapPort = form.imapPort.trim().toIntOrNull() ?: -1,
            login = form.login.trim(),
            password = form.password.ifEmpty { yggmailStore.load()?.password.orEmpty() }
        )
        val error = config.validate()
        if (error != null) {
            yggmailStatus = error
            return null
        }
        return config
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

/** Строки формы Yggmail (порты текстом, чтобы не терять ввод). */
data class YggmailForm(
    val smtpHost: String = "",
    val smtpPort: String = "1025",
    val imapHost: String = "",
    val imapPort: String = "1143",
    val login: String = "",
    val password: String = ""
) {
    companion object {
        fun from(config: YggmailConfig?): YggmailForm =
            if (config == null) YggmailForm()
            else YggmailForm(config.smtpHost, config.smtpPort.toString(), config.imapHost, config.imapPort.toString(), config.login, "")
    }
}
