package com.ramka.app.call

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.ramka.app.notifications.RamkaNotifications
import com.ramka.app.preferences.AppPreferences
import com.ramka.domain.model.AppMessage
import com.ramka.domain.repository.ContactRepository
import com.ramka.network.call.CallSignaling
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Звуки, вибрация и уведомления звонков — только стандартные API Android.
 *
 *  * Входящий: рингтон по кругу (RingtoneManager), вибрация 500/500 мс, heads-up баннер с «Принять/Отклонить».
 *    Звук и вибрация подчиняются переключателям в настройках; вибрация идёт с атрибутами USAGE_ALARM, чтобы
 *    не гаситься беззвучным режимом (режим «Не беспокоить» система может заглушить всё равно).
 *  * Исходящий: гудки (ToneGenerator) от доставки offer до ответа собеседника.
 *  * Пропущенный: обычное уведомление без звука баннера.
 *
 * ## Кто управляет
 * Пока есть экран ([onUiAttached]), решения принимает [com.ramka.app.ui.call.CallViewModel] и дёргает этот класс.
 * Когда экрана нет (Activity уничтожена, процесс жив), входящие сигналы слушает сам [CallAlerts]
 * ([start]): звонит, показывает баннер и ПРЯЧЕТ offer в [pendingOffers] — после тапа по баннеру
 * Activity создаётся заново и ViewModel забирает его ([takePendingOffer]). Без этого звонок терялся бы:
 * у [CallSignaling.incoming] нет повтора.
 */
