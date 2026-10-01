package com.ramka.crypto.noise

import com.ramka.crypto.keys.DeviceIdentity
import com.ramka.crypto.keys.Ed25519Verification
import com.ramka.crypto.keys.InMemoryKeyValueStore
import com.ramka.crypto.keys.KeyManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IkHandshakeTest {

    /** Каждый вызов — независимая "личность" со своим in-memory хранилищем ключей, без Android. */
    private fun newIdentity() = KeyManager(InMemoryKeyValueStore())

    @Test
    fun `full round-trip yields matching sendKey-recvKey pairs on both sides`() {
        val alice = newIdentity()
        val bob = newIdentity()
        val aliceIdentity = alice.getOrCreateIdentity()
        val bobIdentity = bob.getOrCreateIdentity()

        val initiatorSession = IkHandshake.startInitiator(alice, bobIdentity.x25519Public)
        val responderSession = IkHandshake.startResponder(bob, initiatorSession.message1)
            ?: throw AssertionError("Ответчик должен принять корректный Message1")

        val message2 = responderSession.buildMessage2(bob)
        val (message3, aliceResult) = initiatorSession.consumeMessage2(message2, bobIdentity.ed25519Public, alice)
            ?: throw AssertionError("Инициатор должен принять корректный Message2 (верная подпись)")

        val bobResult = responderSession.consumeMessage3(message3, aliceIdentity.ed25519Public, bob)
            ?: throw AssertionError("Ответчик должен принять корректный Message3 (верная подпись)")

        // То, что одна сторона шифрует ("send"), другая должна расшифровывать ("recv"), и наоборот.
        assertArrayEquals(aliceResult.sendKey, bobResult.recvKey)
        assertArrayEquals(aliceResult.recvKey, bobResult.sendKey)
    }

    @Test
    fun `Message2 verified with the wrong signing key is rejected`() {
        val alice = newIdentity()
        val bob = newIdentity()
        val mallory = newIdentity() // третья личность — её Ed25519-ключ заведомо не ключ Боба
        val bobIdentity = bob.getOrCreateIdentity()
        val malloryIdentity = mallory.getOrCreateIdentity()

        val initiatorSession = IkHandshake.startInitiator(alice, bobIdentity.x25519Public)
        val responderSession = IkHandshake.startResponder(bob, initiatorSession.message1)
            ?: throw AssertionError("Ответчик должен принять корректный Message1")
        val message2 = responderSession.buildMessage2(bob)

        // Проверяем подпись Боба чужим публичным ключом — должно провалиться.
        val result = initiatorSession.consumeMessage2(message2, malloryIdentity.ed25519Public, alice)

        assertNull(result)
    }

    @Test
    fun `replaying an old Message3 against a session with a new responder ephemeral is rejected`() {
        val alice = newIdentity()
        val bob = newIdentity()
        val aliceIdentity = alice.getOrCreateIdentity()
        val bobIdentity = bob.getOrCreateIdentity()

        // Первая (легитимная) попытка — доводим до готового Message3.
        val initiatorSession1 = IkHandshake.startInitiator(alice, bobIdentity.x25519Public)
        val responderSession1 = IkHandshake.startResponder(bob, initiatorSession1.message1)
            ?: throw AssertionError("Ответчик должен принять корректный Message1 (попытка 1)")
        val message2FromAttempt1 = responderSession1.buildMessage2(bob)
        val (oldMessage3, _) = initiatorSession1.consumeMessage2(message2FromAttempt1, bobIdentity.ed25519Public, alice)
            ?: throw AssertionError("Инициатор должен принять корректный Message2 (попытка 1)")

        // Вторая, независимая попытка: новый e_i у инициатора -> новый e_r у ответчика ->
        // другой транскрипт рукопожатия.
        val initiatorSession2 = IkHandshake.startInitiator(alice, bobIdentity.x25519Public)
        val responderSession2 = IkHandshake.startResponder(bob, initiatorSession2.message1)
            ?: throw AssertionError("Ответчик должен принять корректный Message1 (попытка 2)")

        // Атакующий подставляет ПОДПИСЬ ИЗ ПЕРВОЙ попытки (over старый транскрипт с
        // e_r из responderSession1) в сессию ответчика с новым e_r (responderSession2).
        val result = responderSession2.consumeMessage3(oldMessage3, aliceIdentity.ed25519Public, bob)

        assertNull(result)
    }

    // ---------- Этап 2.5, D-4a: статический ключ инициатора зашифрован в Message1 ----------

    private fun contains(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty() || needle.size > haystack.size) return false
        for (i in 0..haystack.size - needle.size) {
            var match = true
            for (j in needle.indices) if (haystack[i + j] != needle[j]) { match = false; break }
            if (match) return true
        }
        return false
    }

    @Test
    fun `Message1 has fixed size and hides the initiator static key`() {
        val alice = newIdentity()
        val bob = newIdentity()
        val aliceIdentity = alice.getOrCreateIdentity()
        val bobIdentity = bob.getOrCreateIdentity()

        val session = IkHandshake.startInitiator(alice, bobIdentity.x25519Public)

        assertEquals(80, session.message1.size)
        assertEquals(IkHandshake.MESSAGE1_SIZE, session.message1.size)
        assertFalse("s_i не должен встречаться в Message1", contains(session.message1, aliceIdentity.x25519Public))
        assertFalse("s_r не должен встречаться в Message1", contains(session.message1, bobIdentity.x25519Public))
    }

    @Test
    fun `responder recovers the initiator static key from Message1`() {
        val alice = newIdentity()
        val bob = newIdentity()
        val aliceIdentity = alice.getOrCreateIdentity()
        val bobIdentity = bob.getOrCreateIdentity()

        val session = IkHandshake.startInitiator(alice, bobIdentity.x25519Public)
        val responder = IkHandshake.startResponder(bob, session.message1)

        assertNotNull(responder)
        assertArrayEquals(aliceIdentity.x25519Public, responder!!.remoteStaticPublic)
    }

    @Test
    fun `two Message1 from the same pair have no common 16 byte window`() {
        val alice = newIdentity()
        val bobIdentity = newIdentity().getOrCreateIdentity()

        val first = IkHandshake.startInitiator(alice, bobIdentity.x25519Public).message1
        val second = IkHandshake.startInitiator(alice, bobIdentity.x25519Public).message1

        for (i in 0..first.size - 16) {
            assertFalse("общее окно на смещении $i", contains(second, first.copyOfRange(i, i + 16)))
        }
    }

    @Test
    fun `any single byte change in Message1 is rejected`() {
        val alice = newIdentity()
        val bob = newIdentity()
        val message1 = IkHandshake.startInitiator(alice, bob.getOrCreateIdentity().x25519Public).message1

        for (i in message1.indices) {
            val tampered = message1.copyOf()
            tampered[i] = (tampered[i].toInt() xor 0x01).toByte()
            assertNull("байт $i", IkHandshake.startResponder(bob, tampered))
        }
    }

    @Test
    fun `high bit of the ephemeral key is covered by the AAD`() {
        // X25519 игнорирует старший бит последнего байта публичного ключа, поэтому DH не меняется —
        // подмену должна поймать привязка e_i к AAD/соли.
        val alice = newIdentity()
        val bob = newIdentity()
        val message1 = IkHandshake.startInitiator(alice, bob.getOrCreateIdentity().x25519Public).message1
        val tampered = message1.copyOf()
        tampered[31] = (tampered[31].toInt() xor 0x80).toByte()

        assertNull(IkHandshake.startResponder(bob, tampered))
    }

    @Test
    fun `Message1 addressed to another responder is rejected`() {
        val alice = newIdentity()
        val bob = newIdentity()
        val carol = newIdentity()

        val message1ForBob = IkHandshake.startInitiator(alice, bob.getOrCreateIdentity().x25519Public).message1

        assertNull(IkHandshake.startResponder(carol, message1ForBob))
    }

    @Test
    fun `Message1 of the old 64 byte format and wrong lengths are rejected`() {
        val bob = newIdentity()
        for (size in listOf(0, 1, 32, 64, 79, 81, 96)) {
            assertNull("size=$size", IkHandshake.startResponder(bob, ByteArray(size) { 7 }))
        }
    }

    @Test
    fun `Message1 with a low order ephemeral point is rejected without exception`() {
        val bob = newIdentity()
        val lowOrder = ByteArray(IkHandshake.MESSAGE1_SIZE) // e_i = 32 нулевых байт — точка малого порядка

        assertNull(IkHandshake.startResponder(bob, lowOrder))
    }

    // ---------- Этап 2.5, D-4b: подписи в Message2/Message3 зашифрованы ----------

    /** Рукопожатие, остановленное после Message2: всё, что нужно тестам подмены и replay. */
    private class Flow(
        val alice: KeyManager,
        val bob: KeyManager,
        val aliceIdentity: DeviceIdentity,
        val bobIdentity: DeviceIdentity,
        val initiator: IkHandshake.InitiatorSession,
        val responder: IkHandshake.ResponderSession,
        val message2: ByteArray
    )

    private fun startFlow(
        alice: KeyManager = newIdentity(),
        bob: KeyManager = newIdentity()
    ): Flow {
        val aliceIdentity = alice.getOrCreateIdentity()
        val bobIdentity = bob.getOrCreateIdentity()
        val initiator = IkHandshake.startInitiator(alice, bobIdentity.x25519Public)
        val responder = IkHandshake.startResponder(bob, initiator.message1)
            ?: throw AssertionError("Ответчик должен принять корректный Message1")
        return Flow(alice, bob, aliceIdentity, bobIdentity, initiator, responder, responder.buildMessage2(bob))
    }

    private fun Flow.consume2(message2: ByteArray = this.message2) =
        initiator.consumeMessage2(message2, bobIdentity.ed25519Public, alice)

    private fun Flow.consume3(message3: ByteArray) =
        responder.consumeMessage3(message3, aliceIdentity.ed25519Public, bob)

    @Test
    fun `Message2 and Message3 have fixed sizes`() {
        val flow = startFlow()
        val (message3, _) = flow.consume2() ?: throw AssertionError("Message2 должен приниматься")

        assertEquals(112, flow.message2.size)
        assertEquals(80, message3.size)
        assertEquals(IkHandshake.MESSAGE2_SIZE, flow.message2.size)
        assertEquals(IkHandshake.MESSAGE3_SIZE, message3.size)
    }

    @Test
    fun `signatures are not visible and not verifiable from public data`() {
        val flow = startFlow()
        val (message3, _) = flow.consume2() ?: throw AssertionError("Message2 должен приниматься")

        val eI = flow.initiator.message1.copyOfRange(0, IkHandshake.EPHEMERAL_KEY_SIZE)
        val eR = flow.message2.copyOfRange(0, IkHandshake.EPHEMERAL_KEY_SIZE)
        // Всё, что знает наблюдатель с публичными ключами-кандидатами: s_i, s_r, e_i, e_r (последние два — с провода).
        val transcript = IkHandshake.computeTranscript(
            flow.aliceIdentity.x25519Public, flow.bobIdentity.x25519Public, eI, eR
        )

        // Контроль: настоящая подпись этого transcript проверяется (значит, transcript и verify собраны верно).
        val bobSignature = flow.bob.signWithIdentity(transcript)
        val aliceSignature = flow.alice.signWithIdentity(transcript)
        assertTrue(Ed25519Verification.verify(flow.bobIdentity.ed25519Public, transcript, bobSignature))
        assertTrue(Ed25519Verification.verify(flow.aliceIdentity.ed25519Public, transcript, aliceSignature))

        // Подпись не лежит на проводе открытым текстом...
        assertFalse(contains(flow.message2, bobSignature))
        assertFalse(contains(message3, aliceSignature))
        // ...и попытка «подтверждения по списку ключей» на реальных байтах кадров не проходит.
        assertFalse(Ed25519Verification.verify(flow.bobIdentity.ed25519Public, transcript, flow.message2.copyOfRange(32, 96)))
        assertFalse(Ed25519Verification.verify(flow.aliceIdentity.ed25519Public, transcript, message3.copyOfRange(0, 64)))
    }

    @Test
    fun `Message2 and Message3 of two handshakes of the same pair share no 16 byte window`() {
        val alice = newIdentity()
        val bob = newIdentity()
        val first = startFlow(alice, bob)
        val second = startFlow(alice, bob)
        val third1 = first.consume2()!!.first
        val third2 = second.consume2()!!.first

        for ((a, b) in listOf(first.message2 to second.message2, third1 to third2)) {
            for (i in 0..a.size - 16) {
                assertFalse("общее окно на смещении $i", contains(b, a.copyOfRange(i, i + 16)))
            }
        }
    }

    @Test
    fun `any single byte change in Message2 is rejected`() {
        val flow = startFlow()

        for (i in flow.message2.indices) {
            val tampered = flow.message2.copyOf()
            tampered[i] = (tampered[i].toInt() xor 0x01).toByte()
            assertNull("байт $i", flow.consume2(tampered))
        }
        // Неудачные попытки не портят сессию: настоящий Message2 по-прежнему принимается.
        assertNotNull(flow.consume2())
    }

    @Test
    fun `high bit of the responder ephemeral key is covered by the transcript`() {
        // X25519 игнорирует старший бит последнего байта публичного ключа — DH не меняется,
        // подмену должен поймать transcript (он включает e_r побайтно).
        val flow = startFlow()
        val tampered = flow.message2.copyOf()
        tampered[31] = (tampered[31].toInt() xor 0x80).toByte()

        assertNull(flow.consume2(tampered))
    }

    @Test
    fun `any single byte change in Message3 is rejected`() {
        val flow = startFlow()
        val (message3, _) = flow.consume2() ?: throw AssertionError("Message2 должен приниматься")

        for (i in message3.indices) {
            val tampered = message3.copyOf()
            tampered[i] = (tampered[i].toInt() xor 0x01).toByte()
            assertNull("байт $i", flow.consume3(tampered))
        }
        assertNotNull(flow.consume3(message3))
    }

    @Test
    fun `old Message3 replayed into a new handshake is rejected`() {
        val alice = newIdentity()
        val bob = newIdentity()
        val old = startFlow(alice, bob)
        val oldMessage3 = old.consume2()!!.first

        val fresh = startFlow(alice, bob)

        assertNull(fresh.consume3(oldMessage3))
    }

    @Test
    fun `old Message2 replayed to a new initiator is rejected`() {
        val alice = newIdentity()
        val bob = newIdentity()
        val old = startFlow(alice, bob)

        val fresh = startFlow(alice, bob)

        assertNull(fresh.consume2(old.message2))
    }

    @Test
    fun `Message2 payload reused as Message3 is rejected`() {
        val flow = startFlow()
        val reflected = flow.message2.copyOfRange(IkHandshake.EPHEMERAL_KEY_SIZE, flow.message2.size)

        assertEquals(IkHandshake.MESSAGE3_SIZE, reflected.size)
        assertNull(flow.consume3(reflected))
    }

    @Test
    fun `Message3 reused as Message2 payload is rejected`() {
        val flow = startFlow()
        val (message3, _) = flow.consume2() ?: throw AssertionError("Message2 должен приниматься")
        val reflected = flow.message2.copyOfRange(0, IkHandshake.EPHEMERAL_KEY_SIZE) + message3

        assertNull(flow.consume2(reflected))
    }

    @Test
    fun `reflection is stopped by key separation and not only by the signature check`() {
        // Устройство соединяется само с собой: обе подписи — одним Ed25519-ключом под одним transcript.
        // Тогда отражённый Message2 прошёл бы проверку подписи, и единственная преграда — разные info у k2 и k3.
        val self = newIdentity()
        val flow = startFlow(alice = self, bob = self)

        val reflectedAsMessage3 = flow.message2.copyOfRange(IkHandshake.EPHEMERAL_KEY_SIZE, flow.message2.size)
        assertNull(flow.consume3(reflectedAsMessage3))

        val (message3, _) = flow.consume2() ?: throw AssertionError("Message2 должен приниматься")
        val reflectedAsMessage2 = flow.message2.copyOfRange(0, IkHandshake.EPHEMERAL_KEY_SIZE) + message3
        assertNull(flow.consume2(reflectedAsMessage2))

        // Контроль: настоящая цепочка в этом режиме работает, то есть отказы выше вызваны именно ключами.
        assertNotNull(flow.consume3(message3))
    }

    @Test
    fun `Message3 verified with another signing key is rejected`() {
        val flow = startFlow()
        val mallory = newIdentity().getOrCreateIdentity()
        val (message3, _) = flow.consume2() ?: throw AssertionError("Message2 должен приниматься")

        assertNull(flow.responder.consumeMessage3(message3, mallory.ed25519Public, flow.bob))
    }

    @Test
    fun `low order ephemeral point in Message2 is rejected without exception`() {
        val flow = startFlow()

        assertNull(flow.consume2(ByteArray(IkHandshake.MESSAGE2_SIZE)))
    }

    @Test
    fun `Message2 and Message3 of the old format and wrong lengths are rejected`() {
        val flow = startFlow()
        for (size in listOf(0, 64, 95, 96, 111, 113)) {
            assertNull("M2 size=$size", flow.consume2(ByteArray(size) { 7 }))
        }
        for (size in listOf(0, 63, 64, 79, 81)) {
            assertNull("M3 size=$size", flow.consume3(ByteArray(size) { 7 }))
        }
    }
}
