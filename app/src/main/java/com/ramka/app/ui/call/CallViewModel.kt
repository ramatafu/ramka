package com.ramka.app.ui.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramka.app.webrtc.createAnswerSdp
import com.ramka.app.webrtc.createOfferSdp
import com.ramka.app.webrtc.setLocalSdp
import com.ramka.app.webrtc.setRemoteSdp
import com.ramka.domain.call.CallManager
import com.ramka.domain.call.CallState
import com.ramka.domain.model.AppMessage
import com.ramka.domain.repository.ContactRepository
import com.ramka.network.call.CallSignaling
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription
import java.util.UUID
import javax.inject.Inject

/**
 * Логика звонка (шаг 7.3): только аудио, только локальные ICE-кандидаты (без STUN/TURN), сигналинг —
 * через [CallSignaling] поверх существующего TransportRepository.
 *
 * ## Время жизни
 * ViewModel привязана к Activity (см. RamkaNavHost), а не к экрану звонка: входящий offer приходит,
 * когда экрана ещё нет, а состояние звонка должно переживать переходы между экранами. Пока Activity
 * жива, входящие звонки принимаются. Приём при убитой/свёрнутой Activity (уведомление, foreground-сервис)
 * в 7.3 не реализован.
 *
 * ## Потоки
 * Все изменения состояния идут через одну последовательную очередь ([post]): команды пользователя, сигналы
 * собеседника, колбэки libwebrtc (они приходят на своих потоках) и таймеры выполняются строго по одной,
 * в порядке поступления. Поэтому нет гонок вида «answer пришёл раньше, чем применился local description»
 * или «hangup посреди createOffer».
 *
 * ## Порядок сигналов
 * Offer/answer ставятся в очередь сигналинга ДО setLocalDescription: кандидаты начинают появляться после
 * него, а очередь сигналинга — FIFO, значит получатель увидит offer раньше своих кандидатов.
 * Кандидаты собеседника, пришедшие до применения remote description, копятся и добавляются после него.
 *
 * ## Освобождение
 * [teardown] закрывает PeerConnection, освобождает AudioTrack/AudioSource, возвращает режим звука и
 * отменяет таймеры; вызывается на любом завершении (свой hangup, hangup собеседника, ошибка, onCleared).
 */
