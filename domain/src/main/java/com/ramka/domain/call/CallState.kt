package com.ramka.domain.call

/**
 * Состояние звонка (шаг 7.1 — только модель, без реализации).
 *
 * Жизненный цикл: [Idle] -> [Dialing] -> [Connected] -> [Ended] (или [Error] на любом шаге).
 * Звонок идентифицируется contactId контакта (строка — как в остальных моделях :domain).
 */
sealed class CallState {

    /** Звонка нет. */
    data object Idle : CallState()

    /** Исходящий вызов: ждём ответа/установки соединения. */
    data class Dialing(
        val contactId: String,
        val withVideo: Boolean,
    ) : CallState()

    /** Соединение установлено. [isMuted] — микрофон выключен, [isVideoEnabled] — своя камера передаёт видео. */
    data class Connected(
        val contactId: String,
        val withVideo: Boolean,
        val isMuted: Boolean,
        val isVideoEnabled: Boolean,
    ) : CallState()

    /** Звонок завершён штатно (любой стороной). */
    data object Ended : CallState()

    /** Звонок оборвался из-за ошибки; [message] — текст для показа пользователю. */
    data class Error(val message: String) : CallState()
}
