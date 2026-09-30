package com.ramka.crypto.keys

import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom

/**
 * Единая точка генерации и хранения долговременных ключей устройства.
 *
 * X25519 — используется для обмена ключами (ECDH в IK-рукопожатии).
 * Ed25519 — используется для подписи транскрипта рукопожатия (аутентификация,
 * защита от MITM в локальной сети — см. crypto/noise/IkHandshake).
 *
 * Приватные ключи никогда не покидают устройство и никогда не логируются:
 * генерируются локально через SecureRandom и хранятся зашифрованными через
 * [KeyValueStore] (в продакшене — [com.ramka.crypto.securestorage.SecureKeyStore]
 * поверх Android Keystore; в unit-тестах — простая in-memory реализация, что и
 * делает этот класс тестируемым без Android).
 *
 * ГИГИЕНА КЛЮЧЕВОГО МАТЕРИАЛА: промежуточный byte[] с приватным ключом,
 * возвращаемый [KeyValueStore.getBytes], обнуляется сразу после того, как из
 * него собран объект *PrivateKeyParameters (конструктор Bouncy Castle копирует
 * байты во внутренний массив, поэтому обнулять наш временный массив безопасно).
 * ОСТАТОЧНЫЙ РИСК, который это НЕ закрывает: в реализации на Android сам
 * base64-текст ключа некоторое время существует как java.lang.String внутри
 * EncryptedSharedPreferences (строки в JVM неизменяемы и принудительно обнулены
 * быть не могут) — это ограничение выбранного механизма хранения, а не то, что
 * можно поправить в этом классе. Как и объекты *PrivateKeyParameters, которые эти
 * методы возвращают: их собственный внутренний массив байт живёт до сборки мусора
 * JVM — в лёгком (lightweight) API Bouncy Castle нет метода destroy()/wipe().
 */
class KeyManager(private val secureStore: KeyValueStore) {

    private val random = SecureRandom()

    companion object {
        private const val KEY_X25519_PRIVATE = "identity_x25519_private"
        private const val KEY_ED25519_PRIVATE = "identity_ed25519_private"
    }

    /** Возвращает существующую идентичность устройства либо создаёт новую при первом запуске. */
    fun getOrCreateIdentity(): DeviceIdentity {
        val x25519Private = loadX25519Private()
        val ed25519Private = loadEd25519Private()
        return DeviceIdentity(
            x25519Public = x25519Private.generatePublicKey().encoded,
            ed25519Public = ed25519Private.generatePublicKey().encoded
        )
    }

    /** Приватный ключ X25519 нужен только внутри crypto-модуля для handshake, наружу не отдаётся. */
    internal fun loadX25519Private(): X25519PrivateKeyParameters {
        val bytes = secureStore.getBytes(KEY_X25519_PRIVATE) ?: return generateAndStoreX25519()
        val params = X25519PrivateKeyParameters(bytes, 0)
        bytes.fill(0) // временная копия из хранилища больше не нужна
        return params
    }

    internal fun loadEd25519Private(): Ed25519PrivateKeyParameters {
        val bytes = secureStore.getBytes(KEY_ED25519_PRIVATE) ?: return generateAndStoreEd25519()
        val params = Ed25519PrivateKeyParameters(bytes, 0)
        bytes.fill(0) // временная копия из хранилища больше не нужна
        return params
    }

    /**
     * Новая эфемерная пара X25519 для одного рукопожатия. НЕ сохраняется —
     * живёт только в памяти на время установления соединения (forward secrecy).
     */
    fun generateEphemeralX25519(): X25519PrivateKeyParameters = X25519PrivateKeyParameters(random)

    /** Подпись данных долговременным identity-ключом Ed25519 (например, транскрипта рукопожатия). */
    fun signWithIdentity(data: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, loadEd25519Private())
        signer.update(data, 0, data.size)
        return signer.generateSignature()
    }

    private fun generateAndStoreX25519(): X25519PrivateKeyParameters {
        val priv = X25519PrivateKeyParameters(random)
        secureStore.putBytes(KEY_X25519_PRIVATE, priv.encoded)
        return priv
    }

    private fun generateAndStoreEd25519(): Ed25519PrivateKeyParameters {
        val priv = Ed25519PrivateKeyParameters(random)
        secureStore.putBytes(KEY_ED25519_PRIVATE, priv.encoded)
        return priv
    }
}

/** Публичная часть идентичности устройства — то, что кодируется в QR-приглашении. */
data class DeviceIdentity(
    val x25519Public: ByteArray,
    val ed25519Public: ByteArray
)

/** Проверка подписи чужим публичным Ed25519-ключом — используется при верификации транскрипта. */
object Ed25519Verification {
    fun verify(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean {
        return try {
            val verifier = Ed25519Signer()
            verifier.init(false, Ed25519PublicKeyParameters(publicKey, 0))
            verifier.update(data, 0, data.size)
            verifier.verifySignature(signature)
        } catch (_: Exception) {
            false
        }
    }
}
