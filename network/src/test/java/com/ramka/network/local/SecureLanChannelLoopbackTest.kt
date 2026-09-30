package com.ramka.network.local

import com.ramka.crypto.keys.DeviceIdentity
import com.ramka.crypto.keys.KeyManager
import com.ramka.crypto.noise.IkHandshake
import com.ramka.crypto.padding.FramePadding
import com.ramka.crypto.ratchet.SessionCipher
import com.ramka.network.protocol.Frame
import com.ramka.network.protocol.FrameType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Loopback-тесты [SecureLanChannel] (этап 2.5, шаг 2): реальные сокеты на 127.0.0.1, реальное
 * IK-рукопожатие, in-memory ключи. Без Android.
 */
class SecureLanChannelLoopbackTest {

    private val host = "127.0.0.1"

    private fun newKeyManager() = KeyManager(TestKeyValueStore())

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private fun bytes(size: Int): ByteArray = ByteArray(size) { ((it % 251) + 1).toByte() }

    /** Ждём, пока серверный сокет начнёт принимать соединения (callbackFlow биндует его лениво). */
    private fun waitUntilListening(port: Int) {
        val deadline = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < deadline) {
            try {
                Socket().use { it.connect(InetSocketAddress(host, port), 200) }
                return
            } catch (e: Exception) {
                Thread.sleep(20)
            }
        }
        throw AssertionError("Сервер не начал слушать порт $port")
    }

    /** Поднимает приёмник у [receiver], знающий только [sender] как контакт, и выполняет [block]. */
    private fun <T> withReceiver(
        receiver: KeyManager,
        sender: KeyManager,
        port: Int,
        block: suspend (received: Channel<IncomingMessage>) -> T
    ): T = runBlocking {
        val senderIdentity = sender.getOrCreateIdentity()
        val channel = SecureLanChannel(receiver, port) { staticKey ->
            if (staticKey.contentEquals(senderIdentity.x25519Public)) senderIdentity.ed25519Public else null
        }
        val received = Channel<IncomingMessage>(Channel.UNLIMITED)
        // Отдельный скоуп на Dispatchers.IO: тело теста блокирует поток runBlocking (sleep/connect),
        // на нём приёмник не смог бы работать.
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        channel.incomingMessages(scope).onEach { received.send(it) }.launchIn(scope)
        try {
            waitUntilListening(port)
            block(received)
        } finally {
            scope.cancel()
            channel.stop()
        }
    }

    private suspend fun Channel<IncomingMessage>.receiveWithin(millis: Long = 3000): IncomingMessage =
        withTimeout(millis) { receive() }

    private fun sendVia(sender: KeyManager, receiver: DeviceIdentity, port: Int, plaintext: ByteArray): Boolean =
        runBlocking {
            SecureLanChannel(sender, freePort()) { null }
                .sendMessage(host, port, receiver.x25519Public, receiver.ed25519Public, plaintext)
        }

    /** Клиент, отправляющий в зашифрованном кадре произвольное [body] как есть (без выравнивания). */
    private fun sendRawBody(sender: KeyManager, receiver: DeviceIdentity, port: Int, body: ByteArray): Boolean =
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 3000)
            socket.soTimeout = 3000
            val input = DataInputStream(socket.getInputStream())
            val output = DataOutputStream(socket.getOutputStream())

            val session = IkHandshake.startInitiator(sender, receiver.x25519Public)
            FrameIo.write(output, Frame(FrameType.HANDSHAKE_1, session.message1))
            val message2 = FrameIo.read(input)
            val (signature, result) = session.consumeMessage2(message2.payload, receiver.ed25519Public, sender)
                ?: return@use false
            FrameIo.write(output, Frame(FrameType.HANDSHAKE_3, signature))

            val cipher = SessionCipher(result.sendKey, result.recvKey)
            val sent = cipher.encryptNext(body)
            FrameIo.write(output, Frame(FrameType.ENCRYPTED_MESSAGE, ByteArray(8) + sent.ciphertext))
            true
        }

    /** TCP-прокси, записывающий байты направления «клиент -> сервер», чтобы смотреть размеры кадров на проводе. */
    private class RecordingProxy(private val targetPort: Int) {
        private val server = ServerSocket(0)
        val port: Int = server.localPort
        private val lock = Any()
        private val recorded = ByteArrayOutputStream()

        init {
            thread(isDaemon = true) {
                while (!server.isClosed) {
                    val client = try { server.accept() } catch (e: Exception) { break }
                    val upstream = Socket(HOST, targetPort)
                    pipe(client, upstream, record = true)
                    pipe(upstream, client, record = false)
                }
            }
        }

        private fun pipe(from: Socket, to: Socket, record: Boolean) = thread(isDaemon = true) {
            try {
                val buf = ByteArray(4096)
                val input = from.getInputStream()
                val output = to.getOutputStream()
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (record) synchronized(lock) { recorded.write(buf, 0, n) }
                    output.write(buf, 0, n)
                    output.flush()
                }
                to.shutdownOutput()
            } catch (e: Exception) {
                // соединение закрыто — для теста это нормально
            }
        }

        /** Копия всего записанного потока «клиент -> сервер» без сброса. */
        fun snapshot(): ByteArray = synchronized(lock) { recorded.toByteArray() }

        /** Длины кадров (поле длины, без самих 4 байт) в записанном потоке; запись сбрасывается. */
        fun takeFrameLengths(): List<Int> {
            val bytes = synchronized(lock) { recorded.toByteArray().also { recorded.reset() } }
            val din = DataInputStream(ByteArrayInputStream(bytes))
            val lengths = mutableListOf<Int>()
            while (din.available() > 0) {
                val length = din.readInt()
                lengths += length
                din.skipBytes(length)
            }
            return lengths
        }

        fun close() = server.close()

        private companion object { const val HOST = "127.0.0.1" }
    }

    @Test
    fun `messages of different sizes arrive unchanged through padding`() {
        val receiver = newKeyManager()
        val sender = newKeyManager()
        val port = freePort()
        withReceiver(receiver, sender, port) { received ->
            for (size in listOf(0, 1, 37, 123, 124, 500, 5000)) {
                val plaintext = bytes(size)
                assertTrue("size=$size", sendVia(sender, receiver.getOrCreateIdentity(), port, plaintext))
                val message = received.receiveWithin()
                assertArrayEquals("size=$size", plaintext, message.plaintext)
                assertArrayEquals(sender.getOrCreateIdentity().x25519Public, message.senderStaticX25519)
            }
        }
    }

    @Test
    fun `data frame size on the wire depends only on the bucket`() {
        val receiver = newKeyManager()
        val sender = newKeyManager()
        val port = freePort()
        val proxy = RecordingProxy(port)
        try {
            withReceiver(receiver, sender, port) { received ->
                fun dataFrameLength(plaintextSize: Int): Int {
                    assertTrue(sendVia(sender, receiver.getOrCreateIdentity(), proxy.port, bytes(plaintextSize)))
                    runBlocking { received.receiveWithin() }
                    val frames = proxy.takeFrameLengths()
                    assertEquals("M1, M3 и кадр данных", 3, frames.size)
                    assertEquals("M1: тип (1) + 80 байт", 1 + 80, frames[0])
                    return frames[2]
                }

                val ackSized = dataFrameLength(37)      // размер DELIVERED-ACK
                val shortText = dataFrameLength(37 + 40)
                val fullestSmallBucket = dataFrameLength(FramePadding.BUCKETS[0] - FramePadding.HEADER_SIZE)
                val nextBucket = dataFrameLength(FramePadding.BUCKETS[0] - FramePadding.HEADER_SIZE + 1)

                // тип (1) + счётчик (8) + тело + тег Poly1305 (16)
                val overhead = 1 + 8 + 16
                assertEquals(FramePadding.BUCKETS[0] + overhead, ackSized)
                assertEquals(ackSized, shortText)
                assertEquals(ackSized, fullestSmallBucket)
                assertEquals(FramePadding.BUCKETS[1] + overhead, nextBucket)
            }
        } finally {
            proxy.close()
        }
    }

    @Test
    fun `initiator static key never appears on the wire`() {
        val receiver = newKeyManager()
        val sender = newKeyManager()
        val port = freePort()
        val proxy = RecordingProxy(port)
        try {
            withReceiver(receiver, sender, port) { received ->
                assertTrue(sendVia(sender, receiver.getOrCreateIdentity(), proxy.port, bytes(37)))
                received.receiveWithin()

                val wire = proxy.snapshot()
                val senderStatic = sender.getOrCreateIdentity().x25519Public
                val matches = (0..wire.size - senderStatic.size).any { i ->
                    senderStatic.indices.all { j -> wire[i + j] == senderStatic[j] }
                }
                assertTrue("записано ${wire.size} байт", wire.isNotEmpty())
                assertEquals("s_i найден в потоке клиента", false, matches)
            }
        } finally {
            proxy.close()
        }
    }

    @Test
    fun `body without padding from an old client is dropped and receiver keeps working`() {
        val receiver = newKeyManager()
        val sender = newKeyManager()
        val port = freePort()
        withReceiver(receiver, sender, port) { received ->
            assertTrue(sendRawBody(sender, receiver.getOrCreateIdentity(), port, bytes(37)))
            assertNull(withTimeoutOrNull(700) { received.receive() })

            val ok = bytes(10)
            assertTrue(sendVia(sender, receiver.getOrCreateIdentity(), port, ok))
            assertArrayEquals(ok, received.receiveWithin().plaintext)
        }
    }

    @Test
    fun `cover flagged body is dropped silently`() {
        val receiver = newKeyManager()
        val sender = newKeyManager()
        val port = freePort()
        withReceiver(receiver, sender, port) { received ->
            val cover = FramePadding.pad(bytes(10), FramePadding.FLAG_COVER)
            assertTrue(sendRawBody(sender, receiver.getOrCreateIdentity(), port, cover))
            assertNull(withTimeoutOrNull(700) { received.receive() })

            val ok = bytes(11)
            assertTrue(sendVia(sender, receiver.getOrCreateIdentity(), port, ok))
            assertArrayEquals(ok, received.receiveWithin().plaintext)
        }
    }

    @Test
    fun `non canonical padding is dropped`() {
        val receiver = newKeyManager()
        val sender = newKeyManager()
        val port = freePort()
        withReceiver(receiver, sender, port) { received ->
            val tooLong = FramePadding.pad(bytes(10)) + ByteArray(2) // 130 байт — не корзина
            assertTrue(sendRawBody(sender, receiver.getOrCreateIdentity(), port, tooLong))
            assertNull(withTimeoutOrNull(700) { received.receive() })
        }
    }

    @Test
    fun `oversized payload is rejected before connecting`() {
        val sender = newKeyManager()
        val receiver = newKeyManager().getOrCreateIdentity()
        // Порт, на котором никто не слушает: если бы отправка дошла до connect, результат был бы тем же false,
        // поэтому проверяем только контракт «не бросает и возвращает false».
        val tooBig = ByteArray(FramePadding.MAX_PAYLOAD_SIZE + 1)
        assertEquals(false, sendVia(sender, receiver, freePort(), tooBig))
    }
}
