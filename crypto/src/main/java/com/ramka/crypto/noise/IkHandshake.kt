package com.ramka.crypto.noise

import com.ramka.crypto.keys.Ed25519Verification
import com.ramka.crypto.keys.KeyManager
import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters

/**
 * Рукопожатие по мотивам паттерна Noise_IK (Noise Protocol Framework), реализованное
 * как ручная композиция ПРОВЕРЕННЫХ примитивов Bouncy Castle (X25519, HKDF/SHA-256,
 * Ed25519) — см. запрет на собственную криптографию в PROJECT_SPEC.md 6.2: здесь не
 * создаётся новый алгоритм, только стандартная схема es/ee/se + подпись транскрипта.
 *
 * Статические X25519/Ed25519-ключи обеих сторон считаются уже известными друг другу
 * (получены через QR при добавлении в контакты, см. AddContactUseCase) — рукопожатие
 * подтверждает, что собеседник владеет приватными ключами, соответствующими этим уже
 * известным публичным ключам, и вырабатывает свежий сессионный ключ с forward secrecy.
 *
 * Протокол (3 сообщения), этап 2.5, D-4a:
 *   I -> R: e_i_pub || AEAD(k1, s_i_pub)                             (Message1, 80 байт)
 *   R -> I: e_r_pub || Sign_R(transcript)                            (Message2, 96 байт)
 *   I -> R: Sign_I(transcript)                                       (Message3, 64 байта)
 *
 *   k1 = HKDF(es, salt = e_i_pub || s_r_pub, info = "ramka-ik-v2-m1")
 *   AEAD = ChaCha20-Poly1305, nonce = 12 нулевых байт (k1 одноразовый: свежий e_i на
 *          каждое соединение), aad = e_i_pub || s_r_pub
 *   transcript = SHA256(label || s_i_pub || s_r_pub || e_i_pub || e_r_pub)
 *   root = HKDF(es || ee || se, salt=transcript, info="ramka-ik-root")
 *
 * Статический ключ инициатора s_i больше не передаётся открытым текстом (D-4a): его
 * видит только тот, кто может вычислить es, то есть владелец приватного ключа ответчика
 * (или самого e_i). Пассивный наблюдатель LAN не получает постоянного идентификатора
 * устройства. ОТКРЫТЫЙ ПУНКТ (D-4b): подписи в Message2/Message3 пока открыты и
 * проверяемы по публичным ключам-кандидатам — закрывается следующим шагом.
 *
 * Любая ошибка разбора или расшифровки Message1 даёт null (молчаливый отказ, без
 * различия причин — «не тот адресат», «порча», «неизвестная форма» неразличимы).
 */
object IkHandshake {

    private const val LABEL = "ramka-ik-v2"
    const val EPHEMERAL_KEY_SIZE = 32
    const val SIGNATURE_SIZE = 64
    private const val STATIC_KEY_SIZE = 32
    private const val TAG_SIZE = 16

    /** Message1 = e_i_pub (32) || AEAD(s_i_pub) (32 + 16 тега) = 80 байт. */
    const val MESSAGE1_SIZE = EPHEMERAL_KEY_SIZE + STATIC_KEY_SIZE + TAG_SIZE

    /** Результат успешного рукопожатия: ключи для двух направлений. */
    data class Result(val sendKey: ByteArray, val recvKey: ByteArray) {
        fun wipe() { sendKey.fill(0); recvKey.fill(0) }
    }

    // ---------- Инициатор ----------

