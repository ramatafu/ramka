package com.ramka.crypto.keys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class KeyFingerprintTest {

    private fun randomKeyBytes(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

    @Test
    fun `same key pair produces the same fingerprint`() {
        val x25519 = randomKeyBytes()
        val ed25519 = randomKeyBytes()

        val fingerprint1 = KeyFingerprint.compute(x25519, ed25519)
        // Отдельные копии тех же байт — проверяем, что сравнение идёт по содержимому,
        // а не по ссылке на массив.
        val fingerprint2 = KeyFingerprint.compute(x25519.copyOf(), ed25519.copyOf())

        assertEquals(fingerprint1, fingerprint2)
    }

    @Test
    fun `different key pairs produce different fingerprints`() {
        val ed25519 = randomKeyBytes()
        val fingerprintA = KeyFingerprint.compute(randomKeyBytes(), ed25519)
        val fingerprintB = KeyFingerprint.compute(randomKeyBytes(), ed25519)

        assertNotEquals(fingerprintA, fingerprintB)
    }

    @Test
    fun `fingerprint format is stable — four groups of four lowercase hex characters`() {
        val fingerprint = KeyFingerprint.compute(randomKeyBytes(), randomKeyBytes())

        // Формат зафиксирован в KeyFingerprint.compute: 8 байт хеша -> 16 hex-символов,
        // сгруппированных по 4 через пробел. Экран верификации (§8.4) и это ожидание
        // формата должны меняться синхронно, если формат когда-либо изменится.
        val pattern = Regex("^[0-9a-f]{4}( [0-9a-f]{4}){3}$")
        assertTrue("Формат отпечатка изменился: \"$fingerprint\"", pattern.matches(fingerprint))
    }
}
