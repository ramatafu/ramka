package com.ramka.domain.usecase

import com.ramka.domain.model.Contact
import com.ramka.domain.model.KeyVerificationStatus
import com.ramka.domain.model.NetworkAddress
import com.ramka.domain.repository.ContactRepository
import java.util.UUID

/**
 * Разбирает содержимое одноразового QR-приглашения и создаёт локальную запись контакта.
 * Приглашение содержит оба долговременных публичных ключа контакта (X25519 для обмена
 * ключами, Ed25519 для проверки подписи рукопожатия) и временный адрес (см. Invite).
 */
class AddContactUseCase(
    private val contactRepository: ContactRepository
) {
    suspend operator fun invoke(invite: Invite, alias: String): Contact {
        val contact = Contact(
            localId = UUID.randomUUID().toString(),
            alias = alias,
            publicKey = invite.publicKey,
            signingPublicKey = invite.signingPublicKey,
            lastKnownAddress = invite.address,
            verification = KeyVerificationStatus.VERIFIED_BY_QR,
            addedAtEpochDay = System.currentTimeMillis() / 86_400_000L
        )
        contactRepository.addContact(contact)
        return contact
    }
}

/** Данные, закодированные в QR-приглашении. Живёт ограниченное время (см. [expiresAtEpochMillis]). */
data class Invite(
    val publicKey: ByteArray,
    val signingPublicKey: ByteArray,
    val address: NetworkAddress?,
    val expiresAtEpochMillis: Long,
    val nonce: ByteArray
) {
    fun isExpired(): Boolean = System.currentTimeMillis() > expiresAtEpochMillis
}
