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
    val bodyType: String,  // TEXT | FILE
    val bodyText: String?,
    val status: String,
    val localCreatedAtEpochMillis: Long,
    val deleteAfterReadSeconds: Long?,
    val remoteMessageId: String? = null,
    val readAckSent: Boolean = false,
    // Только для bodyType = FILE (этап 6.4, миграция 3 -> 4); у TEXT — null / 0.
    val fileName: String? = null,
    val fileSize: Long? = null,
    val fileMimeType: String? = null,
    val fileUri: String? = null,
    val fileProgress: Int = 0
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

    @Query("UPDATE messages SET fileProgress = :progress WHERE localId = :localId")
    suspend fun updateFileProgress(localId: String, progress: Int)

    @Query("UPDATE messages SET fileUri = :uri WHERE localId = :localId")
    suspend fun updateFileUri(localId: String, uri: String)

    @Query("UPDATE messages SET status = 'FAILED' WHERE bodyType = 'FILE' AND status = 'SENDING'")
    suspend fun failInterruptedFileTransfers()

    @Query("DELETE FROM messages WHERE localId = :localId")
    suspend fun delete(localId: String)
}
