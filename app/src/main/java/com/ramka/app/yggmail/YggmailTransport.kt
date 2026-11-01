package com.ramka.app.yggmail

import com.ramka.domain.model.Contact
import com.ramka.domain.repository.TransportRepository
import com.ramka.domain.util.OfflineEligibility
import com.ramka.domain.util.OfflinePacketSealer
import com.ramka.domain.util.YggmailMailbox
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * Необязательный канал store-and-forward: пакет запечатывается для получателя ([OfflinePacketSealer]), кодируется
 * в Base64 и уходит письмом «Ramka-E2EE» через SMTP; приём — [YggmailInbox].
 *
 * Изоляция: канал работает, только если он включён, настроен, схема шифрования есть, у контакта есть почтовый
 * адрес Yggmail, а пакет короткий и подходит для почты (текст/подтверждения). Во всех остальных случаях, как и при
 * любом сбое, [sendEncryptedPacket] возвращает false и не бросает исключений — для остального приложения это
 * «канала нет». Открытый текст по почте не уходит никогда: без запечатывания отправка не происходит.
 */
class YggmailTransport(
    private val configSource: YggmailConfigSource,
    private val mail: YggmailMail,
    private val sealer: OfflinePacketSealer,
    private val incoming: Flow<Pair<ByteArray, ByteArray>>,
    private val enabled: () -> Boolean,
    private val log: (String) -> Unit = {}
) : TransportRepository {

    /** Локальная проверка без обращения к серверу — для прогноза канала в UI. */
    fun canDeliver(contact: Contact): Boolean = try {
        enabled() && sealer.isAvailable && YggmailMailbox.recipientFor(contact) != null &&
            configSource.load()?.isValid() == true
    } catch (e: Exception) {
        false
    }

    override suspend fun sendEncryptedPacket(contact: Contact, packet: ByteArray): Boolean {
        try {
            if (!enabled() || !sealer.isAvailable) return false
            if (!OfflineEligibility.isEligible(packet)) return false
            val recipient = YggmailMailbox.recipientFor(contact) ?: return false
            val config = configSource.load()?.takeIf { it.isValid() } ?: return false
            val sealed = sealer.seal(contact, packet) ?: return false
            if (sealed.isEmpty() || sealed.size > YggmailPayload.MAX_SEALED_BYTES) return false
            return mail.send(config, recipient, sealed).isSuccess
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Yggmail send: ${e.javaClass.simpleName}")
            return false
        } catch (e: LinkageError) {
            log("Yggmail send: ${e.javaClass.simpleName}")
            return false
        }
    }

    override fun incomingPackets(): Flow<Pair<ByteArray, ByteArray>> = incoming
}
