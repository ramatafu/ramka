package com.ramka.app.yggmail

import com.ramka.domain.model.AppMessage
import com.ramka.domain.model.Contact
import com.ramka.domain.model.KeyVerificationStatus
import com.ramka.domain.util.OfflinePacketSealer
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YggmailTransportTest {
    private val id = "123e4567-e89b-12d3-a456-426614174000"
    private val textPacket = AppMessage.Text(id, "привет").encode()

    private class FakeMail(var sendResult: Result<Unit> = Result.success(Unit)) : YggmailMail {
        val sent = mutableListOf<Pair<String, ByteArray>>()
        override suspend fun send(config: YggmailConfig, recipient: String, payload: ByteArray): Result<Unit> {
            sent += recipient to payload
            return sendResult
        }
        override suspend fun receive(config: YggmailConfig, handler: suspend (String) -> Boolean): Result<Int> = Result.success(0)
        override suspend fun test(config: YggmailConfig): Result<Unit> = Result.success(Unit)
    }

    /** ТЕСТОВАЯ «схема»: просто разворачивает байты. Не шифр — нужна только чтобы проверить маршрут данных. */
    private class ReverseSealer(override val isAvailable: Boolean = true, val result: ByteArray? = null) : OfflinePacketSealer {
        override suspend fun seal(recipient: Contact, plaintext: ByteArray): ByteArray? =
            if (!isAvailable) null else result ?: plaintext.reversedArray()
        override suspend fun open(sealed: ByteArray): Pair<ByteArray, ByteArray>? = null
    }

    private val validConfig = YggmailConfig("200:db8::1", 1025, "200:db8::1", 1143, "me@yggmail", "pw")
    private val configSource = object : YggmailConfigSource { override fun load(): YggmailConfig? = validConfig }
    private val noConfig = object : YggmailConfigSource { override fun load(): YggmailConfig? = null }

    private fun contact(address: String?) = Contact(
        localId = "c", alias = "a", publicKey = ByteArray(32), signingPublicKey = ByteArray(32),
        lastKnownAddress = null, verification = KeyVerificationStatus.UNVERIFIED, addedAtEpochDay = 0,
        yggmailAddress = address
    )

    private val mailbox = "a".repeat(64) + "@yggmail"

    private fun transport(
        mail: FakeMail = FakeMail(),
        sealer: OfflinePacketSealer = ReverseSealer(),
        config: YggmailConfigSource = configSource,
        enabled: Boolean = true
    ) = YggmailTransport(config, mail, sealer, emptyFlow(), { enabled })

    private fun send(t: YggmailTransport, c: Contact, packet: ByteArray = textPacket) =
        runBlocking { t.sendEncryptedPacket(c, packet) }

    @Test fun sendsSealedPayloadToMailbox() {
        val mail = FakeMail()
        assertTrue(send(transport(mail), contact(mailbox)))
        assertEquals(1, mail.sent.size)
        assertEquals(mailbox, mail.sent[0].first)
        assertArrayEquals(textPacket.reversedArray(), mail.sent[0].second) // ушло запечатанное, не открытое
    }

    @Test fun neverSendsPlaintext() {
        val mail = FakeMail()
        send(transport(mail), contact(mailbox))
        assertFalse(mail.sent.any { it.second.contentEquals(textPacket) })
    }

    @Test fun disabledChannelSendsNothing() {
        val mail = FakeMail()
        assertFalse(send(transport(mail, enabled = false), contact(mailbox)))
        assertTrue(mail.sent.isEmpty())
    }

    @Test fun unavailableSealerSendsNothing() {
        val mail = FakeMail()
        assertFalse(send(transport(mail, sealer = ReverseSealer(isAvailable = false)), contact(mailbox)))
        assertTrue(mail.sent.isEmpty())
    }

    @Test fun missingConfigSendsNothing() {
        val mail = FakeMail()
        assertFalse(send(transport(mail, config = noConfig), contact(mailbox)))
        assertTrue(mail.sent.isEmpty())
    }

    @Test fun contactWithoutMailboxSendsNothing() {
        val mail = FakeMail()
        assertFalse(send(transport(mail), contact(null)))
        assertFalse(send(transport(mail), contact("324:71e::53")))      // только IPv6 для прямого канала
        assertFalse(send(transport(mail), contact("user@example.com"))) // чужой домен
        assertTrue(mail.sent.isEmpty())
    }

    @Test fun callsAndFileChunksAreNotSentByMail() {
        val mail = FakeMail()
        assertFalse(send(transport(mail), contact(mailbox), AppMessage.CallOffer(id, "v=0", false).encode()))
        assertFalse(send(transport(mail), contact(mailbox), AppMessage.FileChunk(id, 0, ByteArray(500)).encode()))
        assertTrue(mail.sent.isEmpty())
    }

    @Test fun sealerFailureSendsNothing() {
        val mail = FakeMail()
        val nullSealer = object : OfflinePacketSealer {
            override val isAvailable = true
            override suspend fun seal(recipient: Contact, plaintext: ByteArray): ByteArray? = null
            override suspend fun open(sealed: ByteArray): Pair<ByteArray, ByteArray>? = null
        }
        assertFalse(send(transport(mail, sealer = nullSealer), contact(mailbox)))
        assertTrue(mail.sent.isEmpty())
    }

    @Test fun oversizedSealedPayloadIsRefused() {
        val mail = FakeMail()
        val huge = ReverseSealer(result = ByteArray(YggmailPayload.MAX_SEALED_BYTES + 1))
        assertFalse(send(transport(mail, sealer = huge), contact(mailbox)))
        assertTrue(mail.sent.isEmpty())
    }

    @Test fun mailFailureIsAnOrdinaryFalse() {
        val mail = FakeMail(Result.failure(java.net.ConnectException("down")))
        assertFalse(send(transport(mail), contact(mailbox)))
    }

    @Test fun throwingMailClientIsContained() {
        val boom = object : YggmailMail {
            override suspend fun send(config: YggmailConfig, recipient: String, payload: ByteArray): Result<Unit> = throw IllegalStateException("boom")
            override suspend fun receive(config: YggmailConfig, handler: suspend (String) -> Boolean): Result<Int> = Result.success(0)
            override suspend fun test(config: YggmailConfig): Result<Unit> = Result.success(Unit)
        }
        assertFalse(send(YggmailTransport(configSource, boom, ReverseSealer(), emptyFlow(), { true }), contact(mailbox)))
    }

    @Test fun canDeliverMatchesSendPreconditions() {
        assertTrue(transport().canDeliver(contact(mailbox)))
        assertFalse(transport().canDeliver(contact(null)))
        assertFalse(transport(enabled = false).canDeliver(contact(mailbox)))
        assertFalse(transport(sealer = ReverseSealer(isAvailable = false)).canDeliver(contact(mailbox)))
        assertFalse(transport(config = noConfig).canDeliver(contact(mailbox)))
    }
}
