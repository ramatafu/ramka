package com.ramka.data.repository

import com.ramka.domain.model.OutboxEntry
import com.ramka.domain.repository.OutboxRepository
import com.ramka.storage.db.OutboxDao
import com.ramka.storage.db.OutboxEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OutboxRepositoryImpl(private val dao: OutboxDao) : OutboxRepository {

    override fun observePending(): Flow<List<OutboxEntry>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun enqueue(entry: OutboxEntry) {
        dao.upsert(entry.toEntity())
    }

    override suspend fun due(nowEpochMillis: Long): List<OutboxEntry> =
        dao.due(nowEpochMillis).map { it.toDomain() }

    override suspend fun recordFailedAttempt(messageLocalId: String, attemptCount: Int, nextAttemptAtEpochMillis: Long) {
        dao.recordFailedAttempt(messageLocalId, attemptCount, nextAttemptAtEpochMillis)
    }

    override suspend fun remove(messageLocalId: String) {
        dao.remove(messageLocalId)
    }
}

private fun OutboxEntity.toDomain() = OutboxEntry(
    messageLocalId = messageLocalId,
    contactId = contactId,
    attemptCount = attemptCount,
    nextAttemptAtEpochMillis = nextAttemptAtEpochMillis,
    createdAtEpochMillis = createdAtEpochMillis
)

private fun OutboxEntry.toEntity() = OutboxEntity(
    messageLocalId = messageLocalId,
    contactId = contactId,
    attemptCount = attemptCount,
    nextAttemptAtEpochMillis = nextAttemptAtEpochMillis,
    createdAtEpochMillis = createdAtEpochMillis
)
