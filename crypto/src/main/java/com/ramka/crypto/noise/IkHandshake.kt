package com.ramka.crypto.noise

import com.ramka.crypto.keys.Ed25519Verification
import com.ramka.crypto.keys.KeyManager
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
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
 * Протокол (3 сообщения):
 *   I -> R: senderStaticX25519Pub || e_i_pub                         (Message1)
 *   R -> I: e_r_pub || Sign_R(transcript)                            (Message2)
 *   I -> R: Sign_I(transcript)                                       (Message3)
 *
 *   transcript = SHA256(label || s_i_pub || s_r_pub || e_i_pub || e_r_pub)
 *   root = HKDF(es || ee || se, salt=transcript, info="ramka-ik-root")
 *
 * ОТКРЫТЫЙ ПУНКТ (зафиксирован в DEVIATIONS.md): senderStaticX25519Pub передаётся в
 * Message1 в открытом виде (не зашифрован временным ключом от es, как в канонической
 * IK) — на LAN между уже добавленными контактами это не раскрывает ничего, чего
 * получатель не знал бы после сопоставления с локальным списком контактов, но
 * скрытие идентичности инициатора от пассивного наблюдателя сети — предмет
 * последующего аудита, не блокирует MVP этапа 1.
 */
object IkHandshake {

    private const val LABEL = "ramka-ik-v1"
    const val EPHEMERAL_KEY_SIZE = 32
    const val SIGNATURE_SIZE = 64

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
        val message1: ByteArray = localStaticPublic + ephemeralPrivate.generatePublicKey().encoded

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

    /** Принимает Message1 от инициатора и начинает сессию ответчика. */
    fun startResponder(keyManager: KeyManager, message1: ByteArray): ResponderSession? {
        if (message1.size != 32 + EPHEMERAL_KEY_SIZE) return null
        val remoteStaticPublic = message1.copyOfRange(0, 32)
        val remoteEphemeralPublic = message1.copyOfRange(32, message1.size)

        val identity = keyManager.getOrCreateIdentity()
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
