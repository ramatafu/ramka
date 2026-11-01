package com.ramka.crypto.offline

import com.ramka.crypto.keys.KeyManager
import com.ramka.crypto.padding.FramePadding
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
 * Односторонняя «запечатанная» посылка для офлайн-доставки (store-and-forward, например письмо Yggmail), по мотивам
 * паттерна Noise_X (`<- s … -> e, es, s, ss`). Как и [com.ramka.crypto.noise.IkHandshake], это ручная композиция
 * ПРОВЕРЕННЫХ примитивов Bouncy Castle (X25519, HKDF/SHA-256, ChaCha20-Poly1305), а не новый алгоритм
 * (PROJECT_SPEC.md 6.2). Ни один существующий файл криптоядра не менялся.
 *
 * Зачем отдельная схема: IK-рукопожатие требует трёх сообщений с живым собеседником, а получатель письма офлайн,
 * поэтому нужна схема из ОДНОГО сообщения, которое читает только получатель и по которому он узнаёт отправителя.
 *
 * Формат (получателю известен его статический ключ, отправителю — статический ключ получателя из контакта):
 *
 *   sealed = e_pub (32) || AEAD(k1, s_pub) (32 + 16) || AEAD(k2, FramePadding(plaintext)) (padded + 16)
 *
 *   es = DH(e_priv, rs_pub)         — эфемерный ключ отправителя и статический ключ получателя
 *   ss = DH(s_priv, rs_pub)         — статические ключи обеих сторон: ЭТО и подтверждает отправителя
 *   k1 = HKDF(es,       salt = e_pub || rs_pub, info = "ramka-x-v1-s")
 *   k2 = HKDF(es || ss, salt = transcript,      info = "ramka-x-v1-p")
 *   transcript = SHA256("ramka-x-v1" || s_pub || rs_pub || e_pub)
 *   AEAD = ChaCha20-Poly1305, nonce = 12 нулевых байт (k1 и k2 ОДНОРАЗОВЫЕ: выведены из свежего эфемерного ключа и
 *          своего info — то же рассуждение, что у IkHandshake), aad = e_pub || rs_pub для k1 и transcript для k2.
 *
 * Свойства: открыть может только владелец приватного ключа получателя; статический ключ отправителя не виден
 * наблюдателю (зашифрован под es); расшифровка тела удаётся только при знании приватного ключа отправителя
 * (ss), поэтому «отправитель» из результата подтверждён; выравнивание как у обычных кадров, длина письма зависит
 * только от корзины [FramePadding]. Любая ошибка даёт null — без различия причин.
 *
 * ИЗВЕСТНЫЕ ОГРАНИЧЕНИЯ (записаны в DEVIATIONS.md, O-4): нет forward secrecy относительно долговременного ключа
 * получателя; при компрометации приватного ключа получателя можно подделывать письма «от» любого контакта (KCI);
 * повтор письма не отбрасывается здесь (схема без состояния) — дубликаты гасит приём по `remoteMessageId`.
 */
object OfflineSeal {

    private const val LABEL = "ramka-x-v1"
    private const val INFO_STATIC = "ramka-x-v1-s"
    private const val INFO_PAYLOAD = "ramka-x-v1-p"
    private const val EPHEMERAL_KEY_SIZE = 32
    private const val STATIC_KEY_SIZE = 32
    private const val TAG_SIZE = 16

    /** e_pub (32) || AEAD(s_pub) (32 + 16) = 80 байт. */
    const val HEADER_SIZE = EPHEMERAL_KEY_SIZE + STATIC_KEY_SIZE + TAG_SIZE

    /** Верхняя граница открытого пакета: только короткие сообщения (см. OfflineEligibility в domain). */
    const val MAX_PLAINTEXT_SIZE = 16 * 1024

    /** Результат [open]: подтверждённый статический ключ отправителя и открытый пакет. */
    class Opened(val senderStaticPublic: ByteArray, val plaintext: ByteArray)

    /** Размер запечатанного пакета для открытого пакета длиной [plaintextSize]. */
    fun sealedSize(plaintextSize: Int): Int = HEADER_SIZE + FramePadding.paddedSize(plaintextSize) + TAG_SIZE

