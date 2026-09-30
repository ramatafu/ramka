package com.ramka.crypto.noise

import com.ramka.crypto.keys.InMemoryKeyValueStore
import com.ramka.crypto.keys.KeyManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
}
