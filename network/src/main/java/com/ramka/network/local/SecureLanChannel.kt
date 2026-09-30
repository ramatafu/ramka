package com.ramka.network.local

import com.ramka.crypto.keys.KeyManager
import com.ramka.crypto.noise.IkHandshake
import com.ramka.crypto.ratchet.SessionCipher
import com.ramka.network.protocol.Frame
import com.ramka.network.protocol.FrameType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * Единственная точка отправки/приёма сообщений в локальной сети (этап 1, п. 3.6).
 *
 * Каждое соединение — это полное IK-рукопожатие (см. [IkHandshake]) с проверкой
 * подписи транскрипта Ed25519 обеих сторон, за которым следует ровно одно
 * зашифрованное сообщение. Повторное использование соединения для нескольких
 * сообщений (чтобы не делать рукопожатие на каждое сообщение) — оптимизация
 * следующего этапа, зафиксирована в DEVIATIONS.md, безопасности не касается.
 *
 * Неизвестные отправители (чей статический ключ не находится через [lookupSigningKey])
 * отклоняются до вычисления сессионных ключей — п. 3.8 спецификации.
 */
class SecureLanChannel(
    private val keyManager: KeyManager,
    private val port: Int,
    private val lookupSigningKey: suspend (remoteStaticX25519: ByteArray) -> ByteArray?
) {
    private var serverSocket: ServerSocket? = null

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
                    val result = runCatching { handleIncomingConnection(client) }.getOrNull()
                    if (result != null) trySend(result)
                }
            }
        }
        awaitClose {
            job.cancel()
            server.close()
        }
    }

    private suspend fun handleIncomingConnection(socket: Socket): IncomingMessage? = socket.use {
        val input = DataInputStream(it.getInputStream())
        val output = DataOutputStream(it.getOutputStream())

        val message1 = FrameIo.read(input)
        require(message1.type == FrameType.HANDSHAKE_1) { "Ожидался HANDSHAKE_1" }
        val responderSession = IkHandshake.startResponder(keyManager, message1.payload) ?: return null

        // Неизвестный отправитель — отклоняем ДО завершения рукопожатия (п. 3.8).
        val remoteSigningKey = lookupSigningKey(responderSession.remoteStaticPublic) ?: return null

        FrameIo.write(output, Frame(FrameType.HANDSHAKE_2, responderSession.buildMessage2(keyManager)))

        val message3 = FrameIo.read(input)
        require(message3.type == FrameType.HANDSHAKE_3) { "Ожидался HANDSHAKE_3" }
        val handshakeResult = responderSession.consumeMessage3(message3.payload, remoteSigningKey, keyManager)
            ?: return null // подпись не сошлась — вероятная MITM-атака

        val sessionCipher = SessionCipher(handshakeResult.sendKey, handshakeResult.recvKey)

        val encryptedFrame = FrameIo.read(input)
        require(encryptedFrame.type == FrameType.ENCRYPTED_MESSAGE) { "Ожидалось зашифрованное сообщение" }
        val counter = readCounter(encryptedFrame.payload)
        val ciphertext = encryptedFrame.payload.copyOfRange(8, encryptedFrame.payload.size)
        val plaintext = sessionCipher.decryptIfFresh(counter, ciphertext) ?: return null

        sessionCipher.wipe()
        IncomingMessage(senderStaticX25519 = responderSession.remoteStaticPublic, plaintext = plaintext)
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
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMillis)
                socket.soTimeout = timeoutMillis
                val input = DataInputStream(socket.getInputStream())
                val output = DataOutputStream(socket.getOutputStream())

                val initiatorSession = IkHandshake.startInitiator(keyManager, remoteStaticX25519)
                FrameIo.write(output, Frame(FrameType.HANDSHAKE_1, initiatorSession.message1))

                val message2 = FrameIo.read(input)
                if (message2.type != FrameType.HANDSHAKE_2) return@withContext false
                val (mySignature, handshakeResult) = initiatorSession.consumeMessage2(
                    message2.payload, remoteSigningPublicKey, keyManager
                ) ?: return@withContext false // подпись собеседника не сошлась — отменяем отправку

                FrameIo.write(output, Frame(FrameType.HANDSHAKE_3, mySignature))

                val sessionCipher = SessionCipher(handshakeResult.sendKey, handshakeResult.recvKey)
                val sent = sessionCipher.encryptNext(plaintext)
                val payload = writeCounter(sent.counter) + sent.ciphertext
                FrameIo.write(output, Frame(FrameType.ENCRYPTED_MESSAGE, payload))
                sessionCipher.wipe()
                true
            }
        } catch (e: SocketTimeoutException) {
            false
        } catch (e: Exception) {
            // Собеседник оффлайн/недостижим — сообщение остаётся в очереди отправителя (п. 3.2).
            false
        }
    }

    fun stop() {
        serverSocket?.close()
    }

    private fun readCounter(payload: ByteArray): Long {
        var value = 0L
        for (i in 0 until 8) value = (value shl 8) or (payload[i].toLong() and 0xFF)
        return value
    }

    private fun writeCounter(counter: Long): ByteArray {
        val out = ByteArray(8)
        for (i in 0 until 8) out[7 - i] = ((counter shr (8 * i)) and 0xFF).toByte()
        return out
    }
}

data class IncomingMessage(val senderStaticX25519: ByteArray, val plaintext: ByteArray)
