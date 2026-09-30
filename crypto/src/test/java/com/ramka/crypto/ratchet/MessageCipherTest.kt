package com.ramka.crypto.ratchet

import org.bouncycastle.crypto.InvalidCipherTextException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.security.SecureRandom

class MessageCipherTest {

    private fun randomKey(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

    @Test
    fun `round-trip encrypt then decrypt returns original plaintext`() {
        val key = randomKey()
        val plaintext = "привет, ramka".toByteArray(Charsets.UTF_8)

        val ciphertext = MessageCipher.encrypt(key, counter = 0L, plaintext = plaintext)
        val decrypted = MessageCipher.decrypt(key, counter = 0L, ciphertext = ciphertext)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test(expected = InvalidCipherTextException::class)
    fun `bit-flip in ciphertext causes decryption to throw`() {
        val key = randomKey()
        val plaintext = "test message".toByteArray()
        val ciphertext = MessageCipher.encrypt(key, counter = 0L, plaintext = plaintext)

        // Переворачиваем один бит — тег аутентификации ChaCha20-Poly1305 должен не сойтись.
        ciphertext[0] = (ciphertext[0].toInt() xor 0x01).toByte()

        MessageCipher.decrypt(key, counter = 0L, ciphertext = ciphertext)
    }

    @Test(expected = InvalidCipherTextException::class)
    fun `decrypting with a different counter than was used to encrypt throws`() {
        val key = randomKey()
        val plaintext = "test message".toByteArray()
        val ciphertext = MessageCipher.encrypt(key, counter = 5L, plaintext = plaintext)

        // Номер сообщения — часть AAD (см. KDoc MessageCipher): подмена counter
        // меняет и nonce, и AAD, поэтому расшифровка с другим counter обязана упасть,
        // а не тихо вернуть мусор — это и есть механизм защиты от подмены номера.
        MessageCipher.decrypt(key, counter = 6L, ciphertext = ciphertext)
    }

    @Test
    fun `same plaintext at different counters produces different ciphertext`() {
        val key = randomKey()
        val plaintext = "repeat me".toByteArray()

        val ciphertextAtCounter0 = MessageCipher.encrypt(key, counter = 0L, plaintext = plaintext)
        val ciphertextAtCounter1 = MessageCipher.encrypt(key, counter = 1L, plaintext = plaintext)

        assertFalse(ciphertextAtCounter0.contentEquals(ciphertextAtCounter1))
    }
}
