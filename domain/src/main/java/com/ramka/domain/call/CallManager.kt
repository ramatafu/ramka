package com.ramka.domain.call

import kotlinx.coroutines.flow.StateFlow

/**
 * Контракт управления звонком (шаг 7.1 — только интерфейс; реализация на WebRTC будет позже).
 *
 * Интерфейс намеренно не зависит от Android и org.webrtc, чтобы :domain оставался чистым JVM-модулем.
 */
interface CallManager {

    /** Текущее состояние звонка. Начальное значение — [CallState.Idle]. */
    val state: StateFlow<CallState>

    /** Начать исходящий звонок контакту [contactId]; [withVideo] — с видео или только аудио. */
    fun startCall(contactId: String, withVideo: Boolean)

    /** Принять входящий звонок от [contactId]; [withVideo] — отвечать ли с видео. */
    fun answerCall(contactId: String, withVideo: Boolean)

    /** Завершить текущий звонок (или отклонить входящий). */
    fun endCall()

    /** Включить/выключить микрофон. */
    fun toggleMute()

    /** Включить/выключить передачу своего видео. */
    fun toggleVideo()
}
