package com.ramka.domain.usecase

import com.ramka.domain.repository.ContactRepository
import com.ramka.domain.repository.TransportRepository

/**
 * Единая точка попытки доставки: и первая отправка ([SendMessageUseCase]), и
 * повторная отправка из очереди ([ProcessOutboxUseCase]) идут через один и тот же
 * путь — TransportRepository.sendEncryptedPacket, который сам делает полное
 * IK-рукопожатие на каждую попытку (D-3/D-6 — не снимается, см. DEVIATIONS.md).
 * Никакого "быстрого" пути в обход рукопожатия здесь и не может появиться,
 * потому что это единственная функция, которая вообще обращается к транспорту.
 */
class AttemptDeliveryUseCase(
    private val contactRepository: ContactRepository,
    private val transportRepository: TransportRepository
) {
    suspend operator fun invoke(contactId: String, payload: ByteArray): Boolean {
        val contact = contactRepository.getContact(contactId) ?: return false
        return transportRepository.sendEncryptedPacket(contact, payload)
    }
}
