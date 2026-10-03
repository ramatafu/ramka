package com.ramka.network.local

import com.ramka.crypto.keys.KeyManager
import com.ramka.crypto.noise.IkHandshake
import com.ramka.crypto.padding.FramePadding
import com.ramka.crypto.ratchet.SessionCipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * Единственная точка отправки/приёма сообщений в локальной сети (этап 1, п. 3.6).
 *
 * Каждое соединение — это полное IK-рукопожатие (см. [IkHandshake]) с проверкой
 * подписи транскрипта Ed25519 обеих сторон, за которым следует ровно одно
 * зашифрованное сообщение. Тело сообщения перед шифрованием выравнивается до
 * фиксированного размера ([FramePadding], этап 2.5), чтобы длина кадра не выдавала
 * тип и размер сообщения; при приёме тело, не прошедшее проверку формата, и
 * фиктивные тела ([FramePadding.FLAG_COVER]) молча отбрасываются. Формат несовместим
 * со старым (без выравнивания) — оба устройства должны быть обновлены одновременно.
 * Повторное использование соединения для нескольких
 * сообщений (чтобы не делать рукопожатие на каждое сообщение) — оптимизация
 * следующего этапа, зафиксирована в DEVIATIONS.md, безопасности не касается.
 *
 * Проводной формат (этап 2.5, шаг 3) описан в [FrameIo]: кадры рукопожатия без типа и длины
 * (80/112/80 байт, роль определяется состоянием автомата), кадр данных без типа, с длиной
 * шифртекста строго по корзинам [FramePadding] и без счётчика (номер пакета неявный, всегда 0). Ответчик ограничивает рукопожатие общим
 * дедлайном ([handshakeTimeoutMillis]) и простоем при чтении кадра данных
 * ([dataIdleTimeoutMillis]); любая ошибка (мусор, таймаут, обрыв, провал AEAD или подписи)
 * приводит к одинаковому закрытию сокета без ответа.
 *
 * Неизвестные отправители (чей статический ключ не находится через [lookupSigningKey])
 * отклоняются до вычисления сессионных ключей — п. 3.8 спецификации.
 */