@HiltViewModel
class CallViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val factoryProvider: dagger.Lazy<PeerConnectionFactory>,
    private val signaling: CallSignaling,
    private val contactRepository: ContactRepository
) : ViewModel(), CallManager {

    private val _state = MutableStateFlow<CallState>(CallState.Idle)
    override val state: StateFlow<CallState> = _state.asStateFlow()

    private val _peerName = MutableStateFlow<String?>(null)

    /** Псевдоним собеседника для экрана звонка (null — ещё не известен). */
    val peerName: StateFlow<String?> = _peerName.asStateFlow()

    // ---- текущий звонок (трогается только из очереди команд, кроме onCleared) ----
    private var callId: String? = null
    private var peerContactId: String? = null
    private var isCaller = false
    private var pendingOffer: AppMessage.CallOffer? = null
    private var remoteDescriptionSet = false
    private val pendingRemoteCandidates = ArrayList<IceCandidate>()
    private var muted = false
    private var timeoutJob: Job? = null

    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var previousAudioMode: Int? = null

    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        viewModelScope.launch {
            for (command in commands) {
                try {
                    command()
                } catch (e: Exception) {
                    // Своя отмена (ViewModel очищена) — выходим; чужое исключение одной команды цикл не убивает.
                    currentCoroutineContext().ensureActive()
                    Log.w(TAG, "Сбой обработки команды звонка: ${e.javaClass.simpleName}")
                }
            }
        }
        viewModelScope.launch {
            signaling.incoming.collect { signal -> post { handleSignal(signal) } }
        }
    }

    private fun post(block: suspend () -> Unit) {
        commands.trySend(block)
    }

    // =================================================================== CallManager

    override fun startCall(contactId: String, withVideo: Boolean) = post { doStartCall(contactId, withVideo) }

    override fun answerCall(contactId: String, withVideo: Boolean) = post { doAnswerCall(contactId) }

    override fun rejectCall() = post { doRejectCall() }

    override fun endCall() = post {
        when (_state.value) {
            is CallState.Incoming -> doRejectCall()
            is CallState.Dialing, is CallState.Connected -> {
                teardown(AppMessage.CallEnd.Reason.HANGUP)
                _state.value = CallState.Ended
            }
            else -> Unit
        }
    }

    override fun toggleMute() = post {
        val track = audioTrack ?: return@post
        muted = !muted
        track.setEnabled(!muted)
        (_state.value as? CallState.Connected)?.let { _state.value = it.copy(isMuted = muted) }
    }

    /** Видео — шаг 7.4; сейчас кнопка камеры на экране скрыта, вызов безвреден. */
    override fun toggleVideo() = Unit

    /** Экран звонка закрыт после [CallState.Ended]/[CallState.Error] — возвращаемся в [CallState.Idle]. */
    fun acknowledgeFinished() = post {
        if (_state.value is CallState.Ended || _state.value is CallState.Error) {
            _state.value = CallState.Idle
            _peerName.value = null
        }
    }

    // =================================================================== исходящий

    private suspend fun doStartCall(contactId: String, withVideo: Boolean) {
        if (!isFree()) return
        if (!hasMicPermission()) {
            _state.value = CallState.Error(MSG_NO_MIC)
            return
        }
        if (withVideo) Log.i(TAG, "Видео появится в шаге 7.4: звонок начат как аудио")

        val id = UUID.randomUUID().toString()
        begin(id, contactId, caller = true)
        _state.value = CallState.Dialing(contactId, withVideo = false)
        try {
            val pc = openMedia(id)
            val offer = pc.createOfferSdp(audioConstraints())
            val sent = signaling.sendOffer(contactId, id, offer.description, withVideo = false)
            pc.setLocalSdp(offer)
            watchDelivery(id, sent, MSG_PEER_UNREACHABLE, notifyPeer = false)
            armConnectTimeout(id)
        } catch (e: Exception) {
            failCall(id, MSG_START_FAILED, e)
        }
    }

    // =================================================================== входящий

    private suspend fun handleSignal(signal: CallSignaling.Signal) {
        val contactId = signal.contactId
        when (val message = signal.message) {
            is AppMessage.CallOffer -> onRemoteOffer(contactId, message)
            is AppMessage.CallAnswer -> if (isCurrent(contactId, message.callId)) onRemoteAnswer(message)
            is AppMessage.CallIceCandidate -> if (isCurrent(contactId, message.callId)) onRemoteCandidate(message)
            is AppMessage.CallEnd -> if (isCurrent(contactId, message.callId)) onRemoteEnd(message)
            else -> Unit // сигналинг отдаёт только Call*; остальное игнорируем
        }
    }

    /** Сигнал принадлежит текущему звонку и пришёл от его собеседника (чужой контакт не может ни завершить, ни подменить звонок). */
    private fun isCurrent(contactId: String, signalCallId: String): Boolean =
        signalCallId == callId && contactId == peerContactId

    private suspend fun onRemoteOffer(contactId: String, offer: AppMessage.CallOffer) {
        if (offer.callId == callId) return // повтор уже известного звонка
        if (!isFree()) {
            signaling.sendEnd(contactId, offer.callId, AppMessage.CallEnd.Reason.BUSY)
            return
        }
        begin(offer.callId, contactId, caller = false)
        pendingOffer = offer
        _state.value = CallState.Incoming(contactId, offer.withVideo)
        armIncomingTimeout(offer.callId)
    }

    private suspend fun doAnswerCall(contactId: String) {
        val offer = pendingOffer ?: return
        val id = callId ?: return
        if (_state.value !is CallState.Incoming || peerContactId != contactId) return
        if (!hasMicPermission()) {
            teardown(AppMessage.CallEnd.Reason.REJECTED)
            _state.value = CallState.Error(MSG_NO_MIC)
            return
        }

        timeoutJob?.cancel()
        pendingOffer = null
        _state.value = CallState.Dialing(contactId, withVideo = false) // «соединяемся»
        try {
            val pc = openMedia(id)
            pc.setRemoteSdp(SessionDescription(SessionDescription.Type.OFFER, offer.sdp))
            remoteDescriptionSet = true
            flushRemoteCandidates(pc)
            val answer = pc.createAnswerSdp(audioConstraints())
            val sent = signaling.sendAnswer(contactId, id, answer.description)
            pc.setLocalSdp(answer)
            watchDelivery(id, sent, MSG_ANSWER_FAILED, notifyPeer = true)
            armConnectTimeout(id)
        } catch (e: Exception) {
            failCall(id, MSG_ANSWER_FAILED, e)
        }
    }

    private suspend fun doRejectCall() {
        if (_state.value !is CallState.Incoming) return
        teardown(AppMessage.CallEnd.Reason.REJECTED)
        _state.value = CallState.Ended
    }

    // =================================================================== сигналы собеседника

    private suspend fun onRemoteAnswer(answer: AppMessage.CallAnswer) {
        val pc = peerConnection ?: return
        if (!isCaller || remoteDescriptionSet) return
        val id = answer.callId
        try {
            pc.setRemoteSdp(SessionDescription(SessionDescription.Type.ANSWER, answer.sdp))
            remoteDescriptionSet = true
            flushRemoteCandidates(pc)
        } catch (e: Exception) {
            failCall(id, MSG_CONNECT_FAILED, e)
        }
    }

    private fun onRemoteCandidate(message: AppMessage.CallIceCandidate) {
        val candidate = IceCandidate(message.sdpMid, message.sdpMLineIndex, message.candidate)
        val pc = peerConnection
        if (pc != null && remoteDescriptionSet) {
            if (!pc.addIceCandidate(candidate)) Log.w(TAG, "libwebrtc отклонил кандидат собеседника")
        } else if (pendingRemoteCandidates.size < MAX_PENDING_CANDIDATES) {
            pendingRemoteCandidates += candidate
        }
    }

    private fun flushRemoteCandidates(pc: PeerConnection) {
        for (candidate in pendingRemoteCandidates) pc.addIceCandidate(candidate)
        pendingRemoteCandidates.clear()
    }

    private fun onRemoteEnd(end: AppMessage.CallEnd) {
        teardown(null) // собеседник уже знает; своего End не шлём
        _state.value = when (end.reason) {
            AppMessage.CallEnd.Reason.HANGUP -> CallState.Ended
            AppMessage.CallEnd.Reason.REJECTED -> CallState.Error(MSG_REJECTED)
            AppMessage.CallEnd.Reason.BUSY -> CallState.Error(MSG_BUSY)
        }
    }

    // =================================================================== состояние соединения

    private fun onConnectionState(id: String, newState: PeerConnection.PeerConnectionState) {
        if (id != callId) return // событие устаревшего PeerConnection
        when (newState) {
            PeerConnection.PeerConnectionState.CONNECTED -> {
                val peer = peerContactId ?: return
                if (_state.value is CallState.Dialing) {
                    timeoutJob?.cancel()
                    _state.value = CallState.Connected(peer, withVideo = false, isMuted = muted, isVideoEnabled = false)
                }
            }
            // DISCONNECTED бывает кратковременным (ICE восстановится сам) — ждём либо CONNECTED, либо FAILED.
            PeerConnection.PeerConnectionState.FAILED -> failCall(id, MSG_CONNECTION_LOST, null)
            else -> Unit
        }
    }

    private inner class PcObserver(private val id: String) : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            post {
                if (id != callId) return@post
                val peer = peerContactId ?: return@post
                signaling.sendIceCandidate(peer, id, candidate.sdpMid.orEmpty(), candidate.sdpMLineIndex, candidate.sdp)
            }
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
            post { onConnectionState(id, newState) }
        }

        override fun onSignalingChange(newState: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) = Unit
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) = Unit
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
        override fun onAddStream(stream: MediaStream) = Unit
        override fun onRemoveStream(stream: MediaStream) = Unit
        override fun onDataChannel(channel: DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit
    }

    // =================================================================== ресурсы

    private suspend fun begin(id: String, contactId: String, caller: Boolean) {
        callId = id
        peerContactId = contactId
        isCaller = caller
        pendingOffer = null
        remoteDescriptionSet = false
        pendingRemoteCandidates.clear()
        muted = false
        _peerName.value = try {
            contactRepository.getContact(contactId)?.alias
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            null
        }
    }

    /** Создаёт PeerConnection и локальный аудиотрек. Только локальные кандидаты: список ICE-серверов пуст. */
    private fun openMedia(id: String): PeerConnection {
        val factory = factoryProvider.get()
        val config = PeerConnection.RTCConfiguration(emptyList()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        val pc = factory.createPeerConnection(config, PcObserver(id))
            ?: throw IllegalStateException("createPeerConnection вернул null")
        peerConnection = pc

        val source = factory.createAudioSource(MediaConstraints())
        audioSource = source
        val track = factory.createAudioTrack(AUDIO_TRACK_ID, source)
        track.setEnabled(!muted)
        audioTrack = track
        pc.addTrack(track, listOf(STREAM_ID))

        enterCommunicationAudioMode()
        return pc
    }

    private fun audioConstraints() = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
    }

    private fun enterCommunicationAudioMode() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (previousAudioMode == null) previousAudioMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    private fun exitCommunicationAudioMode() {
        val previous = previousAudioMode ?: return
        (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.mode = previous
        previousAudioMode = null
    }

    /**
     * Единая точка освобождения. [notifyPeer] != null — сообщить собеседнику причиной завершения
     * (сигналинг живёт в appScope, поэтому End уйдёт и после закрытия экрана).
     */
    private fun teardown(notifyPeer: AppMessage.CallEnd.Reason?) {
        timeoutJob?.cancel()
        timeoutJob = null

        val id = callId
        val peer = peerContactId
        if (id != null) {
            signaling.cancelCall(id) // неотправленные offer/кандидаты больше не нужны
            if (notifyPeer != null && peer != null) signaling.sendEnd(peer, id, notifyPeer)
        }

        releaseMedia()

        callId = null
        peerContactId = null
        isCaller = false
        pendingOffer = null
        remoteDescriptionSet = false
        pendingRemoteCandidates.clear()
        muted = false
    }

    private fun releaseMedia() {
        peerConnection?.let { pc ->
            runCatching { pc.close() }
            runCatching { pc.dispose() }
        }
        peerConnection = null
        audioTrack?.let { track ->
            runCatching { track.setEnabled(false) }
            runCatching { track.dispose() }
        }
        audioTrack = null
        audioSource?.let { source -> runCatching { source.dispose() } }
        audioSource = null
        exitCommunicationAudioMode()
    }

    /** [notifyPeer] = false, когда собеседник заведомо недоступен: лишний End только задержал бы очередь сигналинга. */
    private fun failCall(id: String, message: String, cause: Exception?, notifyPeer: Boolean = true) {
        if (id != callId) return // звонок уже завершён другим путём
        if (cause != null) Log.w(TAG, "Звонок завершён с ошибкой: ${cause.javaClass.simpleName}")
        teardown(if (notifyPeer) AppMessage.CallEnd.Reason.HANGUP else null)
        _state.value = CallState.Error(message)
    }

    // =================================================================== таймеры и проверки

    /** Если сигнал (offer/answer) не ушёл — звонок обречён; результат применяется только к своему звонку. */
    private fun watchDelivery(id: String, sent: Deferred<Boolean>, failureMessage: String, notifyPeer: Boolean) {
        viewModelScope.launch {
            if (!sent.await()) post { failCall(id, failureMessage, null, notifyPeer) }
        }
    }

    private fun armConnectTimeout(id: String) {
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(CONNECT_TIMEOUT_MS)
            post { if (id == callId && _state.value is CallState.Dialing) failCall(id, MSG_CONNECT_TIMEOUT, null) }
        }
    }

    private fun armIncomingTimeout(id: String) {
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(INCOMING_TIMEOUT_MS)
            post {
                if (id == callId && _state.value is CallState.Incoming) {
                    teardown(AppMessage.CallEnd.Reason.HANGUP) // пропущенный: звонящий увидит завершение
                    _state.value = CallState.Ended
                }
            }
        }
    }

    private fun isFree(): Boolean =
        _state.value.let { it is CallState.Idle || it is CallState.Ended || it is CallState.Error }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun onCleared() {
        // ViewModel привязана к Activity: уничтожение Activity = выход из приложения. Освобождаем синхронно.
        val id = callId
        val peer = peerContactId
        if (id != null && peer != null) {
            signaling.cancelCall(id)
            signaling.sendEnd(
                peer, id,
                if (pendingOffer != null) AppMessage.CallEnd.Reason.REJECTED else AppMessage.CallEnd.Reason.HANGUP
            )
        }
        timeoutJob?.cancel()
        releaseMedia()
        commands.close()
        super.onCleared()
    }

    companion object {
        private const val TAG = "ramka"
        private const val AUDIO_TRACK_ID = "ramka-audio"
        private const val STREAM_ID = "ramka-stream"

        /** Сколько ждать установки соединения (ICE) после обмена offer/answer. */
        private const val CONNECT_TIMEOUT_MS = 40_000L

        /** Сколько звонит входящий, пока пользователь не ответил. */
        private const val INCOMING_TIMEOUT_MS = 45_000L

        private const val MAX_PENDING_CANDIDATES = 100

        const val MSG_NO_MIC = "Для звонков необходим доступ к микрофону"
        private const val MSG_PEER_UNREACHABLE = "Собеседник недоступен"
        private const val MSG_START_FAILED = "Не удалось начать звонок"
        private const val MSG_ANSWER_FAILED = "Не удалось принять звонок"
        private const val MSG_CONNECT_FAILED = "Не удалось установить соединение"
        private const val MSG_CONNECT_TIMEOUT = "Не удалось соединиться. Звонки работают только в одной локальной сети"
        private const val MSG_CONNECTION_LOST = "Соединение потеряно"
        private const val MSG_REJECTED = "Звонок отклонён"
        private const val MSG_BUSY = "Абонент занят"
    }
}
