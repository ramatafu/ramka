package com.ramka.data.repository

import com.ramka.domain.model.Contact
import com.ramka.domain.model.NetworkAddress
import com.ramka.domain.repository.TransportRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge

/**
 * Выбирает транспорт для контакта: LAN напрямую или домашний relay (этап 3).
 *
 * Отправка:
 * - relay выключен или не настроен → ровно как до этапа 3: только LAN, если у контакта LAN-адрес;
 * - relay включён и устройство в локальной сети → сначала LAN (если у контакта LAN-адрес),
 *   при неудаче relay;
 * - relay включён, устройство вне локальной сети (мобильный интернет) → сначала relay, чтобы не
 *   ждать таймаут по заведомо недостижимому приватному адресу; LAN остаётся запасным вариантом
 *   (например, когда телефон сам раздаёт точку доступа, а «активной» сетью считается мобильная).
 *
 * Если у контакта нет LAN-адреса (или адрес relay), LAN не пробуется.
 * `false` оставляет сообщение в Outbox, дальше работает backoff.
 *
 * Приём: LAN-слушатель и relay-регистрация работают одновременно, потоки объединяются.
 *
 * @param relayEnabled включён и корректно настроен ли relay (читается на каждую отправку).
 * @param onLocalNetwork подключено ли устройство к локальной сети (Wi-Fi/Ethernet).
 */
class CompositeTransportRepository(
    private val lan: TransportRepository,
    private val relay: TransportRepository,
    private val relayEnabled: () -> Boolean,
    private val onLocalNetwork: () -> Boolean
) : TransportRepository {

    override suspend fun sendEncryptedPacket(contact: Contact, packet: ByteArray): Boolean {
        val hasLanAddress = contact.lastKnownAddress is NetworkAddress.Lan
        if (!relayEnabled()) {
            return hasLanAddress && lan.trySend(contact, packet)
        }
        return if (hasLanAddress && onLocalNetwork()) {
            lan.trySend(contact, packet) || relay.trySend(contact, packet)
        } else {
            relay.trySend(contact, packet) || (hasLanAddress && lan.trySend(contact, packet))
        }
    }

    /** Транспорт не должен бросать исключений; если всё же бросил — это «не вышло», а не падение приложения. */
    private suspend fun TransportRepository.trySend(contact: Contact, packet: ByteArray): Boolean =
        try {
            sendEncryptedPacket(contact, packet)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }

    override fun incomingPackets(): Flow<Pair<ByteArray, ByteArray>> =
        merge(lan.incomingPackets(), relay.incomingPackets())
}
