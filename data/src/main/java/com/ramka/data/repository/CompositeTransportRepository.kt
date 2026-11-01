package com.ramka.data.repository

import com.ramka.domain.model.Contact
import com.ramka.domain.model.NetworkAddress
import com.ramka.domain.model.TransportChannel
import com.ramka.domain.repository.TransportRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.merge

/**
 * Выбирает транспорт для контакта: LAN напрямую, Yggdrasil или домашний relay (этап 3).
 *
 * Порядок каналов (при неудаче — следующий):
 * 1. локальная сеть (LAN/mDNS) — если у контакта LAN-адрес и устройство в локальной сети (либо relay выключен);
 * 2. Yggdrasil — если у контакта есть Yggmail-адрес и Yggdrasil включён ([yggdrasilEnabled]);
 * 3. relay — последний резерв (если включён);
 * 4. LAN как запасной вариант, когда устройство не в локальной сети (например, раздаёт точку доступа:
 *    «активной» сетью считается мобильная).
 * 5. Yggmail (почта в сети Yggdrasil, store-and-forward) — самый последний резерв, только если все прямые каналы
 *    и relay не сработали. Полностью изолирован: любой сбой там (включая отсутствие классов JavaMail) — просто «не вышло».
 * Без Yggdrasil порядок и поведение ровно прежние: relay выключен → только LAN; relay включён и мы в локальной
 * сети → LAN, затем relay; иначе relay, затем LAN.
 *
 * Если у контакта нет LAN-адреса (или адрес relay), LAN не пробуется; без Yggmail-адреса не пробуется Yggdrasil.
 * `false` оставляет сообщение в Outbox, дальше работает backoff.
 *
 * Приём: LAN-слушатель и relay-регистрация работают одновременно, потоки объединяются.
 *
 * @param relayEnabled включён и корректно настроен ли relay (читается на каждую отправку).
 * @param onLocalNetwork подключено ли устройство к локальной сети (Wi-Fi/Ethernet).
 * @param ygg транспорт Yggdrasil (null — функции нет); приём через него отдельно не нужен (общий слушатель LAN).
 * @param yggdrasilEnabled включён ли Yggdrasil в настройках (читается на каждую отправку).
 * @param onChannelUsed сообщает, каким каналом ушёл пакет (null — не ушёл никак); нужен для индикации в чате.
 * @param yggmail транспорт Yggmail (null — функции нет); пробуется последним.
 * @param yggmailEnabled включён ли Yggmail в настройках (читается на каждую отправку).
 */
class CompositeTransportRepository(
    private val lan: TransportRepository,
    private val relay: TransportRepository,
    private val relayEnabled: () -> Boolean,
    private val onLocalNetwork: () -> Boolean,
    private val ygg: TransportRepository? = null,
    private val yggdrasilEnabled: () -> Boolean = { false },
    private val onChannelUsed: (contactId: String, channel: TransportChannel?) -> Unit = { _, _ -> },
    private val yggmail: TransportRepository? = null,
    private val yggmailEnabled: () -> Boolean = { false }
) : TransportRepository {

    override suspend fun sendEncryptedPacket(contact: Contact, packet: ByteArray): Boolean {
        val hasLanAddress = contact.lastKnownAddress is NetworkAddress.Lan
        val relayOn = relayEnabled()
        // LAN первым: relay выключен (как до этапа 3) либо мы в локальной сети; иначе LAN — запасной в конце.
        val lanFirst = hasLanAddress && (!relayOn || onLocalNetwork())
        val yggOn = ygg != null && !contact.yggmailAddress.isNullOrBlank() && yggdrasilEnabled()

        if (lanFirst && lan.trySend(contact, packet)) return used(contact, TransportChannel.LOCAL_WIFI)
        if (yggOn && ygg!!.trySend(contact, packet)) return used(contact, TransportChannel.YGGDRASIL)
        if (relayOn && relay.trySend(contact, packet)) return used(contact, TransportChannel.RELAY)
        if (!lanFirst && hasLanAddress && lan.trySend(contact, packet)) return used(contact, TransportChannel.LOCAL_WIFI)
        if (yggmail != null && yggmailEnabled() && yggmail.trySendIsolated(contact, packet)) return used(contact, TransportChannel.YGGMAIL)
        onChannelUsed(contact.localId, null)
        return false
    }

    private fun used(contact: Contact, channel: TransportChannel): Boolean {
        onChannelUsed(contact.localId, channel)
        return true
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

    /**
     * Для Yggmail ловим и ошибки загрузки классов (`LinkageError`, например если JavaMail вырезан сборкой): падение
     * необязательного канала не должно ронять отправку и приложение. Отмену корутины пробрасываем.
     */
    private suspend fun TransportRepository.trySendIsolated(contact: Contact, packet: ByteArray): Boolean =
        try {
            sendEncryptedPacket(contact, packet)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        } catch (e: LinkageError) {
            false
        }

    override fun incomingPackets(): Flow<Pair<ByteArray, ByteArray>> {
        val direct = merge(lan.incomingPackets(), relay.incomingPackets()) // Yggdrasil принимает тот же слушатель, что и LAN
        val mail = yggmail ?: return direct
        // .catch: сбой потока Yggmail не должен обрывать приём по LAN и relay (merge отменяется целиком при ошибке одного).
        return merge(direct, mail.incomingPackets().catch { })
    }
}
