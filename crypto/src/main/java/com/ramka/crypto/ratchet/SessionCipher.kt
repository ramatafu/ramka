package com.ramka.crypto.ratchet

/**
 * Состояние одной установленной сессии (одного TCP-соединения) после успешного
 * IK-рукопожатия. Отдельные счётчики на отправку и приём — счётчик начинается
 * с нуля на каждое новое соединение (свежий ключ — свежий счётчик).
 *
 * Replay protection: входящий пакет с номером <= последнего принятого номера
 * отклоняется (строгое возрастание). Этого достаточно в рамках одной TCP-сессии
 * с надёжной доставкой по порядку; окно с битовой маской для приёма "не по
 * порядку" пакетов — задача этапа, где появится ненадёжный/переупорядочивающий
 * транспорт (см. DEVIATIONS.md).
 */
class SessionCipher(private val sendKey: ByteArray, private val recvKey: ByteArray) {

    private var sendCounter = 0L
    private var lastAcceptedRecvCounter = -1L

    fun encryptNext(plaintext: ByteArray): SentFrame {
        val counter = sendCounter
        val ciphertext = MessageCipher.encrypt(sendKey, counter, plaintext)
        sendCounter += 1
        return SentFrame(counter, ciphertext)
    }

    /** Возвращает расшифрованные данные либо null, если пакет повторный или не прошёл проверку подлинности. */
    fun decryptIfFresh(counter: Long, ciphertext: ByteArray): ByteArray? {
        if (counter <= lastAcceptedRecvCounter) return null // повтор — отбрасываем (replay protection)
        val plaintext = runCatching { MessageCipher.decrypt(recvKey, counter, ciphertext) }.getOrNull()
            ?: return null
        lastAcceptedRecvCounter = counter
        return plaintext
    }

    fun wipe() {
        sendKey.fill(0); recvKey.fill(0)
    }
}

data class SentFrame(val counter: Long, val ciphertext: ByteArray)
