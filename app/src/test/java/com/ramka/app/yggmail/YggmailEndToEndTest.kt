package com.ramka.app.yggmail

import com.ramka.app.offline.NoiseXOfflineSealer
import com.ramka.crypto.keys.KeyManager
import com.ramka.crypto.keys.KeyValueStore
import com.ramka.domain.model.AppMessage
import com.ramka.domain.model.Contact
import com.ramka.domain.model.KeyVerificationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Весь путь без сети: Алиса запечатывает и «отправляет письмо», почтовый ящик Боба отдаёт его Бобу, тот вскрывает.
 * Почтовый сервер заменён общей очередью; шифрование настоящее.
 */
class YggmailEndToEndTest {
    private class MemoryStore : KeyValueStore {
        private val map = mutableMapOf<String, ByteArray>()
        override fun getBytes(key: String): ByteArray? = map[key]?.copyOf()
        override fun putBytes(key: String, value: ByteArray) { map[key] = value.copyOf() }
    }

    /** Общий «почтовый сервер»: send кладёт письмо, receive отдаёт все письма обработчику. */
    private class SharedMailbox : YggmailMail {
        val letters = mutableListOf<ByteArray>()
        val markedRead = mutableListOf<Boolean>()
        override suspend fun send(config: YggmailConfig, recipient: String, payload: ByteArray): Result<Unit> {
            letters += payload
            return Result.success(Unit)
        }
        override suspend fun receive(config: YggmailConfig, handler: suspend (String) -> Boolean): Result<Int> {
            letters.forEach { markedRead += handler(YggmailPayload.encode(it)) }
            return Result.success(markedRead.count { it })
        }
        override suspend fun test(config: YggmailConfig): Result<Unit> = Result.success(Unit)
    }

    private val config = YggmailConfig("200:db8::1", 1025, "200:db8::1", 1143, "me@yggmail", "pw")
    private val source = object : YggmailConfigSource { override fun load(): YggmailConfig? = config }

    private val aliceKeys = KeyManager(MemoryStore())
    private val bobKeys = KeyManager(MemoryStore())
    private val carolKeys = KeyManager(MemoryStore())
    private val mailbox = "b".repeat(64) + "@yggmail"

    private fun contactFor(keys: KeyManager): Contact {
        val identity = keys.getOrCreateIdentity()
        return Contact(
            localId = "bob", alias = "Bob", publicKey = identity.x25519Public, signingPublicKey = identity.ed25519Public,
            lastKnownAddress = null, verification = KeyVerificationStatus.VERIFIED_BY_QR, addedAtEpochDay = 0,
            yggmailAddress = mailbox
        )
    }

    private val textId = "123e4567-e89b-12d3-a456-426614174000"
    private val packet = AppMessage.Text(textId, "привет через почту").encode()

    private fun aliceTransport(mail: YggmailMail) =
        YggmailTransport(source, mail, NoiseXOfflineSealer(aliceKeys), emptyFlow(), { true })

    private fun inboxOf(keys: KeyManager, mail: YggmailMail) =
        YggmailInbox(source, mail, NoiseXOfflineSealer(keys), { true }, subscriberWaitMs = 0L)

    @Test fun letterSentByAliceIsReadByBobAndAttributedToAlice() = runBlocking {
        val server = SharedMailbox()
        assertTrue(aliceTransport(server).sendEncryptedPacket(contactFor(bobKeys), packet))
        assertEquals(1, server.letters.size)

        val inbox = inboxOf(bobKeys, server)
        val received = mutableListOf<Pair<ByteArray, ByteArray>>()
        val job = launch(Dispatchers.Unconfined) { inbox.packets.collect { received += it } }
        inbox.pollOnce()
        job.cancel()

        assertEquals(1, received.size)
        assertArrayEquals(aliceKeys.getOrCreateIdentity().x25519Public, received[0].first)
        assertArrayEquals(packet, received[0].second)
        val decoded = AppMessage.decode(received[0].second) as AppMessage.Text
        assertEquals("привет через почту", decoded.text)
        assertEquals(listOf(true), server.markedRead)
    }

    @Test fun serverNeverSeesPlaintextOrSenderKey() = runBlocking {
        val server = SharedMailbox()
        aliceTransport(server).sendEncryptedPacket(contactFor(bobKeys), packet)
        val body = YggmailPayload.encode(server.letters[0])
        val raw = server.letters[0]
        assertFalse(String(raw, Charsets.ISO_8859_1).contains("привет"))
        assertFalse(body.contains("привет"))
        val alicePublic = aliceKeys.getOrCreateIdentity().x25519Public
        assertFalse(raw.toList().windowed(alicePublic.size).any { it.toByteArray().contentEquals(alicePublic) })
    }

    @Test fun someoneElsesInboxCannotReadItButConsumesTheLetter() = runBlocking {
        val server = SharedMailbox()
        aliceTransport(server).sendEncryptedPacket(contactFor(bobKeys), packet)

        val carolInbox = inboxOf(carolKeys, server)
        val received = mutableListOf<Pair<ByteArray, ByteArray>>()
        val job = launch(Dispatchers.Unconfined) { carolInbox.packets.collect { received += it } }
        carolInbox.pollOnce()
        job.cancel()

        assertTrue(received.isEmpty())              // Кэрол ничего не прочитала
        assertEquals(listOf(true), server.markedRead) // невскрываемое письмо отброшено и помечено прочитанным
    }

    @Test fun sealedPacketsStayWithinTransportLimits() = runBlocking {
        val server = SharedMailbox()
        val biggest = AppMessage.Text(textId, "я".repeat(3000)).encode() // длинное сообщение: ~6 КБ, корзина 8 КБ
        aliceTransport(server).sendEncryptedPacket(contactFor(bobKeys), biggest)
        assertTrue(server.letters.single().size <= YggmailPayload.MAX_SEALED_BYTES)
        assertTrue(YggmailPayload.encode(server.letters.single()).length <= YggmailPayload.MAX_BODY_CHARS)
    }
}
