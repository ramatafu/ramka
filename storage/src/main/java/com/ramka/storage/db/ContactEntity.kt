package com.ramka.storage.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Строка контакта в локальной (зашифрованной SQLCipher) БД.
 * Оба публичных ключа и адрес хранятся в Base64 — сами по себе не секрет
 * (это уже публичные данные собеседника), но БД целиком зашифрована.
 */
@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val localId: String,
    val alias: String,
    val publicKeyB64: String,
    val signingPublicKeyB64: String,
    val addressType: String?, // "lan" | "relay" | null
    val addressHost: String?,
    val addressPort: Int?,
    val verificationStatus: String,
    val addedAtEpochDay: Long,
    val unreadCount: Int = 0
)

@Dao
interface ContactDao {
    // Контакты с непрочитанными — вверху списка (UX-правка); внутри каждой группы — по алфавиту.
    @Query("SELECT * FROM contacts ORDER BY (unreadCount > 0) DESC, alias COLLATE NOCASE")
    fun observeAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE localId = :localId")
    suspend fun getById(localId: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ContactEntity)

    @Query("UPDATE contacts SET alias = :alias WHERE localId = :localId")
    suspend fun updateAlias(localId: String, alias: String)

    @Query("""
        UPDATE contacts SET addressType = :type, addressHost = :host, addressPort = :port
        WHERE localId = :localId
    """)
    suspend fun updateAddress(localId: String, type: String, host: String?, port: Int?)

    @Query("UPDATE contacts SET publicKeyB64 = :publicKeyB64, verificationStatus = :status WHERE localId = :localId")
    suspend fun updateKey(localId: String, publicKeyB64: String, status: String)

    @Query("UPDATE contacts SET unreadCount = unreadCount + 1 WHERE localId = :localId")
    suspend fun incrementUnreadCount(localId: String)

    @Query("UPDATE contacts SET unreadCount = 0 WHERE localId = :localId")
    suspend fun resetUnreadCount(localId: String)

    @Query("DELETE FROM contacts WHERE localId = :localId")
    suspend fun delete(localId: String)
}