    class InitiatorSession internal constructor(
        private val ephemeralPrivate: X25519PrivateKeyParameters,
        private val localStaticPublic: ByteArray,
        private val remoteStaticPublic: ByteArray
    ) {
        /**
         * e_i_pub || AEAD(k1, s_i_pub). Если статический ключ ответчика некорректен (low-order точка),
         * DH бросает IllegalStateException уже здесь — вызывающий код (sendMessage) трактует это как
         * неудачную попытку отправки.
         */
        val message1: ByteArray = buildMessage1()

        private fun buildMessage1(): ByteArray {
            val ephemeralPublic = ephemeralPrivate.generatePublicKey().encoded
            val es = dh(ephemeralPrivate, remoteStaticPublic)
            val k1 = deriveMessage1Key(es, ephemeralPublic, remoteStaticPublic)
            es.fill(0)
            val sealed = aeadSeal(k1, ephemeralPublic + remoteStaticPublic, localStaticPublic)
            k1.fill(0)
            return ephemeralPublic + sealed
        }

        /** Принимает Message2 от ответчика, проверяет подпись, возвращает Message3 для отправки и итоговые ключи. */
        fun consumeMessage2(
            message2: ByteArray,
            remoteSigningPublicKey: ByteArray,
            keyManager: KeyManager
        ): Pair<ByteArray, Result>? {
            if (message2.size != EPHEMERAL_KEY_SIZE + SIGNATURE_SIZE) return null
            val remoteEphemeralPub = message2.copyOfRange(0, EPHEMERAL_KEY_SIZE)
            val signature = message2.copyOfRange(EPHEMERAL_KEY_SIZE, message2.size)

            val transcript = computeTranscript(
                localStaticPublic, remoteStaticPublic,
                ephemeralPrivate.generatePublicKey().encoded, remoteEphemeralPub
            )
            if (!Ed25519Verification.verify(remoteSigningPublicKey, transcript, signature)) {
                return null // подпись не сошлась — вероятная MITM-атака, соединение отклоняется
            }

            val es = dh(ephemeralPrivate, remoteStaticPublic)
            val ee = dh(ephemeralPrivate, remoteEphemeralPub)
            val se = dh(keyManager.loadX25519Private(), remoteEphemeralPub)
            val result = deriveKeys(es, ee, se, transcript, isInitiator = true)

            val mySignature = keyManager.signWithIdentity(transcript)
            return mySignature to result
        }
    }

    fun startInitiator(keyManager: KeyManager, remoteStaticPublic: ByteArray): InitiatorSession {
        val identity = keyManager.getOrCreateIdentity()
        return InitiatorSession(keyManager.generateEphemeralX25519(), identity.x25519Public, remoteStaticPublic)
    }

    // ---------- Ответчик ----------

    class ResponderSession internal constructor(
        private val ephemeralPrivate: X25519PrivateKeyParameters,
        val remoteStaticPublic: ByteArray,
        private val remoteEphemeralPublic: ByteArray,
        private val transcript: ByteArray
    ) {
        /** Message2 для отправки инициатору. */
        fun buildMessage2(keyManager: KeyManager): ByteArray {
            val myEphemeralPub = ephemeralPrivate.generatePublicKey().encoded
            val signature = keyManager.signWithIdentity(transcript)
            return myEphemeralPub + signature
        }

        /** Проверяет Message3 (подпись инициатора) и, если всё верно, возвращает итоговые ключи. */
        fun consumeMessage3(
            message3: ByteArray,
            remoteSigningPublicKey: ByteArray,
            keyManager: KeyManager
        ): Result? {
            if (message3.size != SIGNATURE_SIZE) return null
            if (!Ed25519Verification.verify(remoteSigningPublicKey, transcript, message3)) return null

            // es у ответчика = DH(s_r_priv, e_i_pub) — тот же секрет, что у инициатора DH(e_i_priv, s_r_pub).
            val es = dh(keyManager.loadX25519Private(), remoteEphemeralPublic)
            val ee = dh(ephemeralPrivate, remoteEphemeralPublic)
            val se = dh(ephemeralPrivate, remoteStaticPublic)
            return deriveKeys(es, ee, se, transcript, isInitiator = false)
        }
    }

