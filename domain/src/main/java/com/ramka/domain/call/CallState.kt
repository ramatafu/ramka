package com.ramka.domain.call

/**
 * Состояние звонка.
 *
 * Жизненный цикл исходящего: [Idle] -> [Dialing] -> [Connected] -> [Ended] (или [Error] на любом шаге).
 * Входящего: [Idle] -> [Incoming] -> [Dialing] (отвечаем, ждём установки соединения) -> [Connected] -> [Ended].
 * Звонок идентифицируется contactId контакта (строка — как в остальных моделях :domain).
 */
sealed class CallState {

    /** Звонка нет. */
    data object Idle : CallState()

    /** Исходящий вызов (или принятый входящий): ждём ответа/установки соединения. */
    data class Dialing(
        val contactId: String,
        val withVideo: Boolean,
    ) : CallState()

    /** Входящий звонок от [contactId]: ждём, примет его пользователь или отклонит (шаг 7.3). */
    data class Incoming(
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
