package com.ramka.domain.usecase

import com.ramka.domain.model.AppMessage
import com.ramka.domain.model.MessageBody
import com.ramka.domain.model.MessageStatus
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
    suspend fun processDue(nowEpochMillis: Long) {
        val dueEntries = outboxRepository.due(nowEpochMillis)

        for (entry in dueEntries) {
            val message = messageRepository.getMessage(entry.messageLocalId)
            if (message == null) {
                // Сообщение удалено пользователем (или иначе исчезло) — ретраить нечего.
                outboxRepository.remove(entry.messageLocalId)
                continue
            }

            val text = (message.body as? MessageBody.Text)?.text
            if (text == null) {
                // Вложения (этап 6) в Outbox этого прохода не участвуют.
                continue
            }

            val delivered = attemptDelivery(entry.contactId, AppMessage.Text(entry.messageLocalId, text).encode())

            if (delivered) {
                // "SENT" здесь означает только "TCP-сеанс с рукопожатием прошёл успешно" —
                // НЕ то же самое, что "доставлено" в смысле UI (✓). Статус DELIVERED
                // выставляется отдельно, при получении DELIVERED-ACK (см. B.5) — поэтому
                // запись Outbox можно спокойно удалить: повторная отправка САМОГО
                // сообщения больше не нужна независимо от того, придёт ACK или потеряется.
                messageRepository.updateStatus(entry.messageLocalId, MessageStatus.SENT)
                outboxRepository.remove(entry.messageLocalId)
            } else {
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
}
