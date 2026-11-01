package com.ramka.data.repository

import com.ramka.domain.model.Contact
import com.ramka.domain.repository.TransportRepository
import com.ramka.domain.util.YggdrasilAddress
import com.ramka.network.local.SecureLanChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Отправка контакту по сети Yggdrasil: тот же [SecureLanChannel] (Noise IK, те же ключи и формат кадров), но
 * адрес берётся из `Contact.yggmailAddress` — обычный TCP к порту Ramka собеседника через системный интерфейс
 * Yggdrasil. Рукопожатие по-прежнему проверяет статические ключи контакта, так что подмена адреса не даёт
 * подслушать или выдать себя за собеседника.
 *
 * Приём отдельно не нужен: LAN-слушатель привязан к всем интерфейсам, включая Yggdrasil, и его пакеты
 * уже попадают в общий поток через [LanTransportRepository], поэтому [incomingPackets] пуст.
 *
 * @param usable можно ли сейчас пробовать Yggdrasil (включено в настройках и интерфейс есть) — читается на
 *   каждую отправку; без интерфейса соединение просто повисло бы до таймаута.
 */
class YggdrasilTransportRepository(
    private val secureLanChannel: SecureLanChannel,
    private val port: Int,
    private val usable: () -> Boolean = { true }
) : TransportRepository {

    override suspend fun sendEncryptedPacket(contact: Contact, packet: ByteArray): Boolean {
        if (!usable()) return false
        // Адрес проверяется здесь же: в соединение уходит только литерал из 200::/7, не произвольный хост.
        val host = YggdrasilAddress.parse(contact.yggmailAddress.orEmpty()) ?: return false
        return secureLanChannel.sendMessage(
            host = host,
            port = port,
            remoteStaticX25519 = contact.publicKey,
            remoteSigningPublicKey = contact.signingPublicKey,
            plaintext = packet
        )
    }

    override fun incomingPackets(): Flow<Pair<ByteArray, ByteArray>> = emptyFlow()
}