class SecureLanChannel(
    private val keyManager: KeyManager,
    private val port: Int,
    private val handshakeTimeoutMillis: Long = HANDSHAKE_TIMEOUT_MILLIS,
    private val dataIdleTimeoutMillis: Int = DATA_IDLE_TIMEOUT_MILLIS,
    private val lookupSigningKey: suspend (remoteStaticX25519: ByteArray) -> ByteArray?
) : TunnelMessageChannel {
    private var serverSocket: ServerSocket? = null

    companion object {
        /** Общий лимит на приём M1 и M3 (и ожидание проверки контакта между ними). */
        const val HANDSHAKE_TIMEOUT_MILLIS = 10_000L

        /** Простой при чтении кадра данных после рукопожатия (соединение одноразовое, отправитель шлёт сразу). */
        const val DATA_IDLE_TIMEOUT_MILLIS = 30_000
    }

    fun incomingMessages(scope: CoroutineScope): Flow<IncomingMessage> = callbackFlow {
        val server = ServerSocket(port).also { serverSocket = it }
        val job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val client = try {
                    server.accept()
                } catch (e: Exception) {
                    if (server.isClosed) break else continue
                }
                launch(Dispatchers.IO) {
                    val result = runCatching { processIncomingSocket(client) }.getOrNull()
                    if (result != null) trySend(result)
                }
            }
        }
        awaitClose {
            job.cancel()
            server.close()
        }
    }

    /**
     * Обрабатывает ОДНО входящее соединение уже установленным сокетом: рукопожатие (роль ответчика),
     * проверка подписи, расшифровка, снятие выравнивания. Сокет закрывается всегда ([use]).
     * Возвращает `null` для любого сбоя и для фиктивных пакетов — вызывающий ничего не отвечает.
     *
     * Используется и LAN-слушателем ([incomingMessages]), и транспортом через релей: сокет, пришедший
     * после ACCEPT/READY, — такая же «труба» до отправителя (RELAY_PROTOCOL.md §4.2), формат тот же.
     */
    override suspend fun processIncomingSocket(socket: Socket): IncomingMessage? = socket.use {
        val input = it.getInputStream()
        val output = it.getOutputStream()
        val handshakeDeadline = System.nanoTime() + handshakeTimeoutMillis * 1_000_000L

        // Состояние AWAIT_M1: ровно 80 байт, без типа и длины.
        val message1 = FrameIo.readExact(it, input, FrameIo.MESSAGE1_SIZE, handshakeDeadline)
        val responderSession = IkHandshake.startResponder(keyManager, message1) ?: return null

        // Неизвестный отправитель — отклоняем ДО завершения рукопожатия (п. 3.8).
        val remoteSigningKey = lookupSigningKey(responderSession.remoteStaticPublic) ?: return null

        FrameIo.writeHandshake(output, responderSession.buildMessage2(keyManager), FrameIo.MESSAGE2_SIZE)

        // Состояние AWAIT_M3: ровно 80 байт, тот же общий дедлайн.
        val message3 = FrameIo.readExact(it, input, FrameIo.MESSAGE3_SIZE, handshakeDeadline)
        val handshakeResult = responderSession.consumeMessage3(message3, remoteSigningKey, keyManager)
            ?: return null // подпись не сошлась — вероятная MITM-атака

        val sessionCipher = SessionCipher(handshakeResult.sendKey, handshakeResult.recvKey)

        // Состояние TRANSPORT: один кадр данных; недопустимая длина -> исключение -> закрытие.
        it.soTimeout = dataIdleTimeoutMillis
        val ciphertext = FrameIo.readData(it, input)
        val padded = sessionCipher.decryptIfFresh(FrameIo.IMPLICIT_COUNTER, ciphertext) ?: return null

        sessionCipher.wipe()

        // Формат выравнивания нарушен — отбрасываем. Фиктивный пакет (этап 2.5, шаг 7) тоже молча отбрасывается.
        val unpadded = FramePadding.unpad(padded) ?: return null
        if ((unpadded.flags and FramePadding.FLAG_COVER) != 0) return null

        IncomingMessage(senderStaticX25519 = responderSession.remoteStaticPublic, plaintext = unpadded.payload)
    }

    /** Устанавливает соединение с известным контактом, проводит рукопожатие и отправляет одно сообщение. */
    suspend fun sendMessage(
        host: String,
        port: Int,
        remoteStaticX25519: ByteArray,
        remoteSigningPublicKey: ByteArray,
        plaintext: ByteArray,
        timeoutMillis: Int = 5000
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Тело длиннее FramePadding.MAX_PAYLOAD_SIZE даёт IllegalArgumentException и
            // попадает в catch ниже (false) ДО открытия соединения.
            val body = FramePadding.pad(plaintext)
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMillis)
                exchangeOverSocket(socket, remoteStaticX25519, remoteSigningPublicKey, body, timeoutMillis)
            }
        } catch (e: SocketTimeoutException) {
            false
        } catch (e: Exception) {
            // Собеседник оффлайн/недостижим — сообщение остаётся в очереди отправителя (п. 3.2).
            false
        }
    }

    /**
     * То же, что [sendMessage], но по УЖЕ установленному сокету до собеседника (например, «трубе»
     * через релей, этап 3). Рукопожатие, проверки и формат кадров те же; E2E-шифрование сохраняется,
     * релей видит только непрозрачные байты. Сокет НЕ закрывается — им владеет вызывающий.
     */
    override suspend fun sendMessageOver(
        socket: Socket,
        remoteStaticX25519: ByteArray,
        remoteSigningPublicKey: ByteArray,
        plaintext: ByteArray,
        timeoutMillis: Int // значение по умолчанию (5000) задано в TunnelMessageChannel
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = FramePadding.pad(plaintext)
            exchangeOverSocket(socket, remoteStaticX25519, remoteSigningPublicKey, body, timeoutMillis)
        } catch (e: SocketTimeoutException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    /** Общая часть отправки: рукопожатие (инициатор) и один кадр данных по подключённому сокету. */
    private fun exchangeOverSocket(
        socket: Socket,
        remoteStaticX25519: ByteArray,
        remoteSigningPublicKey: ByteArray,
        body: ByteArray,
        timeoutMillis: Int
    ): Boolean {
        socket.soTimeout = timeoutMillis
        val input = socket.getInputStream()
        val output = socket.getOutputStream()

        val initiatorSession = IkHandshake.startInitiator(keyManager, remoteStaticX25519)
        FrameIo.writeHandshake(output, initiatorSession.message1, FrameIo.MESSAGE1_SIZE)

        // Ожидается M2: ровно 112 байт (таймаут чтения — soTimeout выше).
        val message2 = FrameIo.readExact(socket, input, FrameIo.MESSAGE2_SIZE)
        val (mySignature, handshakeResult) = initiatorSession.consumeMessage2(
            message2, remoteSigningPublicKey, keyManager
        ) ?: return false // подпись собеседника не сошлась — отменяем отправку

        FrameIo.writeHandshake(output, mySignature, FrameIo.MESSAGE3_SIZE)

        val sessionCipher = SessionCipher(handshakeResult.sendKey, handshakeResult.recvKey)
        val sent = sessionCipher.encryptNext(body)
        // Номер на провод не идёт, получатель подставит IMPLICIT_COUNTER: если это не первый
        // пакет сессии, nonce не совпадёт — лучше отменить отправку (исключение -> false).
        check(sent.counter == FrameIo.IMPLICIT_COUNTER) { "Ожидался первый пакет сессии" }
        FrameIo.writeData(output, sent.ciphertext)
        sessionCipher.wipe()
        return true
    }

    fun stop() {
        serverSocket?.close()
    }
}

data class IncomingMessage(val senderStaticX25519: ByteArray, val plaintext: ByteArray)
