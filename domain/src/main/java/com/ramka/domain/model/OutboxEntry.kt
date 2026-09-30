package com.ramka.domain.model

/**
 * Запись очереди недоставленных сообщений (Outbox, §4.5 PROJECT_SPEC.md).
 *
 * Существует, пока попытка доставки исходного сообщения не завершилась успехом
 * хотя бы один раз (успешный TCP-сеанс с полным IK-рукопожатием — см. D-3/D-6
 * в DEVIATIONS.md, инвариант не снимается). После первой успешной передачи
 * запись удаляется из Outbox — дальнейшее подтверждение "доставлено"/"прочитано"
 * идёт отдельным механизмом ACK (см. AppMessage.DeliveredAck/ReadAck) и уже не
 * влияет на повторные попытки САМОГО сообщения (иначе потерянный ACK приводил
 * бы к бесконечной повторной отправке дублей текста).
 *
 * Никаких точных временных меток не передаётся по сети — здесь они только
 * локальные, для планирования retry (см. RetryBackoff).
 */
data class OutboxEntry(
    val messageLocalId: String,
    val contactId: String,
    val attemptCount: Int,
    val nextAttemptAtEpochMillis: Long,
    val createdAtEpochMillis: Long
)
