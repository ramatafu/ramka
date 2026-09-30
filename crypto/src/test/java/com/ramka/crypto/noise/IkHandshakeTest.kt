package com.ramka.crypto.noise

import com.ramka.crypto.keys.InMemoryKeyValueStore
import com.ramka.crypto.keys.KeyManager
import org.junit.Assert.assertArrayEquals
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
}