    /**
     * Запечатывает [plaintext] для владельца [recipientStaticPublic] от имени устройства [keyManager].
     * null — ключ получателя некорректен (длина, low-order точка) или пакет больше [MAX_PLAINTEXT_SIZE].
     */
    fun seal(keyManager: KeyManager, recipientStaticPublic: ByteArray, plaintext: ByteArray): ByteArray? {
        if (recipientStaticPublic.size != STATIC_KEY_SIZE || plaintext.size > MAX_PLAINTEXT_SIZE) return null
        return try {
            val senderPublic = keyManager.getOrCreateIdentity().x25519Public
            val ephemeral = keyManager.generateEphemeralX25519()
            val ephemeralPublic = ephemeral.generatePublicKey().encoded

            val es = try {
                dh(ephemeral, recipientStaticPublic)
            } catch (e: IllegalStateException) {
                return null // low-order / некорректный статический ключ получателя
            }
            val ss = try {
                dh(keyManager.loadX25519Private(), recipientStaticPublic)
            } catch (e: IllegalStateException) {
                es.fill(0)
                return null
            }

            val aadStatic = ephemeralPublic + recipientStaticPublic
            val k1 = hkdf(es, aadStatic, INFO_STATIC)
            val sealedStatic = aeadSeal(k1, aadStatic, senderPublic)
            k1.fill(0)

            val transcript = transcript(senderPublic, recipientStaticPublic, ephemeralPublic)
            val ikm = es + ss
            es.fill(0); ss.fill(0)
            val k2 = hkdf(ikm, transcript, INFO_PAYLOAD)
            ikm.fill(0)
            val padded = FramePadding.pad(plaintext)
            val sealedPayload = aeadSeal(k2, transcript, padded)
            k2.fill(0); padded.fill(0)

            ephemeralPublic + sealedStatic + sealedPayload
        } catch (e: RuntimeException) {
            null
        }
    }

    /**
     * Вскрывает пакет устройством [keyManager]. null — любая ошибка (длина, чужой адресат, порча, подмена, некорректная
     * точка): причины не различаются. Исключений не бросает.
     */
    fun open(keyManager: KeyManager, sealed: ByteArray): Opened? {
        val paddedSize = sealed.size - HEADER_SIZE - TAG_SIZE
        if (paddedSize < 0 || !FramePadding.isValidPaddedSize(paddedSize)) return null
        return try {
            val ephemeralPublic = sealed.copyOfRange(0, EPHEMERAL_KEY_SIZE)
            val sealedStatic = sealed.copyOfRange(EPHEMERAL_KEY_SIZE, HEADER_SIZE)
            val sealedPayload = sealed.copyOfRange(HEADER_SIZE, sealed.size)

            val myPublic = keyManager.getOrCreateIdentity().x25519Public
            val myPrivate = keyManager.loadX25519Private()

            val es = try {
                dh(myPrivate, ephemeralPublic)
            } catch (e: IllegalStateException) {
                return null // low-order эфемерная точка
            }
            val aadStatic = ephemeralPublic + myPublic
            val k1 = hkdf(es, aadStatic, INFO_STATIC)
            val senderPublic = aeadOpen(k1, aadStatic, sealedStatic)
            k1.fill(0)
            if (senderPublic == null || senderPublic.size != STATIC_KEY_SIZE) {
                es.fill(0)
                return null
            }

            val ss = try {
                dh(myPrivate, senderPublic)
            } catch (e: IllegalStateException) {
                es.fill(0)
                return null // low-order «статический ключ отправителя»
            }
            val transcript = transcript(senderPublic, myPublic, ephemeralPublic)
            val ikm = es + ss
            es.fill(0); ss.fill(0)
            val k2 = hkdf(ikm, transcript, INFO_PAYLOAD)
            ikm.fill(0)
            val padded = aeadOpen(k2, transcript, sealedPayload)
            k2.fill(0)
            if (padded == null) return null

            val unpadded = FramePadding.unpad(padded)
            padded.fill(0)
            if (unpadded == null || unpadded.flags != FramePadding.FLAG_NONE) return null
            Opened(senderPublic, unpadded.payload)
        } catch (e: RuntimeException) {
            null
        }
    }

    // ---------- примитивы (как в IkHandshake) ----------

    private fun dh(privateKey: X25519PrivateKeyParameters, publicKeyBytes: ByteArray): ByteArray {
        val agreement = X25519Agreement()
        agreement.init(privateKey)
        val out = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(X25519PublicKeyParameters(publicKeyBytes, 0), out, 0)
        return out
    }

    private fun hkdf(ikm: ByteArray, salt: ByteArray, info: String): ByteArray {
        val generator = HKDFBytesGenerator(SHA256Digest())
        generator.init(HKDFParameters(ikm, salt, info.toByteArray()))
        val key = ByteArray(32)
        generator.generateBytes(key, 0, 32)
        return key
    }

    private fun transcript(senderStatic: ByteArray, recipientStatic: ByteArray, ephemeral: ByteArray): ByteArray {
        val digest = SHA256Digest()
        fun feed(b: ByteArray) = digest.update(b, 0, b.size)
        feed(LABEL.toByteArray())
        feed(senderStatic); feed(recipientStatic); feed(ephemeral)
        val out = ByteArray(digest.digestSize)
        digest.doFinal(out, 0)
        return out
    }

    /** ChaCha20-Poly1305 с нулевым nonce: допустимо только для ОДНОРАЗОВЫХ ключей (k1, k2: каждый — ровно раз). */
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
}
