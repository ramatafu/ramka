package com.ramka.domain.usecase

import com.ramka.domain.util.Jitter
import kotlinx.coroutines.delay

/**
 * Отправка ACK с предварительной случайной задержкой (этап 2.5, джиттер): сначала пауза
 * [Jitter.ackDelayMillis], потом попытка доставки. Сама отправка — best-effort, как и раньше
 * (результат не ретраится, ACK не идёт через Outbox).
 *
 * Функции доставки и паузы инжектируются: в приложении это `AttemptDeliveryUseCase` и
 * `kotlinx.coroutines.delay`, в тестах — запись вызовов без реального ожидания.
 */
class DelayedAckSender(
    private val deliver: suspend (contactId: String, payload: ByteArray) -> Boolean,
    private val jitter: Jitter = Jitter(),
    private val pause: suspend (millis: Long) -> Unit = { delay(it) }
) {
    suspend fun send(contactId: String, payload: ByteArray): Boolean {
        val wait = jitter.ackDelayMillis()
        if (wait > 0) pause(wait)
        return deliver(contactId, payload)
    }
}
