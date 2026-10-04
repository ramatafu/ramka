package com.ramka.domain.relay

import com.ramka.domain.model.Contact
import kotlinx.coroutines.flow.Flow

/** Причина неудачи при работе с релеем. Используется и для статуса в UI, и для [RelayProvider.report]. */
enum class RelayFailure {
    /** Не удалось соединиться: нет сети, неверный адрес/порт, не пробросили порт. */
    UNREACHABLE,

    /** Ошибка TLS: пин не совпал, сертификат недействителен. */
    TLS,

    /** Платформа не умеет TLS 1.3 (Android 8–9 без сторонней библиотеки). */
    TLS13_UNSUPPORTED,

    /** Релей отклонил токен (ERR AUTH). */
    AUTH,

    /** Нарушение протокола (неверный кадр, версия, подпись). */
    PROTOCOL,

    /** Получателя нет на релее (он не в сети): сам релей исправен. */
    TARGET_OFFLINE,

    /** Релей не ответил вовремя / получатель не принял соединение. */
    TIMEOUT,

    /** Релей ограничил нагрузку (лимиты, блокировка по частоте ошибок). */
    BUSY,

    /** Другое устройство с тем же ключом подключилось к релею (ERR REPLACED). */
    REPLACED,

    /** Внутренняя ошибка релея. */
    INTERNAL
}

/**
 * Выбор релея. Транспорт зависит от этого интерфейса, а не от одного адреса: сейчас реализация —
 * [PoolRelayProvider] (пул релеёв с приоритетами, паузами после ошибок и «залипанием»).
 */
interface RelayProvider {
    /**
     * Релеи, на которых нужно держать регистрацию, чтобы ПРИНИМАТЬ сообщения. Пустой список —
     * релей не используется. Эмитится при любом изменении набора; транспорт сравнивает НАБОРЫ, поэтому
     * перестановка не рвёт рабочие соединения.
     */
    val ownRelays: Flow<List<RelayEndpoint>>

    /** Релеи для ОТПРАВКИ контакту в порядке попыток. Пустой список — релея нет. */
    suspend fun relaysFor(contact: Contact): List<RelayEndpoint>

    /**
     * Обратная связь о результате обращения к релею: [failure] == null — релей исправен
     * (включая ответ «получатель не в сети»).
     */
    fun report(endpoint: RelayEndpoint, failure: RelayFailure?)

    /**
     * Доставка контакту завершена через [endpoint]: [latencyMillis] — время попытки на этом релее,
     * [fallback] — релей был не первым в очереди. По умолчанию ничего не делает.
     */
    fun reportDelivery(endpoint: RelayEndpoint, contact: Contact, latencyMillis: Long, fallback: Boolean) {}

    /** Состояние регистрации на релее (приём). По умолчанию сводится к [report]. */
    fun reportLink(endpoint: RelayEndpoint, state: RelayLinkState) {
        when (state) {
            RelayLinkState.Connected -> report(endpoint, null)
            is RelayLinkState.Failed -> report(endpoint, state.failure)
            RelayLinkState.Connecting, is RelayLinkState.Reachable -> Unit
        }
    }
}

/** Результат проверки подключения к релею (кнопка «Проверить подключение» в настройках). */
sealed interface RelayCheckResult {
    /** TLS (с пином/CA), токен и протокол в порядке. */
    object Ok : RelayCheckResult {
        override fun toString() = "Ok"
    }

    data class Failed(val failure: RelayFailure) : RelayCheckResult
}

/** Проверка релея без регистрации: не вытесняет рабочее подключение этого устройства. */
fun interface RelayChecker {
    suspend fun check(endpoint: RelayEndpoint): RelayCheckResult
}