@Singleton
class CallAlerts @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
    private val signaling: CallSignaling,
    private val contactRepository: ContactRepository,
    private val appScope: CoroutineScope
) {
    /** Звонок, который сейчас звонит у нас. */
    private class Ringing(val callId: String, val contactId: String, val name: String, val withVideo: Boolean)

    /** Просьба «Принять» из уведомления: экран звонка выполняет её, когда появится состояние Incoming. */
    data class AcceptRequest(val contactId: String, val atMillis: Long)

    private val lock = Any()
    private var ringing: Ringing? = null
    private var ringtone: Ringtone? = null
    private var ringtoneLoopJob: Job? = null
    private var ringbackTone: ToneGenerator? = null
    private var uiAttachedCount = 0

    private val pendingOffers = PendingOfferStore<CallSignaling.Signal>()
    private var unattendedTimeoutJob: Job? = null

    private val _declineRequests = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** callId, отклонённые кнопкой в уведомлении, пока жив экран (ViewModel выполняет отказ). */
    val declineRequests: SharedFlow<String> = _declineRequests.asSharedFlow()

    private val _acceptRequest = MutableStateFlow<AcceptRequest?>(null)
    val acceptRequest: StateFlow<AcceptRequest?> = _acceptRequest.asStateFlow()

    /** Просьба открыть экран звонка: тап по баннеру или «Принять». Сам по себе входящий звонок экран НЕ открывает. */
    data class OpenRequest(val contactId: String, val atMillis: Long)

    private val _openCallRequest = MutableStateFlow<OpenRequest?>(null)
    val openCallRequest: StateFlow<OpenRequest?> = _openCallRequest.asStateFlow()

    // ------------------------------------------------------------------ жизненный цикл

    /** Запуск приёма сигналов без экрана. Вызывается один раз из RamkaApp. */
    fun start() {
        appScope.launch {
            signaling.incoming.collect { signal ->
                try {
                    onSignalWithoutUi(signal)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Сбой обработки звонка без экрана: ${e.javaClass.simpleName}")
                }
            }
        }
    }

    fun onUiAttached() {
        synchronized(lock) { uiAttachedCount++ }
    }

    fun onUiDetached() {
        synchronized(lock) { if (uiAttachedCount > 0) uiAttachedCount-- }
    }

    /**
     * Можно ли показать баннер входящего звонка: уведомления разрешены и канал «Звонки» не заглушён пользователем.
     * Если нельзя, навигация открывает экран звонка сама — иначе принять звонок было бы нечем.
     */
    fun isBannerAvailable(): Boolean = RamkaNotifications.canShowCallBanner(context)

    // ------------------------------------------------------------------ входящий

    /** Идемпотентно по [callId]: повторный вызов для уже звонящего звонка ничего не перезапускает. */
    fun startIncoming(callId: String, contactId: String, name: String, withVideo: Boolean) {
        synchronized(lock) {
            if (ringing?.callId == callId) return
            stopIncomingLocked()
            ringing = Ringing(callId, contactId, name, withVideo)
            playRingtoneLocked()
            startVibrationLocked()
            // Баннер показываем всегда, в том числе когда приложение открыто: полноэкранный экран звонка не навязываем.
            RamkaNotifications.showIncomingCall(context, callId, contactId, name, withVideo)
        }
    }

    /** Принят, отклонён, отменён звонящим или истёк таймаут: гасим звук, вибрацию и баннер. */
    fun stopIncoming() {
        synchronized(lock) { stopIncomingLocked() }
    }

    private fun stopIncomingLocked() {
        ringing = null
        ringtoneLoopJob?.cancel()
        ringtoneLoopJob = null
        ringtone?.let { runCatching { it.stop() } }
        ringtone = null
        runCatching { vibrator()?.cancel() }
        RamkaNotifications.cancelIncomingCall(context)
    }

    fun showMissedCall() {
        RamkaNotifications.showMissedCall(context)
    }

    private fun playRingtoneLocked() {
        if (!prefs.callRingtoneEnabled) return
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE) ?: return
            val tone = RingtoneManager.getRingtone(context, uri) ?: return
            tone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) tone.isLooping = true
            tone.play()
            ringtone = tone
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                // До Android 9 зацикливания у Ringtone нет — досигнализируем сами.
                ringtoneLoopJob = appScope.launch {
                    while (isActive) {
                        delay(RINGTONE_RESTART_CHECK_MS)
                        synchronized(lock) { if (ringtone === tone && !tone.isPlaying) tone.play() }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось запустить рингтон: ${e.javaClass.simpleName}")
        }
    }

    private fun startVibrationLocked() {
        if (!prefs.callVibrationEnabled) return
        try {
            val vibrator = vibrator() ?: return
            if (!vibrator.hasVibrator()) return
            val effect = VibrationEffect.createWaveform(VIBRATION_PATTERN, 0) // 0 — повторять с начала
            // USAGE_ALARM: вибрация не гасится беззвучным режимом, как у обычного входящего вызова.
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, attributes)
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось включить вибрацию: ${e.javaClass.simpleName}")
        }
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    // ------------------------------------------------------------------ исходящий

    /** Гудки «идёт вызов»: системный ToneGenerator, пока собеседник не ответил. */
    fun startRingback() {
        synchronized(lock) {
            if (ringbackTone != null) return
            try {
                val generator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, RINGBACK_VOLUME)
                generator.startTone(ToneGenerator.TONE_SUP_RINGTONE)
                ringbackTone = generator
            } catch (e: Exception) {
                Log.w(TAG, "Не удалось включить гудки: ${e.javaClass.simpleName}")
            }
        }
    }

    fun stopRingback() {
        synchronized(lock) {
            ringbackTone?.let {
                runCatching { it.stopTone() }
                runCatching { it.release() }
            }
            ringbackTone = null
        }
    }

    // ------------------------------------------------------------------ действия из уведомления

    /** Разбор Intent, пришедшего из баннера (см. MainActivity): «Принять» превращается в просьбу к экрану звонка. */
    fun handleIntent(intent: Intent?) {
        if (intent == null || !intent.getBooleanExtra(EXTRA_INCOMING_CALL, false)) return
        val contactId = intent.getStringExtra(EXTRA_CALLER_ID)
        val action = intent.getStringExtra(EXTRA_CALL_ACTION)
        // Intent переживает пересоздание Activity — не обрабатываем его дважды.
        intent.removeExtra(EXTRA_INCOMING_CALL)
        intent.removeExtra(EXTRA_CALL_ACTION)
        if (contactId == null) return
        // Тап по баннеру и «Принять» относятся к звонку, который ещё звонит; устаревшие нажатия игнорируем.
        val stillRinging = synchronized(lock) { ringing?.contactId == contactId }
        if (!stillRinging) return
        val now = System.currentTimeMillis()
        _openCallRequest.value = OpenRequest(contactId, now)
        if (action == ACTION_ACCEPT) _acceptRequest.value = AcceptRequest(contactId, now)
    }

    fun consumeAcceptRequest() {
        _acceptRequest.value = null
    }

    fun consumeOpenRequest() {
        _openCallRequest.value = null
    }

    /** «Отклонить» в баннере. Есть экран — решает ViewModel; нет — отвечаем звонящему сами. */
    fun declineFromNotification(callId: String, contactId: String) {
        val attached = synchronized(lock) { uiAttachedCount > 0 }
        if (attached) {
            _declineRequests.tryEmit(callId)
            return
        }
        if (pendingOffers.key() == callId) {
            pendingOffers.clear(callId)
            unattendedTimeoutJob?.cancel()
            signaling.sendEnd(contactId, callId, AppMessage.CallEnd.Reason.REJECTED)
        }
        stopIncoming()
    }

    // ------------------------------------------------------------------ звонок без экрана

    /** Offer, пришедший без экрана, для ViewModel, который только что создали (тап по баннеру). */
    fun takePendingOffer(): CallSignaling.Signal? {
        unattendedTimeoutJob?.cancel()
        unattendedTimeoutJob = null
        return pendingOffers.take()
    }

    private suspend fun onSignalWithoutUi(signal: CallSignaling.Signal) {
        if (synchronized(lock) { uiAttachedCount > 0 }) return // экран жив — этим занимается CallViewModel
        when (val message = signal.message) {
            is AppMessage.CallOffer -> {
                val knownCall = pendingOffers.key()
                if (knownCall == message.callId) return
                if (knownCall != null) {
                    signaling.sendEnd(signal.contactId, message.callId, AppMessage.CallEnd.Reason.BUSY)
                    return
                }
                val name = nameOf(signal.contactId)
                pendingOffers.put(message.callId, signal)
                startIncoming(message.callId, signal.contactId, name, message.withVideo)
                scheduleUnattendedTimeout(message.callId, signal.contactId)
            }
            is AppMessage.CallEnd -> {
                if (pendingOffers.key() != message.callId) return
                pendingOffers.clear(message.callId)
                unattendedTimeoutJob?.cancel()
                stopIncoming()
                if (message.reason == AppMessage.CallEnd.Reason.HANGUP) showMissedCall()
            }
            else -> Unit
        }
    }

    private fun scheduleUnattendedTimeout(callId: String, contactId: String) {
        unattendedTimeoutJob?.cancel()
        unattendedTimeoutJob = appScope.launch {
            delay(PendingOfferStore.DEFAULT_TTL_MILLIS)
            if (pendingOffers.key() == callId || synchronized(lock) { ringing?.callId == callId }) {
                pendingOffers.clear(callId)
                stopIncoming()
                signaling.sendEnd(contactId, callId, AppMessage.CallEnd.Reason.HANGUP)
                showMissedCall()
            }
        }
    }

    private suspend fun nameOf(contactId: String): String =
        try {
            contactRepository.getContact(contactId)?.alias?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_NAME
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DEFAULT_NAME
        }

    companion object {
        private const val TAG = "ramka"
        private const val DEFAULT_NAME = "Контакт"

        /** 500 мс вибрации / 500 мс пауза, по кругу. */
        private val VIBRATION_PATTERN = longArrayOf(0, 500, 500, 500, 500)
        private const val RINGTONE_RESTART_CHECK_MS = 1_000L
        private const val RINGBACK_VOLUME = 70

        const val EXTRA_INCOMING_CALL = "INCOMING_CALL"
        const val EXTRA_CALLER_ID = "CALLER_ID"
        const val EXTRA_CALL_ID = "CALL_ID"
        const val EXTRA_CALL_ACTION = "CALL_ACTION"
        const val ACTION_OPEN = "open"
        const val ACTION_ACCEPT = "accept"
        const val ACTION_DECLINE = "com.ramka.app.call.DECLINE"
    }
}
