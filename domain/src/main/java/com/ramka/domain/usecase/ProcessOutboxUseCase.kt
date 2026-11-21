package com.ramka.domain.usecase

import com.ramka.domain.model.AppMessage
import com.ramka.domain.model.MessageBody
import com.ramka.domain.model.MessageStatus
import com.ramka.domain.model.OutboxEntry
import com.ramka.domain.repository.MessageRepository
import com.ramka.domain.repository.OutboxRepository
import com.ramka.domain.util.Jitter
import com.ramka.domain.util.RetryBackoff

/**
 * Обрабатывает записи Outbox, чьё время следующей попытки уже наступило (ЭТАП B,
 * пункты 3 и 6). Вызывается по трём независимым триггерам (mDNS-обнаружение пира,
 * периодический WorkManager-sweep не чаще раза в 15 минут, ручное «обновить») —
 * сам класс не знает, кто и почему его вызвал, только обрабатывает то, что due
 * прямо сейчас.
 *
 * Каждая попытка — [AttemptDeliveryUseCase], то есть полноценное IK-рукопожатие.
 * Инвариант D-3/D-6 (без CounterStorage нельзя снимать "одно сообщение — одно
 * соединение") этим кодом не затрагивается вообще — здесь нет ничего похожего на
 * переиспользование сессии или счётчика между попытками.
 */
class ProcessOutboxUseCase(
    private val outboxRepository: OutboxRepository,
    private val messageRepository: MessageRepository,
    private val attemptDelivery: AttemptDeliveryUseCase,
    /** Джиттер ±20% на интервал повтора (этап 2.5); по умолчанию — боевой конфиг. */
    private val jitter: Jitter = Jitter()
) {
    /** Сообщения, которые обрабатываются прямо сейчас: параллельные вызовы не отправят одно и то же дважды. */
    private val inFlight = HashSet<String>()

    suspend fun processDue(nowEpochMillis: Long) {
        process(outboxRepository.due(nowEpochMillis), nowEpochMillis, forced = false)
    }

    /**
     * Связь появилась (релей подключился, найден LAN-пир): пробует ВСЕ записи Outbox, не дожидаясь
     * `nextAttemptAt`. Неудачная принудительная попытка расписание не меняет: backoff не растёт от того,
     * что связь мигает.
     */
    suspend fun processAll(nowEpochMillis: Long) {
        process(outboxRepository.due(Long.MAX_VALUE), nowEpochMillis, forced = true)
    }

    private suspend fun process(entries: List<OutboxEntry>, nowEpochMillis: Long, forced: Boolean) {
        for (entry in entries) {
            // Запись уже обрабатывается другим вызовом (старт, LAN-триггер, sweep, восстановление связи): пропускаем.
            if (!claim(entry.messageLocalId)) continue
            try {
                processEntry(entry, nowEpochMillis, forced)
            } finally {
                release(entry.messageLocalId)
            }
        }
    }

    // synchronized, а не Mutex: освобождение в finally не должно зависеть от отмены корутины.
    private fun claim(id: String): Boolean = synchronized(inFlight) { inFlight.add(id) }

    private fun release(id: String) {
        synchronized(inFlight) { inFlight.remove(id) }
    }

    private suspend fun processEntry(entry: OutboxEntry, nowEpochMillis: Long, forced: Boolean) {
        val message = messageRepository.getMessage(entry.messageLocalId)
        if (message == null) {
            // Сообщение удалено пользователем (или иначе исчезло) — ретраить нечего.
            outboxRepository.remove(entry.messageLocalId)
            return
        }
        if (message.status == MessageStatus.SENT || message.status == MessageStatus.DELIVERED ||
            message.status == MessageStatus.READ
        ) {
            // Другой вызов уже отправил его, пока мы ждали своей очереди: повторной отправки не будет.
            outboxRepository.remove(entry.messageLocalId)
            return
        }

        val text = (message.body as? MessageBody.Text)?.text
            ?: return // Вложения (этап 6) в Outbox этого прохода не участвуют.

        val delivered = attemptDelivery(entry.contactId, AppMessage.Text(entry.messageLocalId, text).encode())

        if (delivered) {
            // "SENT" здесь означает только "TCP-сеанс с рукопожатием прошёл успешно" —
            // НЕ то же самое, что "доставлено" в смысле UI (✓). Статус DELIVERED
            // выставляется отдельно, при получении DELIVERED-ACK (см. B.5) — поэтому
            // запись Outbox можно спокойно удалить: повторная отправка САМОГО
            // сообщения больше не нужна независимо от того, придёт ACK или потеряется.
            messageRepository.updateStatus(entry.messageLocalId, MessageStatus.SENT)
            outboxRepository.remove(entry.messageLocalId)
        } else if (!forced) {
            val nextAttemptCount = entry.attemptCount + 1
            val delay = jitter.applyBackoff(RetryBackoff.nextDelayMillis(nextAttemptCount))
            outboxRepository.recordFailedAttempt(
                entry.messageLocalId,
                nextAttemptCount,
                nowEpochMillis + delay
            )
        }
    }
}
