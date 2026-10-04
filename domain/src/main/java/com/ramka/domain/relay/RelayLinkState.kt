package com.ramka.domain.relay

/**
 * Состояние связи с релеем для экрана настроек (по адресу релея). Отсутствие записи — «данных нет»
 * (релей не используется или ещё не проверялся).
 */
sealed interface RelayLinkState {
    /** Идёт подключение или регистрация. */
    object Connecting : RelayLinkState {
        override fun toString() = "Connecting"
    }

    /** Регистрация на релее активна: приём идёт именно здесь. */
    object Connected : RelayLinkState {
        override fun toString() = "Connected"
    }

    /** Фоновая проверка прошла, релей доступен, но приём на нём не держится (например, режим экономии батареи). */
    data class Reachable(val latencyMillis: Long?) : RelayLinkState

    /** Подключиться не удалось; [retryInMillis] — через сколько клиент повторит регистрацию (если известно). */
    data class Failed(val failure: RelayFailure, val retryInMillis: Long? = null) : RelayLinkState
}
