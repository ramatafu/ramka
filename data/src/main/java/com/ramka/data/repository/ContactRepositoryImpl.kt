package com.ramka.data.repository

import android.util.Base64
import com.ramka.domain.model.*
import com.ramka.domain.repository.ContactRepository
import com.ramka.storage.db.ContactDao
import com.ramka.storage.db.ContactEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ContactRepositoryImpl(private val dao: ContactDao) : ContactRepository {

    override fun observeContacts(): Flow<List<Contact>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getContact(localId: String): Contact? =
        dao.getById(localId)?.toDomain()

    override suspend fun addContact(contact: Contact) {
        dao.upsert(contact.toEntity())
    }

    override suspend fun updateAlias(localId: String, alias: String) {
        dao.updateAlias(localId, alias)
    }

    override suspend fun updateLastKnownAddress(localId: String, address: NetworkAddress) {
        when (address) {
            is NetworkAddress.Lan -> dao.updateAddress(localId, "lan", address.host, address.port)
            is NetworkAddress.Relay -> dao.updateAddress(localId, "relay", address.relayUrl, null)
        }
    }

    override suspend fun markKeyChanged(localId: String, newPublicKey: ByteArray) {
        dao.updateKey(
            localId,
            Base64.encodeToString(newPublicKey, Base64.NO_WRAP),
            KeyVerificationStatus.KEY_CHANGED_WARNING.name
        )
    }

    override suspend fun removeContact(localId: String) {
        dao.delete(localId)
    }

    override suspend fun incrementUnreadCount(localId: String) {
        dao.incrementUnreadCount(localId)
    }

    override suspend fun resetUnreadCount(localId: String) {
        dao.resetUnreadCount(localId)
    }
}

private fun ContactEntity.toDomain(): Contact = Contact(
    localId = localId,
    alias = alias,
    publicKey = Base64.decode(publicKeyB64, Base64.NO_WRAP),
    signingPublicKey = Base64.decode(signingPublicKeyB64, Base64.NO_WRAP),
    lastKnownAddress = when (addressType) {
        "lan" -> addressHost?.let { NetworkAddress.Lan(it, addressPort ?: 0) }
        "relay" -> addressHost?.let { NetworkAddress.Relay(it, "") }
        else -> null
    },
    verification = KeyVerificationStatus.valueOf(verificationStatus),
    addedAtEpochDay = addedAtEpochDay,
    unreadCount = unreadCount
)

private fun Contact.toEntity(): ContactEntity = ContactEntity(
    localId = localId,
    alias = alias,
    publicKeyB64 = Base64.encodeToString(publicKey, Base64.NO_WRAP),
    signingPublicKeyB64 = Base64.encodeToString(signingPublicKey, Base64.NO_WRAP),
    addressType = when (lastKnownAddress) {
        is NetworkAddress.Lan -> "lan"
        is NetworkAddress.Relay -> "relay"
        null -> null
    },
    addressHost = when (val a = lastKnownAddress) {
        is NetworkAddress.Lan -> a.host
        is NetworkAddress.Relay -> a.relayUrl
        null -> null
    },
    addressPort = (lastKnownAddress as? NetworkAddress.Lan)?.port,
    verificationStatus = verification.name,
    addedAtEpochDay = addedAtEpochDay,
    unreadCount = unreadCount
)
