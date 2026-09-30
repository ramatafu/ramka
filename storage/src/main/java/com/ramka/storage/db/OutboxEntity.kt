package com.ramka.storage.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Строка очереди недоставленных сообщений (Outbox, §4.5). Внешний ключ на
 * MessageEntity — при удалении сообщения запись Outbox удаляется каскадно
 * (иначе можно было бы бесконечно ретраить сообщение, которого уже нет).
 */
@Entity(
    tableName = "outbox",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["localId"],
            childColumns = ["messageLocalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("messageLocalId", unique = true), Index("contactId")]
)
data class OutboxEntity(
    @PrimaryKey val messageLocalId: String,
    val contactId: String,
    val attemptCount: Int,
    val nextAttemptAtEpochMillis: Long,
    val createdAtEpochMillis: Long
)

@Dao
interface OutboxDao {
    @Query("SELECT * FROM outbox")
    fun observeAll(): Flow<List<OutboxEntity>>

    @Query("SELECT * FROM outbox WHERE nextAttemptAtEpochMillis <= :nowEpochMillis")
    suspend fun due(nowEpochMillis: Long): List<OutboxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OutboxEntity)

    @Query("""
        UPDATE outbox SET attemptCount = :attemptCount, nextAttemptAtEpochMillis = :nextAttemptAtEpochMillis
        WHERE messageLocalId = :messageLocalId
    """)
    suspend fun recordFailedAttempt(messageLocalId: String, attemptCount: Int, nextAttemptAtEpochMillis: Long)

    @Query("DELETE FROM outbox WHERE messageLocalId = :messageLocalId")
    suspend fun remove(messageLocalId: String)
}
