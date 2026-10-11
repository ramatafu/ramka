package com.ramka.app.ui.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramka.app.webrtc.IceServersConfig
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
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CallViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val factoryProvider: dagger.Lazy<PeerConnectionFactory>,
    private val eglBase: EglBase,
    private val signaling: CallSignaling,
    private val contactRepository: ContactRepository
) : ViewModel(), CallManager {

    private val _state = MutableStateFlow<CallState>(CallState.Idle)
    override val state: StateFlow<CallState> = _state.asStateFlow()

    private val _peerName = MutableStateFlow<String?>(null)
    val peerName: StateFlow<String?> = _peerName.asStateFlow()

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    val eglBaseContext: EglBase.Context get() = eglBase.eglBaseContext

    private var callId: String? = null
    private var peerContactId: String? = null
    private var isCaller = false
    private var pendingOffer: AppMessage.CallOffer? = null
    private var remoteDescriptionSet = false
    private val pendingRemoteCandidates = ArrayList<IceCandidate>()
    private var muted = false
    private var timeoutJob: Job? = null

    private var callHasVideo = false
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var videoEnabled = false
    private var uiStopped = false
    private var capturing = false

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

    override fun startCall(contactId: String, withVideo: Boolean) = post { doStartCall(contactId, withVideo) }
    override fun answerCall(contactId: String, withVideo: Boolean) = post { doAnswerCall(contactId, withVideo) }
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

    override fun toggleVideo() = post {
        val track = videoTrack ?: return@post
        videoEnabled = !videoEnabled
        track.setEnabled(videoEnabled)
        updateCapture()
        (_state.value as? CallState.Connected)?.let { _state.value = it.copy(isVideoEnabled = videoEnabled) }
    }

    fun onUiStopped() = post {
        uiStopped = true
        updateCapture()
    }

    fun onUiStarted() = post {
        uiStopped = false
        updateCapture()
    }

    fun acknowledgeFinished() = post {
        if (_state.value is CallState.Ended || _state.value is CallState.Error) {
            _state.value = CallState.Idle
            _peerName.value = null
        }
    }

    private suspend fun doStartCall(contactId: String, withVideo: Boolean) {
        if (!isFree()) return
        if (!hasMicPermission()) {
            _state.value = CallState.Error(MSG_NO_MIC)
            return
        }
        val wantVideo = withVideo && hasCameraPermission()
        if (withVideo && !wantVideo) Log.i(TAG, "Нет разрешения CAMERA: звонок начат как аудио")

        val id = UUID.randomUUID().toString()
        begin(id, contactId, caller = true)
        _state.value = CallState.Dialing(contactId, withVideo = wantVideo)
        try {
            val pc = openMedia(id, withLocalVideo = wantVideo)
            if (callHasVideo != wantVideo) _state.value = CallState.Dialing(contactId, withVideo = callHasVideo)
            val offer = pc.createOfferSdp(mediaConstraints(video = callHasVideo))
            val sent = signaling.sendOffer(contactId, id, offer.description, withVideo = callHasVideo)
            pc.setLocalSdp(offer)
            watchDelivery(id, sent, MSG_PEER_UNREACHABLE, notifyPeer = false)
            armConnectTimeout(id)
        } catch (e: Exception) {
            failCall(id, MSG_START_FAILED, e)
        }
    }

    private suspend fun handleSignal(signal: CallSignaling.Signal) {
        val contactId = signal.contactId
        when (val message = signal.message) {
            is AppMessage.CallOffer -> onRemoteOffer(contactId, message)
            is AppMessage.CallAnswer -> if (isCurrent(contactId, message.callId)) onRemoteAnswer(message)
            is AppMessage.CallIceCandidate -> if (isCurrent(contactId, message.callId)) onRemoteCandidate(message)
            is AppMessage.CallEnd -> if (isCurrent(contactId, message.callId)) onRemoteEnd(message)
            else -> Unit
        }
    }

    private fun isCurrent(contactId: String, signalCallId: String): Boolean =
        signalCallId == callId && contactId == peerContactId

    private suspend fun onRemoteOffer(contactId: String, offer: AppMessage.CallOffer) {
        if (offer.callId == callId) return
        if (!isFree()) {
            signaling.sendEnd(contactId, offer.callId, AppMessage.CallEnd.Reason.BUSY)
            return
        }
        begin(offer.callId, contactId, caller = false)
        pendingOffer = offer
        _state.value = CallState.Incoming(contactId, offer.withVideo)
        armIncomingTimeout(offer.callId)
    }

    private suspend fun doAnswerCall(contactId: String, withVideo: Boolean) {
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
        val sendVideo = withVideo && offer.withVideo && hasCameraPermission()
        _state.value = CallState.Dialing(contactId, withVideo = offer.withVideo)
        try {
            val pc = openMedia(id, withLocalVideo = sendVideo)
            callHasVideo = offer.withVideo
            pc.setRemoteSdp(SessionDescription(SessionDescription.Type.OFFER, offer.sdp))
            remoteDescriptionSet = true
            flushRemoteCandidates(pc)
            val answer = pc.createAnswerSdp(mediaConstraints(video = offer.withVideo))
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
        teardown(null)
        _state.value = when (end.reason) {
            AppMessage.CallEnd.Reason.HANGUP -> CallState.Ended
            AppMessage.CallEnd.Reason.REJECTED -> CallState.Error(MSG_REJECTED)
            AppMessage.CallEnd.Reason.BUSY -> CallState.Error(MSG_BUSY)
        }
    }

    private fun onConnectionState(id: String, newState: PeerConnection.PeerConnectionState) {
        if (id != callId) return
        when (newState) {
            PeerConnection.PeerConnectionState.CONNECTED -> {
                val peer = peerContactId ?: return
                if (_state.value is CallState.Dialing) {
                    timeoutJob?.cancel()
                    _state.value = CallState.Connected(
                        peer,
                        withVideo = callHasVideo,
                        isMuted = muted,
                        isVideoEnabled = videoTrack != null && videoEnabled
                    )
                }
            }
            PeerConnection.PeerConnectionState.FAILED -> failCall(id, MSG_CONNECTION_LOST, null)
            else -> Unit
        }
    }

    private inner class PcObserver(private val id: String) : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            val type = when {
                candidate.sdp.contains("typ host") -> "HOST (Локальный)"
                candidate.sdp.contains("typ srflx") -> "SRFLX (STUN/Публичный)"
                candidate.sdp.contains("typ relay") -> "RELAY (TURN/Ретрансляция) ⭐"
                else -> "UNKNOWN"
            }
            Log.d(TAG, "📤 onIceCandidate: type=$type, sdpMid=${candidate.sdpMid}, sdp=${candidate.sdp}")
            
            post {
                if (id != callId) return@post
                val peer = peerContactId ?: return@post
                signaling.sendIceCandidate(peer, id, candidate.sdpMid.orEmpty(), candidate.sdpMLineIndex, candidate.sdp)
            }
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
            Log.d(TAG, "🔗 onConnectionChange (PeerConnection): $newState")
            post { onConnectionState(id, newState) }
        }

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
            Log.d(TAG, "🔄 onIceConnectionChange (ICE): $newState")
            if (newState == PeerConnection.IceConnectionState.FAILED) {
                Log.e(TAG, "❌ ICE соединение ПРОВАЛИЛОСЬ! Проверь TURN, STUN и сеть.")
            } else if (newState == PeerConnection.IceConnectionState.CONNECTED) {
                Log.i(TAG, "✅ ICE соединение УСПЕШНО установлено!")
            }
        }

        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {
            Log.d(TAG, "📡 onIceGatheringChange: $newState")
        }

        override fun onSignalingChange(newState: PeerConnection.SignalingState) {
            Log.d(TAG, "📝 onSignalingChange: $newState")
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
        
        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
            val track = receiver.track() as? VideoTrack ?: return
            Log.d(TAG, "🎥 onAddTrack: получен удаленный видеотрек")
            post {
                if (id == callId) _remoteVideoTrack.value = track
            }
        }

        override fun onAddStream(stream: MediaStream) = Unit
        override fun onRemoveStream(stream: MediaStream) = Unit
        override fun onDataChannel(channel: DataChannel) = Unit
        
        override fun onRenegotiationNeeded() {
            Log.d(TAG, "🔄 onRenegotiationNeeded")
        }
    }

    private suspend fun begin(id: String, contactId: String, caller: Boolean) {
        callId = id
        peerContactId = contactId
        isCaller = caller
        pendingOffer = null
        remoteDescriptionSet = false
        pendingRemoteCandidates.clear()
        muted = false
        callHasVideo = false
        videoEnabled = false
        _peerName.value = try {
            contactRepository.getContact(contactId)?.alias
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            null
        }
    }

    private fun openMedia(id: String, withLocalVideo: Boolean): PeerConnection {
        val factory = factoryProvider.get()
        val rtcConfig = PeerConnection.RTCConfiguration(IceServersConfig.iceServers()).apply {
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        val pc = factory.createPeerConnection(rtcConfig, PcObserver(id))
            ?: throw IllegalStateException("createPeerConnection вернул null")
        peerConnection = pc

        val source = factory.createAudioSource(MediaConstraints())
        audioSource = source
        val track = factory.createAudioTrack(AUDIO_TRACK_ID, source)
        track.setEnabled(!muted)
        audioTrack = track
        pc.addTrack(track, listOf(STREAM_ID))

        if (withLocalVideo) callHasVideo = startLocalVideo(factory, pc)

        enterCommunicationAudioMode()
        return pc
    }

    private fun mediaConstraints(video: Boolean) = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", video.toString()))
    }

    private fun startLocalVideo(factory: PeerConnectionFactory, pc: PeerConnection): Boolean {
        try {
            val capturer = createCameraCapturer() ?: run {
                Log.w(TAG, "Камера не найдена: звонок без видео")
                return false
            }
            videoCapturer = capturer
            val helper = SurfaceTextureHelper.create(CAPTURE_THREAD_NAME, eglBase.eglBaseContext)
            surfaceTextureHelper = helper
            val source = factory.createVideoSource(false)
            videoSource = source
            capturer.initialize(helper, context, source.capturerObserver)
            val track = factory.createVideoTrack(VIDEO_TRACK_ID, source)
            track.setEnabled(true)
            videoTrack = track
            pc.addTrack(track, listOf(STREAM_ID))
            videoEnabled = true
            _localVideoTrack.value = track
            updateCapture()
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось подготовить видео: ${e.javaClass.simpleName}")
            releaseVideo()
            return false
        }
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val names = enumerator.deviceNames
        val ordered = names.filter { enumerator.isFrontFacing(it) } + names.filterNot { enumerator.isFrontFacing(it) }
        for (name in ordered) {
            enumerator.createCapturer(name, null)?.let { return it }
        }
        return null
    }

    private fun updateCapture() {
        val capturer = videoCapturer ?: return
        val shouldCapture = videoTrack != null && videoEnabled && !uiStopped
        if (shouldCapture && !capturing) {
            try {
                capturer.startCapture(CAPTURE_WIDTH, CAPTURE_HEIGHT, CAPTURE_FPS)
                capturing = true
            } catch (e: Exception) {
                Log.w(TAG, "Камера не запустилась: ${e.javaClass.simpleName}")
            }
        } else if (!shouldCapture && capturing) {
            try {
                capturer.stopCapture()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            capturing = false
        }
    }

    private fun stopVideoCapture() {
        val capturer = videoCapturer ?: return
        if (!capturing) return
        try {
            capturer.stopCapture()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        capturing = false
    }

    private fun releaseVideo() {
        _localVideoTrack.value = null
        _remoteVideoTrack.value = null
        videoCapturer?.let { capturer ->
            if (capturing) {
                try {
                    capturer.stopCapture()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            runCatching { capturer.dispose() }
        }
        videoCapturer = null
        capturing = false
        videoTrack?.let { track ->
            runCatching { track.setEnabled(false) }
            runCatching { track.dispose() }
        }
        videoTrack = null
        videoSource?.let { runCatching { it.dispose() } }
        videoSource = null
        surfaceTextureHelper?.let { runCatching { it.dispose() } }
        surfaceTextureHelper = null
        videoEnabled = false
        callHasVideo = false
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

    private fun teardown(notifyPeer: AppMessage.CallEnd.Reason?) {
        timeoutJob?.cancel()
        timeoutJob = null

        val id = callId
        val peer = peerContactId
        if (id != null) {
            signaling.cancelCall(id)
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
        callHasVideo = false
    }

    private fun releaseMedia() {
        stopVideoCapture()
        peerConnection?.let { pc ->
            runCatching { pc.close() }
            runCatching { pc.dispose() }
        }
        peerConnection = null
        releaseVideo()
        audioTrack?.let { track ->
            runCatching { track.setEnabled(false) }
            runCatching { track.dispose() }
        }
        audioTrack = null
        audioSource?.let { source -> runCatching { source.dispose() } }
        audioSource = null
        exitCommunicationAudioMode()
    }

    private fun failCall(id: String, message: String, cause: Exception?, notifyPeer: Boolean = true) {
        if (id != callId) return
        if (cause != null) Log.w(TAG, "Звонок завершён с ошибкой: ${cause.javaClass.simpleName}")
        teardown(if (notifyPeer) AppMessage.CallEnd.Reason.HANGUP else null)
        _state.value = CallState.Error(message)
    }

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
                    teardown(AppMessage.CallEnd.Reason.HANGUP)
                    _state.value = CallState.Ended
                }
            }
        }
    }

    private fun isFree(): Boolean =
        _state.value.let { it is CallState.Idle || it is CallState.Ended || it is CallState.Error }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onCleared() {
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
        private const val VIDEO_TRACK_ID = "ramka-video"
        private const val STREAM_ID = "ramka-stream"
        private const val CAPTURE_THREAD_NAME = "ramka-capture"

        private const val CAPTURE_WIDTH = 640
        private const val CAPTURE_HEIGHT = 480
        private const val CAPTURE_FPS = 30

        private const val CONNECT_TIMEOUT_MS = 40_000L
        private const val INCOMING_TIMEOUT_MS = 45_000L
        private const val MAX_PENDING_CANDIDATES = 100

        const val MSG_NO_MIC = "Для звонков необходим доступ к микрофону"
        private const val MSG_PEER_UNREACHABLE = "Собеседник недоступен"
        private const val MSG_START_FAILED = "Не удалось начать звонок"
        private const val MSG_ANSWER_FAILED = "Не удалось принять звонок"
        private const val MSG_CONNECT_FAILED = "Не удалось установить соединение"
        private const val MSG_CONNECT_TIMEOUT = "Не удалось соединиться. Проверьте интернет; за строгим NAT/файрволом без TURN звонок может не пройти"
        private const val MSG_CONNECTION_LOST = "Соединение потеряно"
        private const val MSG_REJECTED = "Звонок отклонён"
        private const val MSG_BUSY = "Абонент занят"
    }
}