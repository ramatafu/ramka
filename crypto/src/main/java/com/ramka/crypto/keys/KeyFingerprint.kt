package com.ramka.crypto.keys

import org.bouncycastle.crypto.digests.SHA256Digest

/**
 * Короткий отпечаток пары ключей контакта для сверки "из рук в руки" (п. 8.4).
 * Не является секретом — используется только для визуального сравнения.
 */
object KeyFingerprint {
    fun compute(x25519PublicKey: ByteArray, ed25519PublicKey: ByteArray): String {
        val digest = SHA256Digest()
        digest.update(x25519PublicKey, 0, x25519PublicKey.size)
        digest.update(ed25519PublicKey, 0, ed25519PublicKey.size)
        val out = ByteArray(digest.digestSize)
        digest.doFinal(out, 0)

        // Первые 8 байт хеша как непрерывный hex, сгруппированный по 4 символа — удобно сверять вслух.
        val hex = out.copyOf(8).joinToString("") { b -> "%02x".format(b) }
        return hex.chunked(4).joinToString(" ")
    }
}
