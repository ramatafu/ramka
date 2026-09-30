package com.ramka.data.repository

import com.ramka.domain.model.Contact
import com.ramka.domain.model.NetworkAddress
import com.ramka.domain.repository.TransportRepository
import com.ramka.network.local.SecureLanChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Тонкий адаптер domain.TransportRepository поверх [SecureLanChannel] (этап 1 —
 * только прямое LAN-соединение, см. п. 3.6/3.7; интернет-режим — этап 4).
 *
 * Всё рукопожатие, аутентификация и шифрование уже выполнены внутри
 * [SecureLanChannel] — сюда попадает и отсюда уходит только открытый текст.
 */
class LanTransportRepository(
    private val secureLanChannel: SecureLanChannel,
    private val serverScope: CoroutineScope
) : TransportRepository {

    override suspend fun sendEncryptedPacket(contact: Contact, packet: ByteArray): Boolean {
        val address = contact.lastKnownAddress as? NetworkAddress.Lan ?: return false
        return secureLanChannel.sendMessage(
            host = address.host,
            port = address.port,
            remoteStaticX25519 = contact.publicKey,
            remoteSigningPublicKey = contact.signingPublicKey,
            plaintext = packet
        )
    }

    override fun incomingPackets(): Flow<Pair<ByteArray, ByteArray>> =
        secureLanChannel.incomingMessages(serverScope).map { it.senderStaticX25519 to it.plaintext }
}
