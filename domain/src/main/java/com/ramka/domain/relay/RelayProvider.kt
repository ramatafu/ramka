package com.ramka.domain.relay

import com.ramka.domain.model.Contact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

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
 * Выбор релея. Транспорт зависит от этого интерфейса, а не от одного адреса: сегодня
 * [SingleRelayProvider] отдаёт один сервер из настроек, завтра можно добавить реализацию со
 * списком серверов, приоритетами и автоматическим переключением без изменений в транспорте.
 */
interface RelayProvider {
    /**
     * Релеи, на которых нужно держать регистрацию, чтобы ПРИНИМАТЬ сообщения. Пустой список —
     * релей не используется. Каждое изменение (смена адреса, токена, выключение) эмитится заново.
     */
    val ownRelays: Flow<List<RelayEndpoint>>

    /** Релеи для ОТПРАВКИ контакту, по убыванию приоритета. Пустой список — релея нет. */
    suspend fun relaysFor(contact: Contact): List<RelayEndpoint>

    /**
     * Обратная связь о результате обращения к релею: [failure] == null — релей исправен
     * (включая ответ «получатель не в сети»). Реализация со списком серверов использует это
     * для переключения; [SingleRelayProvider] лишь запоминает последний результат.
     */
    fun report(endpoint: RelayEndpoint, failure: RelayFailure?)
}

/** Единственный релей из настроек ([RelayConfigSource]). Отдаёт его только если он включён и настроен. */
class SingleRelayProvider(private val source: RelayConfigSource) : RelayProvider {

    private val lastFailureState = MutableStateFlow<RelayFailure?>(null)

    /** Результат последнего обращения к релею (null — исправен или обращений не было). */
    val lastFailure: StateFlow<RelayFailure?> = lastFailureState.asStateFlow()

    override val ownRelays: Flow<List<RelayEndpoint>> =
        source.config.map { listOfNotNull(it.activeEndpoint) }.distinctUntilChanged()

    override suspend fun relaysFor(contact: Contact): List<RelayEndpoint> =
        listOfNotNull(source.config.value.activeEndpoint)

    override fun report(endpoint: RelayEndpoint, failure: RelayFailure?) {
        lastFailureState.value = failure
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