    /**
     * Принимает Message1 от инициатора и начинает сессию ответчика. null — при любой ошибке
     * (длина, некорректная эфемерная точка, не сошёлся тег AEAD): причины намеренно не различаются.
     */
    fun startResponder(keyManager: KeyManager, message1: ByteArray): ResponderSession? {
        if (message1.size != MESSAGE1_SIZE) return null
        val remoteEphemeralPublic = message1.copyOfRange(0, EPHEMERAL_KEY_SIZE)
        val sealed = message1.copyOfRange(EPHEMERAL_KEY_SIZE, message1.size)

        val identity = keyManager.getOrCreateIdentity()
        val staticPrivate = keyManager.loadX25519Private()
        val es = try {
            dh(staticPrivate, remoteEphemeralPublic)
        } catch (e: IllegalStateException) {
            return null // low-order / некорректная точка от инициатора
        }
        val k1 = deriveMessage1Key(es, remoteEphemeralPublic, identity.x25519Public)
        es.fill(0)
        val remoteStaticPublic = aeadOpen(k1, remoteEphemeralPublic + identity.x25519Public, sealed)
        k1.fill(0)
        if (remoteStaticPublic == null || remoteStaticPublic.size != STATIC_KEY_SIZE) return null

        val ephemeralPrivate = keyManager.generateEphemeralX25519()
        val transcript = computeTranscript(
            remoteStaticPublic, identity.x25519Public,
            remoteEphemeralPublic, ephemeralPrivate.generatePublicKey().encoded
        )
        return ResponderSession(ephemeralPrivate, remoteStaticPublic, remoteEphemeralPublic, transcript)
    }

    // ---------- Общие вычисления ----------

    private fun dh(privateKey: X25519PrivateKeyParameters, publicKeyBytes: ByteArray): ByteArray {
        val agreement = X25519Agreement()
        agreement.init(privateKey)
        val out = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(X25519PublicKeyParameters(publicKeyBytes, 0), out, 0)
        return out
    }

    /** Ключ шифрования Message1: HKDF(es), солью служат e_i_pub || s_r_pub; отдельный info — не пересекается с root. */
    private fun deriveMessage1Key(es: ByteArray, ephemeralInitiatorPub: ByteArray, staticResponderPub: ByteArray): ByteArray {
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(es, ephemeralInitiatorPub + staticResponderPub, "ramka-ik-v2-m1".toByteArray()))
        val key = ByteArray(32)
        hkdf.generateBytes(key, 0, 32)
        return key
    }

    /** ChaCha20-Poly1305 с нулевым nonce: допустимо только для ОДНОРАЗОВЫХ ключей (каждый k1 используется ровно раз). */
    private fun aeadSeal(key: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = ChaCha20Poly1305()
        cipher.init(true, AEADParameters(KeyParameter(key), TAG_SIZE * 8, ByteArray(12), aad))
        val out = ByteArray(cipher.getOutputSize(plaintext.size))
        val len = cipher.processBytes(plaintext, 0, plaintext.size, out, 0)
        cipher.doFinal(out, len)
        return out
    }

    private fun aeadOpen(key: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray? = try {
        val cipher = ChaCha20Poly1305()
        cipher.init(false, AEADParameters(KeyParameter(key), TAG_SIZE * 8, ByteArray(12), aad))
        val out = ByteArray(cipher.getOutputSize(ciphertext.size))
        val len = cipher.processBytes(ciphertext, 0, ciphertext.size, out, 0)
        val total = len + cipher.doFinal(out, len)
        out.copyOf(total)
    } catch (e: InvalidCipherTextException) {
        null
    }

    private fun computeTranscript(sInitiator: ByteArray, sResponder: ByteArray, eInitiator: ByteArray, eResponder: ByteArray): ByteArray {
        val digest = SHA256Digest()
        fun feed(b: ByteArray) = digest.update(b, 0, b.size)
        feed(LABEL.toByteArray())
        feed(sInitiator); feed(sResponder); feed(eInitiator); feed(eResponder)
        val out = ByteArray(digest.digestSize)
        digest.doFinal(out, 0)
        return out
    }

    private fun deriveKeys(es: ByteArray, ee: ByteArray, se: ByteArray, transcript: ByteArray, isInitiator: Boolean): Result {
        val ikm = es + ee + se
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(ikm, transcript, "ramka-ik-root".toByteArray()))

        val keyAtoB = ByteArray(32) // ключ для направления "инициатор -> ответчик"
        val keyBtoA = ByteArray(32) // ключ для направления "ответчик -> инициатор"
        hkdf.generateBytes(keyAtoB, 0, 32)
        hkdf.generateBytes(keyBtoA, 0, 32)

        ikm.fill(0); es.fill(0); ee.fill(0); se.fill(0)

        return if (isInitiator) Result(sendKey = keyAtoB, recvKey = keyBtoA)
        else Result(sendKey = keyBtoA, recvKey = keyAtoB)
    }
}
