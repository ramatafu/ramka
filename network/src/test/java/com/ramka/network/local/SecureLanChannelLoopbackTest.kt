package com.ramka.network.local

import com.ramka.crypto.keys.DeviceIdentity
import com.ramka.crypto.keys.KeyManager
import com.ramka.crypto.noise.IkHandshake
import com.ramka.crypto.padding.FramePadding
import com.ramka.crypto.ratchet.SessionCipher
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
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import kotlin.concurrent.thread

/**
 * Loopback-тесты [SecureLanChannel] (этап 2.5, шаги 2 и 3): реальные сокеты на 127.0.0.1,
 * реальное IK-рукопожатие, in-memory ключи. Без Android.
 *
 * Шаг 3: кадры рукопожатия идут без типа и длины (80/112/80 байт), кадр данных — без типа и с
 * длиной шифртекста строго по корзинам FramePadding; молчащие и медленные соединения закрываются
 * по таймауту; мусор закрывает соединение и ничего не доставляет.
 */
class SecureLanChannelLoopbackTest {

    private val host = "127.0.0.1"

    private val m1 = IkHandshake.MESSAGE1_SIZE
    private val m2 = IkHandshake.MESSAGE2_SIZE
    private val m3 = IkHandshake.MESSAGE3_SIZE
    private val tag = 16
    private val counterSize = 8

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
        handshakeTimeoutMillis: Long = SecureLanChannel.HANDSHAKE_TIMEOUT_MILLIS,
        dataIdleTimeoutMillis: Int = SecureLanChannel.DATA_IDLE_TIMEOUT_MILLIS,
        block: suspend (received: Channel<IncomingMessage>) -> T
    ): T = runBlocking {
        val senderIdentity = sender.getOrCreateIdentity()
        val channel = SecureLanChannel(receiver, port, handshakeTimeoutMillis, dataIdleTimeoutMillis) { staticKey ->
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

    private suspend fun Channel<IncomingMessage>.assertNothingWithin(millis: Long = 700) {
        assertNull("ничего не должно быть доставлено", withTimeoutOrNull(millis) { receive() })
    }

    private fun sendVia(sender: KeyManager, receiver: DeviceIdentity, port: Int, plaintext: ByteArray): Boolean =
        runBlocking {
            SecureLanChannel(sender, freePort()) { null }
                .sendMessage(host, port, receiver.x25519Public, receiver.ed25519Public, plaintext)
        }

    private fun connect(port: Int): Socket = Socket().also {
        it.connect(InetSocketAddress(host, port), 3000)
        it.soTimeout = 3000
    }

    /** Честное рукопожатие «вручную» по новому формату: M1 -> M2 (112 байт) -> M3. */
    private fun handshake(socket: Socket, sender: KeyManager, receiver: DeviceIdentity): SessionCipher? {
        val input = DataInputStream(socket.getInputStream())
        val output = socket.getOutputStream()
        val session = IkHandshake.startInitiator(sender, receiver.x25519Public)
        output.write(session.message1)
        output.flush()
        val message2 = ByteArray(m2)
        input.readFully(message2)
        val (signature, result) = session.consumeMessage2(message2, receiver.ed25519Public, sender) ?: return null
        output.write(signature)
        output.flush()
        return SessionCipher(result.sendKey, result.recvKey)
    }

    /** Пишет кадр данных с произвольным заявленным [declaredLength] (в том числе неверным). */
    private fun writeDataFrame(socket: Socket, declaredLength: Int, counter: ByteArray, ciphertext: ByteArray) {
        val frame = ByteBuffer.allocate(4 + counter.size + ciphertext.size)
            .putInt(declaredLength).put(counter).put(ciphertext).array()
        socket.getOutputStream().write(frame)
        socket.getOutputStream().flush()
    }

    /** Клиент, отправляющий в зашифрованном кадре произвольное [body] как есть (без выравнивания). */
    private fun sendRawBody(sender: KeyManager, receiver: DeviceIdentity, port: Int, body: ByteArray): Boolean =
        connect(port).use { socket ->
            val cipher = handshake(socket, sender, receiver) ?: return@use false
            val sent = cipher.encryptNext(body)
            writeDataFrame(socket, sent.ciphertext.size, ByteArray(counterSize), sent.ciphertext)
            true
        }

    /**
     * Ждёт, пока сервер закроет соединение (EOF или сброс), и возвращает затраченное время в мс.
     * Любые присланные данные или отсутствие закрытия за [withinMillis] — провал теста.
     */
    private fun awaitClosed(socket: Socket, withinMillis: Int): Long {
        val start = System.nanoTime()
        socket.soTimeout = withinMillis
        try {
            val n = socket.getInputStream().read()
            if (n >= 0) throw AssertionError("Сервер прислал данные вместо закрытия: $n")
        } catch (e: SocketTimeoutException) {
            throw AssertionError("Соединение не закрыто за $withinMillis мс")
        } catch (e: IOException) {
            // сброс соединения — тоже закрытие
        }
        return (System.nanoTime() - start) / 1_000_000
    }

    /** TCP-прокси, записывающий байты обоих направлений, чтобы смотреть форму потока на проводе. */
    private class RecordingProxy(private val targetPort: Int) {
        private val server = ServerSocket(0)
        val port: Int = server.localPort
        private val lock = Any()
        private val recorded = ByteArrayOutputStream()           // клиент -> сервер
        private val recordedFromServer = ByteArrayOutputStream() // сервер -> клиент

        init {
            thread(isDaemon = true) {
                while (!server.isClosed) {
                    val client = try { server.accept() } catch (e: Exception) { break }
                    val upstream = Socket(HOST, targetPort)
                    pipe(client, upstream, recorded)
                    pipe(upstream, client, recordedFromServer)
                }
            }
        }

        private fun pipe(from: Socket, to: Socket, sink: ByteArrayOutputStream) = thread(isDaemon = true) {
            try {
                val buf = ByteArray(4096)
                val input = from.getInputStream()
                val output = to.getOutputStream()
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    synchronized(lock) { sink.write(buf, 0, n) }
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

        /** Весь поток «клиент -> сервер» (M1 || M3 || кадр данных); запись сбрасывается. */
        fun takeClientBytes(): ByteArray = takeAndReset(recorded)

        /** Весь поток «сервер -> клиент» (в handshake это ровно Message2); запись сбрасывается. */
        fun takeServerBytes(): ByteArray = takeAndReset(recordedFromServer)

        private fun takeAndReset(sink: ByteArrayOutputStream): ByteArray =
            synchronized(lock) { sink.toByteArray().also { sink.reset() } }

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
    fun `handshake frames have no type or length and data frame length is bucket plus tag`() {
        val receiver = newKeyManager()
        val sender = newKeyManager()
        val port = freePort()
        val proxy = RecordingProxy(port)
        try {
            withReceiver(receiver, sender, port) { received ->
                // (размер тела, ожидаемая корзина): 5 байт заголовка выравнивания входят в корзину.
                val cases = listOf(
                    37 to 128,               // DELIVERED-ACK
                    77 to 128,               // короткий текст
                    123 to 128,              // заполненная малая корзина
                    124 to 256,              // следующая корзина
                    5000 to 8192             // выше 4096 — кратно 4096
                )
                for ((plaintextSize, bucket) in cases) {
                    assertTrue(sendVia(sender, receiver.getOrCreateIdentity(), proxy.port, bytes(plaintextSize)))
                    received.receiveWithin()

                    val client = proxy.takeClientBytes()
                    val server = proxy.takeServerBytes()
                    assertEquals("M2 ровно $m2 байт, без типа и длины", m2, server.size)

                    val declaredLength = ByteBuffer.wrap(client, m1 + m3, 4).int
                    assertEquals("length = корзина + тег AEAD", bucket + tag, declaredLength)
                    assertEquals(
                        "поток клиента = M1(80) + M3(80) + length(4) + counter(8) + шифртекст",
                        m1 + m3 + 4 + counterSize + declaredLength,
                        client.size
                    )
                }
            }
        } finally {
            proxy.close()
        }
    }

    @Test
    fun `frame sizes are fixed 80 112 80`() {
        assertEquals(80, m1)
        assertEquals(112, m2)
        assertEquals(80, m3)
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
    fun `recorded connection replayed to the receiver delivers nothing`() {
        val receiver = newKeyManager()
        val sender = newKeyManager()
        val port = freePort()
        val proxy = RecordingProxy(port)
        try {
            withReceiver(receiver, sender, port) { received ->
                assertTrue(sendVia(sender, receiver.getOrCreateIdentity(), proxy.port, bytes(37)))
                received.receiveWithin()
                val captured = proxy.snapshot()
                assertTrue(captured.isNotEmpty())

                // Весь исходящий поток соединения (M1, M3, данные) повторяется как есть: Message1 валиден,
                // но у ответчика новая эфемерная пара, поэтому старый Message3 не проходит.
                try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(host, port), 3000)
                        socket.getOutputStream().write(captured)
                        socket.getOutputStream().flush()
                        Thread.sleep(300)
                    }
                } catch (e: IOException) {
                    // ответчик уже закрыл соединение — для теста это нормально
                }
                received.assertNothingWithin()

                // Приёмник жив и принимает обычные сообщения.
                val ok = bytes(12)
                assertTrue(sendVia(sender, receiver.getOrCreateIdentity(), proxy.port, ok))
                assertArrayEquals(ok, received.receiveWithin().plaintext)
            }
        } finally {
            proxy.close()
        }
    }

    @Test
    fun `garbage instead of Message1 closes the connection and delivers nothing`() {
        val receiver = newKeyManager()
        val sender = newKeyManager()
        val port = freePort()
        withReceiver(receiver, sender, port) { received ->
            val random = java.util.Random(42)
            // Ровно 80 байт мусора, больше 80 и «старый формат» [длина 4][тип 1][80 байт].
            val oldFormat = ByteBuffer.allocate(4 + 1 + m1).putInt(1 + m1).put(1.toByte()).put(bytes(m1)).array()
            val inputs = listOf(ByteArray(m1).also { random.nextBytes(it) }, ByteArray(200).also { random.nextBytes(it) }, oldFormat)
            for (garbage in inputs) {
                connect(port).use { socket ->
                    socket.getOutputStream().write(garbage)
                    socket.getOutputStream().flush()
                    val elapsed = awaitClosed(socket, 2500)
                    assertTrue("закрыто сразу, а не по таймауту (size=${garbage.size}, $elapsed мс)", elapsed < 2000)
                }
            }
            received.assertNothingWithin()

            val ok = bytes(9)
            assertTrue(sendVia(sender, receiver.getOrCreateIdentity(), port, ok))
            assertArrayEquals(ok, received.receiveWithin().plaintext)
        }
    }

    @Test
    fun `silent incoming connection is closed by handshake timeout`() {
        val receiver = newKeyManager()
        val sender = newKeyManager()
        val port = freePort()
        withReceiver(receiver, sender, port, handshakeTimeoutMillis = 500) { received ->
            connect(port).use { socket ->
                val elapsed = awaitClosed(socket, 3000)
                assertTrue("закрыто по таймауту рукопожатия, $elapsed мс", elapsed in 300..2500)
            }
            val ok = bytes(5)
            assertTrue(sendVia(sender, receiver.getOrCreateIdentity(), port, ok))
            assertArrayEquals(ok, received.receiveWithin().plaintext)
        }
    }

    @Test
    fun `truncated Message1 followed by silence is closed by handshake timeout`() {
        val receiver = newKeyManager()
        val sender = newKeyManager()
        val port = freePort()
        withReceiver(receiver, sender, port, handshakeTimeoutMillis = 500) { received ->
            connect(port).use { socket ->
                socket.getOutputStream().write(bytes(m1 - 1)) // 79 из 80 байт
                socket.getOutputStream().flush()
                val elapsed = awaitClosed(socket, 3000)
                assertTrue("закрыто по таймауту, $elapsed мс", elapsed in 300..2500)
            }
            received.assertNothingWithin(200)
        }
    }

    @Test
    fun `slow byte-by-byte Message1 is cut by the total handshake deadline`() {
        val receiver = newKeyManager()
        val sender = newKeyManager()
        val port = freePort()
        withReceiver(receiver, sender, port, handshakeTimeoutMillis = 600) { _ ->
            connect(port).use { socket ->
                // Пауза между байтами (150 мс) короче таймаута (600 мс): простой-таймаут не сработал бы
                // никогда, закрывает именно общий дедлайн. Без него 80 байт заняли бы ~12 с.
                thread(isDaemon = true) {
                    try {
                        repeat(m1) {
                            socket.getOutputStream().write(1)
                            socket.getOutputStream().flush()
                            Thread.sleep(150)
                        }
                    } catch (e: Exception) {
                        // сервер закрыл соединение — ожидаемо
                    }
                }
                val elapsed = awaitClosed(socket, 3000)
                assertTrue("закрыто общим дедлайном, $elapsed мс", elapsed in 300..2500)
            }
        }
    }

    @Test
    fun `valid handshake followed by silence is closed by data idle timeout`() {
        val receiver = newKeyManager()
        val sender = newKeyManager()
        val port = freePort()
        withReceiver(receiver, sender, port, dataIdleTimeoutMillis = 500) { received ->
            connect(port).use { socket ->
                assertTrue(handshake(socket, sender, receiver.getOrCreateIdentity()) != null)
                val elapsed = awaitClosed(socket, 3000)
                assertTrue("закрыто по простою, $elapsed мс", elapsed in 300..2500)
            }
            received.assertNothingWithin(200)
        }
    }

    @Test
    fun `data frame with length outside buckets closes the connection`() {
        val receiver = newKeyManager()
        val sender = newKeyManager()
        val port = freePort()
        withReceiver(receiver, sender, port) { received ->
            val bucketCiphertext = FramePadding.BUCKETS[0] + tag
            val badLengths = listOf(
                0, 1, 15, 16, 100,
                FramePadding.BUCKETS[0] + tag - 1,   // на байт меньше допустимой
                bucketCiphertext + 1,                // на байт больше допустимой
                FramePadding.BUCKETS[0] + 100 + tag, // между корзинами
                FramePadding.MAX_PADDED_SIZE + FramePadding.LARGE_STEP + tag, // выше максимума
                Int.MAX_VALUE, -1, Int.MIN_VALUE
            )
            for (declared in badLengths) {
                connect(port).use { socket ->
                    val cipher = handshake(socket, sender, receiver.getOrCreateIdentity())
                    assertTrue("handshake, declared=$declared", cipher != null)
                    // Реальное тело короткое: сервер должен отвергнуть уже по полю длины.
                    writeDataFrame(socket, declared, ByteArray(counterSize), ByteArray(64))
                    val elapsed = awaitClosed(socket, 2500)
                    assertTrue("declared=$declared закрыто сразу, а не по таймауту ($elapsed мс)", elapsed < 2000)
                }
            }
            received.assertNothingWithin()

            val ok = bytes(14)
            assertTrue(sendVia(sender, receiver.getOrCreateIdentity(), port, ok))
            assertArrayEquals(ok, received.receiveWithin().plaintext)
        }
    }

    @Test
    fun `data frame with corrupted ciphertext of valid length is dropped`() {
        val receiver = newKeyManager()
        val sender = newKeyManager()
        val port = freePort()
        withReceiver(receiver, sender, port) { received ->
            connect(port).use { socket ->
                val cipher = handshake(socket, sender, receiver.getOrCreateIdentity())!!
                val sent = cipher.encryptNext(FramePadding.pad(bytes(10)))
                sent.ciphertext[3] = (sent.ciphertext[3].toInt() xor 0x01).toByte()
                writeDataFrame(socket, sent.ciphertext.size, ByteArray(counterSize), sent.ciphertext)
                awaitClosed(socket, 2500)
            }
            received.assertNothingWithin()

            val ok = bytes(8)
            assertTrue(sendVia(sender, receiver.getOrCreateIdentity(), port, ok))
            assertArrayEquals(ok, received.receiveWithin().plaintext)
        }
    }

    @Test
    fun `body without padding is dropped and receiver keeps working`() {
        val receiver = newKeyManager()
        val sender = newKeyManager()
        val port = freePort()
        withReceiver(receiver, sender, port) { received ->
            // 37 байт без выравнивания: шифртекст 53 байта — не корзина, отвергается по длине.
            assertTrue(sendRawBody(sender, receiver.getOrCreateIdentity(), port, bytes(37)))
            received.assertNothingWithin()

            // 128 ненулевых байт без заголовка выравнивания: длина допустима, AEAD проходит, unpad() отвергает.
            assertTrue(sendRawBody(sender, receiver.getOrCreateIdentity(), port, bytes(FramePadding.BUCKETS[0])))
            received.assertNothingWithin()

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
            received.assertNothingWithin()

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
            // 130 байт — не корзина: отвергается уже по полю длины (146 != корзина + 16).
            val tooLong = FramePadding.pad(bytes(10)) + ByteArray(2)
            assertTrue(sendRawBody(sender, receiver.getOrCreateIdentity(), port, tooLong))
            received.assertNothingWithin()

            // Размер корзины верный, но хвост ненулевой: длина допустима, отвергает unpad().
            val dirtyTail = FramePadding.pad(bytes(10)).also { it[it.size - 1] = 1 }
            assertTrue(sendRawBody(sender, receiver.getOrCreateIdentity(), port, dirtyTail))
            received.assertNothingWithin()
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

    @Test
    fun `valid ciphertext length predicate matches FramePadding buckets`() {
        for (bucket in FramePadding.BUCKETS) {
            assertTrue(FrameIo.isValidCiphertextLength(bucket + tag))
            assertEquals(false, FrameIo.isValidCiphertextLength(bucket + tag + 1))
            assertEquals(false, FrameIo.isValidCiphertextLength(bucket))
        }
        assertTrue(FrameIo.isValidCiphertextLength(2 * FramePadding.LARGE_STEP + tag))
        assertTrue(FrameIo.isValidCiphertextLength(FramePadding.MAX_PADDED_SIZE + tag))
        assertEquals(false, FrameIo.isValidCiphertextLength(FramePadding.MAX_PADDED_SIZE + FramePadding.LARGE_STEP + tag))
        assertEquals(false, FrameIo.isValidCiphertextLength(0))
        assertEquals(false, FrameIo.isValidCiphertextLength(-1))
        assertEquals(false, FrameIo.isValidCiphertextLength(Int.MAX_VALUE))
    }
}
