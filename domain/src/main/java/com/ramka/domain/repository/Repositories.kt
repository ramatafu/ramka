package com.ramka.domain.repository

import com.ramka.domain.model.Contact
import com.ramka.domain.model.Message
import kotlinx.coroutines.flow.Flow

/** Интерфейс работы со списком контактов. Реализация — только локальное зашифрованное хранилище. */
interface ContactRepository {
    fun observeContacts(): Flow<List<Contact>>
    suspend fun getContact(localId: String): Contact?
    suspend fun addContact(contact: Contact)
    suspend fun updateAlias(localId: String, alias: String)
    suspend fun updateLastKnownAddress(localId: String, address: com.ramka.domain.model.NetworkAddress)
    suspend fun markKeyChanged(localId: String, newPublicKey: ByteArray)
    suspend fun removeContact(localId: String)
    /** Новое входящее сообщение от контакта, счётчик +1 (UX-badge в списке контактов). */
    suspend fun incrementUnreadCount(localId: String)
    /** Открытие чата с контактом — счётчик обнуляется. */
    suspend fun resetUnreadCount(localId: String)
}

/** Интерфейс работы с сообщениями и очередью недоставленных. */
interface MessageRepository {
    fun observeMessages(contactId: String): Flow<List<Message>>
    suspend fun getMessage(localId: String): Message?
    suspend fun saveOutgoing(message: Message)
    suspend fun saveIncoming(message: Message)
    suspend fun updateStatus(localId: String, status: com.ramka.domain.model.MessageStatus)
    suspend fun markReadAckSent(localId: String)
    suspend fun deleteMessage(localId: String)
    suspend fun pendingForContact(contactId: String): List<Message>
    /** Входящие сообщения этого контакта, за которые ещё не отправлен ReadAck (см. ЭТАП B.5). */
    suspend fun incomingWithoutReadAck(contactId: String): List<Message>
    /** Уже сохранённое входящее сообщение с таким remoteMessageId — для идемпотентности приёма (дубликат не создаёт вторую запись). */
    suspend fun findIncomingByRemoteId(contactId: String, remoteMessageId: String): Message?
}

/** Абстракция транспорта: не знает про UI и БД, только отправляет/принимает зашифрованные пакеты. */
interface TransportRepository {
    suspend fun sendEncryptedPacket(contact: Contact, packet: ByteArray): Boolean
    fun incomingPackets(): Flow<Pair<ByteArray, ByteArray>> // (senderPublicKey, packet)
}

/**
 * Очередь недоставленных сообщений (Outbox, §4.5). Переживает перезапуск процесса —
 * реализация обязана хранить записи в той же зашифрованной БД, что и сами сообщения.
 */
interface OutboxRepository {
    fun observePending(): Flow<List<com.ramka.domain.model.OutboxEntry>>
    suspend fun enqueue(entry: com.ramka.domain.model.OutboxEntry)
    /** Записи, чьё время следующей попытки уже наступило (nextAttemptAtEpochMillis <= now). */
    suspend fun due(nowEpochMillis: Long): List<com.ramka.domain.model.OutboxEntry>
    /** Попытка не удалась — увеличить счётчик и назначить следующее время по backoff. */
    suspend fun recordFailedAttempt(messageLocalId: String, attemptCount: Int, nextAttemptAtEpochMillis: Long)
    /** Попытка удалась (сообщение ушло по сети) — запись больше не нужна в очереди. */
    suspend fun remove(messageLocalId: String)
}
