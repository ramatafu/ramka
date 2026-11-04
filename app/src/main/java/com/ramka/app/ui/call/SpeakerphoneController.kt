package com.ramka.app.ui.call

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/** Правила громкой связи по умолчанию (отдельно от Android, чтобы проверять тестом). */
object CallAudioPolicy {
    /** Видеозвонок включает громкую связь сам (телефон держат не у уха); аудиозвонок — верхний динамик. */
    fun speakerOnByDefault(withVideo: Boolean): Boolean = withVideo
}

/**
 * Громкая связь звонка через системный [AudioManager] (сервис, а не ресурс: отдельного освобождения не требует, но
 * маршрут нужно вернуть в исходное положение в конце звонка — это делает [reset]).
 *
 * До Android 12 используется `setSpeakerphoneOn`. С Android 12 (API 31) он объявлен устаревшим в пользу
 * `setCommunicationDevice`, поэтому там сначала выбирается встроенный динамик как устройство связи, а
 * `setSpeakerphoneOn` остаётся запасным путём, если устройство не нашлось или система отказала. Перед любым
 * переключением режим выставляется в `MODE_IN_COMMUNICATION`: вне него маршрут связи игнорируется.
 */
class SpeakerphoneController(private val context: Context) {

    private fun audioManager(): AudioManager? = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    fun setEnabled(on: Boolean) {
        val manager = audioManager() ?: return
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (on) {
                val speaker = manager.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speaker != null && manager.setCommunicationDevice(speaker)) return
            } else {
                manager.clearCommunicationDevice()
            }
        }
        @Suppress("DEPRECATION")
        manager.isSpeakerphoneOn = on
    }

    /** Конец звонка: громкая связь выключается, выбор устройства связи сбрасывается. */
    fun reset() {
        val manager = audioManager() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) manager.clearCommunicationDevice()
        @Suppress("DEPRECATION")
        manager.isSpeakerphoneOn = false
    }
}
