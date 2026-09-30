package com.ramka.crypto.ratchet

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.security.SecureRandom

class SessionCipherTest {

    private fun randomKey(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

    /** Зеркальная пара, как sendKey/recvKey получаются из IkHandshake у двух сторон одной сессии. */
    private fun mirroredPair(): Pair<SessionCipher, SessionCipher> {
        val keyAtoB = randomKey()
        val keyBtoA = randomKey()
        val alice = SessionCipher(sendKey = keyAtoB, recvKey = keyBtoA)
        val bob = SessionCipher(sendKey = keyBtoA, recvKey = keyAtoB)
        return alice to bob
    }

    @Test
    fun `strictly increasing counters are accepted in order`() {
        val (alice, bob) = mirroredPair()

        val frame0 = alice.encryptNext("first".toByteArray())
        val frame1 = alice.encryptNext("second".toByteArray())

        assertArrayEquals("first".toByteArray(), bob.decryptIfFresh(frame0.counter, frame0.ciphertext))
        assertArrayEquals("second".toByteArray(), bob.decryptIfFresh(frame1.counter, frame1.ciphertext))
    }

    @Test
    fun `replaying an already-accepted counter is rejected`() {
        val (alice, bob) = mirroredPair()
        val frame0 = alice.encryptNext("first".toByteArray())

        val firstAttempt = bob.decryptIfFresh(frame0.counter, frame0.ciphertext)
        assertArrayEquals("first".toByteArray(), firstAttempt)

        // Тот же самый (counter, ciphertext) второй раз — классический replay.
        val replay = bob.decryptIfFresh(frame0.counter, frame0.ciphertext)
        assertNull(replay)
    }

    @Test
    fun `counter not greater than last accepted is rejected even with valid ciphertext for that counter`() {
        val (alice, bob) = mirroredPair()
        alice.encryptNext("zero".toByteArray()) // counter=0, не отправляем боту
        val frame1 = alice.encryptNext("one".toByteArray()) // counter=1
        val frame2 = alice.encryptNext("two".toByteArray()) // counter=2

        // Принимаем сразу counter=2 (например, из-за переупорядочивания на транспорте).
        assertArrayEquals("two".toByteArray(), bob.decryptIfFresh(frame2.counter, frame2.ciphertext))

        // Более ранний counter=1 после этого уже не должен приниматься — строгое возрастание,
        // окна для "опоздавших" пакетов в этой версии нет (см. DEVIATIONS.md, D-5).
        val late = bob.decryptIfFresh(frame1.counter, frame1.ciphertext)
        assertNull(late)
    }

    @Test
    fun `garbage ciphertext is rejected without throwing`() {
        val (_, bob) = mirroredPair()
        val garbage = ByteArray(48).also { SecureRandom().nextBytes(it) }

        // decryptIfFresh оборачивает расшифровку в runCatching — вызывающий код
        // не должен получать исключение на мусорных/повреждённых данных с сети.
        val result = bob.decryptIfFresh(0L, garbage)

        assertNull(result)
    }
}
