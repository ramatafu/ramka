package com.ramka.app.webrtc

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import kotlin.coroutines.resume

/** Ошибка SDP-операции (отказ libwebrtc или таймаут); текст — только для лога, пользователю не показывается. */
class SdpException(message: String) : Exception(message)

/** Заготовка [SdpObserver] с пустыми реакциями — наследник переопределяет только нужные. */
private open class SdpObserverAdapter : SdpObserver {
    override fun onCreateSuccess(description: SessionDescription) = Unit
    override fun onSetSuccess() = Unit
    override fun onCreateFailure(error: String?) = Unit
    override fun onSetFailure(error: String?) = Unit
}

/** Предел ожидания одной SDP-операции: без него «молчащий» колбэк навсегда завис бы звонок. */
private const val SDP_TIMEOUT_MS = 10_000L

private suspend fun <T> awaitSdp(what: String, block: (resume: (Result<T>) -> Unit) -> Unit): T {
    val result = withTimeoutOrNull(SDP_TIMEOUT_MS) {
        suspendCancellableCoroutine<Result<T>> { cont ->
            block { r -> if (cont.isActive) cont.resume(r) }
        }
    } ?: throw SdpException("$what: таймаут")
    return result.getOrThrow()
}

suspend fun PeerConnection.createOfferSdp(constraints: MediaConstraints): SessionDescription =
    awaitSdp("createOffer") { done ->
        createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(description: SessionDescription) = done(Result.success(description))
            override fun onCreateFailure(error: String?) = done(Result.failure(SdpException("createOffer: отказ")))
        }, constraints)
    }

suspend fun PeerConnection.createAnswerSdp(constraints: MediaConstraints): SessionDescription =
    awaitSdp("createAnswer") { done ->
        createAnswer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(description: SessionDescription) = done(Result.success(description))
            override fun onCreateFailure(error: String?) = done(Result.failure(SdpException("createAnswer: отказ")))
        }, constraints)
    }

suspend fun PeerConnection.setLocalSdp(description: SessionDescription) {
    awaitSdp<Unit>("setLocalDescription") { done ->
        setLocalDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() = done(Result.success(Unit))
            override fun onSetFailure(error: String?) = done(Result.failure(SdpException("setLocalDescription: отказ")))
        }, description)
    }
}

suspend fun PeerConnection.setRemoteSdp(description: SessionDescription) {
    awaitSdp<Unit>("setRemoteDescription") { done ->
        setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() = done(Result.success(Unit))
            override fun onSetFailure(error: String?) = done(Result.failure(SdpException("setRemoteDescription: отказ")))
        }, description)
    }
}
