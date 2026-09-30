package com.ramka.storage.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Строка сообщения. localCreatedAtEpochMillis — только для отображения в UI
 * (с возможностью скрыть в настройках), никогда не покидает устройство как метаданные.
 *
 * remoteMessageId/readAckSent — см. Message.kt в domain-модуле (ЭТАП B.5, ACK).
 */
@Entity(
    tableName = "messages",
    indices = [Index("contactId")]
)
data class MessageEntity(
    @PrimaryKey val localId: String,
    val contactId: String,
    val direction: String, // OUTGOING | INCOMING
    val bodyType: String,  // TEXT (позже IMAGE)
    val bodyText: String?,
    val status: String,
    val localCreatedAtEpochMillis: Long,
    val deleteAfterReadSeconds: Long?,
    val remoteMessageId: String? = null,
    val readAckSent: Boolean = false
)

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE contactId = :contactId ORDER BY localCreatedAtEpochMillis ASC")
    fun observeForContact(contactId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE localId = :localId")
    suspend fun getById(localId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE contactId = :contactId AND status IN ('SENDING', 'FAILED')")
    suspend fun pendingForContact(contactId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE contactId = :contactId AND direction = 'INCOMING' AND readAckSent = 0")
    suspend fun incomingWithoutReadAck(contactId: String): List<MessageEntity>

    @Query("""
        SELECT * FROM messages WHERE contactId = :contactId AND direction = 'INCOMING'
        AND remoteMessageId = :remoteMessageId LIMIT 1
    """)
    suspend fun findIncomingByRemoteId(contactId: String, remoteMessageId: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE localId = :localId")
    suspend fun updateStatus(localId: String, status: String)

    @Query("UPDATE messages SET readAckSent = 1 WHERE localId = :localId")
    suspend fun markReadAckSent(localId: String)

    @Query("DELETE FROM messages WHERE localId = :localId")
    suspend fun delete(localId: String)
}
