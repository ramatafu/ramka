package com.ramka.crypto.ratchet

import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter

/**
 * Шифрование/расшифровка тела одного сообщения на сессионном ключе, полученном из
 * [com.ramka.crypto.noise.IkHandshake]. Использует ChaCha20-Poly1305 (AEAD) из
 * Bouncy Castle — готовый, проверенный примитив, без собственной криптографии.
 *
 * Nonce — ДЕТЕРМИНИРОВАННЫЙ: 12 байт = 4 нулевых байта + 8-байтный big-endian
 * номер сообщения в рамках текущей сессии (см. [SessionCipher]). Поскольку ключ
 * уникален для каждого установленного соединения (свежий ECDH при каждом
 * рукопожатии), а номер сообщения строго возрастает и не переиспользуется —
 * повторное использование nonce с одним и тем же ключом исключено.
 * Номер сообщения также передаётся как associated data (AAD) — получатель не
 * может подменить его, не сломав тег аутентификации, что и даёт защиту от replay
 * в паре с проверкой строгого возрастания в [SessionCipher].
 */
object MessageCipher {

    private const val KEY_SIZE = 32

    fun nonceFor(counter: Long): ByteArray {
        val nonce = ByteArray(12)
        for (i in 0 until 8) {
            nonce[11 - i] = ((counter shr (8 * i)) and 0xFF).toByte()
        }
        return nonce
    }

    fun encrypt(key: ByteArray, counter: Long, plaintext: ByteArray): ByteArray {
        require(key.size == KEY_SIZE) { "Ожидается ключ длиной 32 байта" }
        val nonce = nonceFor(counter)
        val aad = nonce.copyOfRange(4, 12) // тот же номер сообщения, что и в nonce — см. описание выше

        val cipher = ChaCha20Poly1305()
        cipher.init(true, AEADParameters(KeyParameter(key), 128, nonce, aad))
        val out = ByteArray(cipher.getOutputSize(plaintext.size))
        val len = cipher.processBytes(plaintext, 0, plaintext.size, out, 0)
        cipher.doFinal(out, len)
        return out
    }

    fun decrypt(key: ByteArray, counter: Long, ciphertext: ByteArray): ByteArray {
        require(key.size == KEY_SIZE) { "Ожидается ключ длиной 32 байта" }
        val nonce = nonceFor(counter)
        val aad = nonce.copyOfRange(4, 12)

        val cipher = ChaCha20Poly1305()
        cipher.init(false, AEADParameters(KeyParameter(key), 128, nonce, aad))
        val out = ByteArray(cipher.getOutputSize(ciphertext.size))
        val len = cipher.processBytes(ciphertext, 0, ciphertext.size, out, 0)
        val total = len + cipher.doFinal(out, len)
        return out.copyOf(total)
    }
}
