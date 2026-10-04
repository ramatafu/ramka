package com.ramka.network.relay

import com.ramka.domain.relay.RelayCheckResult
import com.ramka.domain.relay.RelayEndpoint
import com.ramka.domain.relay.RelayFailure
import com.ramka.domain.util.Jitter
import com.ramka.domain.util.PrivacyTimingConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

class RelayClientTest {

    private val noJitter = Jitter(PrivacyTimingConfig(jitterEnabled = false))

    private fun fastConfig() = RelayClientConfig(
        connectTimeoutMillis = 2_000,
        connectDeadlineMillis = 3_000,
        handshakeIoTimeoutMillis = 2_000,
        acceptReadyTimeoutMillis = 2_000,
        pingMinMillis = 40,
        pingMaxMillis = 60,
        pongTimeoutMillis = 150,
        backoffBaseMillis = 30,
        backoffCapMillis = 100,
        replacedMinDelayMillis = 100,
        fatalRetryMillis = 200
    )

    private fun client(relay: FakeRelay, identity: RelayIdentity = TestIdentity(), config: RelayClientConfig = fastConfig()) =
        RelayClient(identity, PlainConnector(relay.port), noJitter, config)

    private fun waitUntil(what: String, timeoutMillis: Long = 4_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMillis
        while (!condition()) {
            if (System.currentTimeMillis() > end) throw AssertionError("Не дождались: $what")
            Thread.sleep(10)
        }
    }

    /** Подписчик incoming(): собирает «трубы» и состояния. */
    private class Listener(client: RelayClient, endpoint: RelayEndpoint) {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val tunnels = Channel<Socket>(Channel.UNLIMITED)
        val states = CopyOnWriteArrayList<RelayConnectionState>()

        init {
            scope.launch { client.incoming(endpoint) { states.add(it) }.collect { tunnels.send(it) } }
        }

        fun stop() = scope.cancel()
        fun registered() = states.any { it === RelayConnectionState.Registered }
    }

    private fun readText(socket: Socket, length: Int): String {
        socket.soTimeout = 3_000
        val buffer = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val n = socket.getInputStream().read(buffer, offset, length - offset)
            if (n < 0) throw AssertionError("EOF после $offset из $length байт")
            offset += n
        }
        return String(buffer, Charsets.UTF_8)
    }

    // ---- Протокол и тестовые векторы §9 ----

    @Test
    fun `routing_id и подпись совпадают с тестовыми векторами спецификации`() {
        val pub1 = unhex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        assertEquals("03473099b0738effe4a712e6140f1ec0", hex(RelayProtocol.routingId(pub1)))

        val seed = unhex("07".repeat(32))
        val pub2 = unhex("ea4a6c63e29c520abef5507b132ec5f9954776aebebe7b92421eea691446d22c")
        val rid2 = RelayProtocol.routingId(pub2)
        assertEquals("8c5b434b82cf9562754dd2092e8c7559", hex(rid2))

        val nonce = unhex("a0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebf")
        val signature = TestIdentity.signWithSeed(seed, RelayProtocol.proofMessage(nonce, rid2))
        assertEquals(
            "a21eb8a2b754cd83427df8005e575baec863558556e25b1309d73b1188278c65" +
                "7ea4d13bdeae51cd85719568b24ab2f02bb5be94ad718f0bf1316170bc15ca0f",
            hex(signature)
        )
        assertTrue(TestIdentity.verify(pub2, RelayProtocol.proofMessage(nonce, rid2), signature))
    }

    @Test
    fun `раскладка кадров и тела REGISTER`() {
        val out = java.io.ByteArrayOutputStream()
        RelayProtocol.writeFrame(out, RelayProtocol.PING)
        RelayProtocol.writeFrame(out, RelayProtocol.INCOMING, ByteArray(16) { 7 })
        val bytes = out.toByteArray()
        assertEquals("090000", hex(bytes.copyOfRange(0, 3)))
        assertEquals("070010", hex(bytes.copyOfRange(3, 6)))

        val body = RelayProtocol.authBody(TEST_TOKEN, ByteArray(48))
        assertEquals(1, body[0].toInt())
        assertEquals(TEST_TOKEN.length, body[1].toInt())
        assertEquals(2 + TEST_TOKEN.length + 48, body.size)

        // Кадр длиннее 512 байт читать нельзя.
        val tooBig = byteArrayOf(1, 0x02, 0x01) // length = 513
        try {
            RelayProtocol.readFrame(java.io.ByteArrayInputStream(tooBig))
            throw AssertionError("ожидалось исключение")
        } catch (e: RelayException) {
            assertEquals(RelayFailure.PROTOCOL, e.failure)
        }
    }

    @Test
    fun `коды ошибок сводятся к причинам`() {
        assertEquals(RelayFailure.AUTH, RelayProtocol.failureForCode(RelayProtocol.CODE_AUTH))
        assertEquals(RelayFailure.TARGET_OFFLINE, RelayProtocol.failureForCode(RelayProtocol.CODE_OFFLINE))
        assertEquals(RelayFailure.TIMEOUT, RelayProtocol.failureForCode(RelayProtocol.CODE_TIMEOUT))
        assertEquals(RelayFailure.BUSY, RelayProtocol.failureForCode(RelayProtocol.CODE_BUSY))
        assertEquals(RelayFailure.REPLACED, RelayProtocol.failureForCode(RelayProtocol.CODE_REPLACED))
        assertEquals(RelayFailure.INTERNAL, RelayProtocol.failureForCode(RelayProtocol.CODE_INTERNAL))
        for (code in listOf(0x01, 0x02, 0x04, 0x05, 0x08, 0x55)) {
            assertEquals("код $code", RelayFailure.PROTOCOL, RelayProtocol.failureForCode(code))
        }
    }

    @Test
    fun `backoff растёт, ограничен потолком, AUTH и REPLACED обрабатываются особо`() {
        val c = RelayClient(TestIdentity(), PlainConnector(1), noJitter, RelayClientConfig())
        assertEquals(1_000L, c.nextDelayMillis(RelayFailure.UNREACHABLE, 0))
        assertEquals(2_000L, c.nextDelayMillis(RelayFailure.UNREACHABLE, 1))
        assertEquals(4_000L, c.nextDelayMillis(RelayFailure.TIMEOUT, 2))
        assertEquals(60_000L, c.nextDelayMillis(RelayFailure.UNREACHABLE, 10))
        assertEquals(60_000L, c.nextDelayMillis(RelayFailure.UNREACHABLE, 500))
        assertEquals(300_000L, c.nextDelayMillis(RelayFailure.AUTH, 0))
        assertEquals(300_000L, c.nextDelayMillis(RelayFailure.TLS13_UNSUPPORTED, 3))
        assertEquals(30_000L, c.nextDelayMillis(RelayFailure.REPLACED, 0)) // пол 30 с
    }

    @Test
    fun `backoff получает джиттер ±20 процентов`() {
        val low = RelayClient(TestIdentity(), PlainConnector(1), Jitter(nextUnit = { 0.0 }), RelayClientConfig())
        val high = RelayClient(TestIdentity(), PlainConnector(1), Jitter(nextUnit = { 1.0 }), RelayClientConfig())
        assertEquals(800L, low.nextDelayMillis(RelayFailure.UNREACHABLE, 0))
        assertEquals(1_200L, high.nextDelayMillis(RelayFailure.UNREACHABLE, 0))
        assertEquals(48_000L, low.nextDelayMillis(RelayFailure.UNREACHABLE, 10))
    }

    // ---- check ----

    @Test
    fun `check — исправный релей`() = runBlocking {
        FakeRelay().use { relay ->
            assertEquals(RelayCheckResult.Ok, client(relay).check(testEndpoint()))
        }
    }

    @Test
    fun `check — неверный токен`() = runBlocking {
        FakeRelay().use { relay ->
            val result = client(relay).check(testEndpoint("wrong-token-0123456789"))
            assertEquals(RelayCheckResult.Failed(RelayFailure.AUTH), result)
        }
    }

    @Test
    fun `check — релей недоступен`() = runBlocking {
        val relay = FakeRelay()
        val c = client(relay)
        relay.close()
        assertEquals(RelayCheckResult.Failed(RelayFailure.UNREACHABLE), c.check(testEndpoint()))
    }

    @Test
    fun `check не вытесняет регистрацию устройства`() = runBlocking {
        FakeRelay().use { relay ->
            val identity = TestIdentity()
            val c = client(relay, identity)
            val listener = Listener(c, testEndpoint())
            try {
                waitUntil("регистрация") { listener.registered() }
                assertEquals(RelayCheckResult.Ok, c.check(testEndpoint()))
                Thread.sleep(150)
                assertEquals(1, relay.registeredNow)
                assertEquals(1, relay.registerCount.get())
            } finally {
                listener.stop()
            }
        }
    }

    // ---- connectTo ----

    @Test
    fun `connectTo — получатель не в сети`() = runBlocking {
        FakeRelay().use { relay ->
            val result = client(relay).connectTo(testEndpoint(), TestIdentity().ed25519Public)
            assertEquals(RelayConnectResult.Failed(RelayFailure.TARGET_OFFLINE), result)
        }
    }

    @Test
    fun `connectTo — неверный токен`() = runBlocking {
        FakeRelay().use { relay ->
            val result = client(relay).connectTo(testEndpoint("wrong-token-0123456789"), TestIdentity().ed25519Public)
            assertEquals(RelayConnectResult.Failed(RelayFailure.AUTH), result)
        }
    }

    @Test
    fun `connectTo — неверный ключ получателя`() = runBlocking {
        FakeRelay().use { relay ->
            val result = client(relay).connectTo(testEndpoint(), ByteArray(5))
            assertEquals(RelayConnectResult.Failed(RelayFailure.PROTOCOL), result)
        }
    }

    @Test
    fun `доставка через релей — байты идут в обе стороны, EOF доходит до получателя`() = runBlocking {
        FakeRelay().use { relay ->
            val bob = TestIdentity()
            val bobListener = Listener(client(relay, bob), testEndpoint())
            val alice = client(relay)
            try {
                waitUntil("регистрация Боба") { bobListener.registered() }
                val result = alice.connectTo(testEndpoint(), bob.ed25519Public)
                assertTrue("ожидалось Connected, получено $result", result is RelayConnectResult.Connected)
                val aliceSide = (result as RelayConnectResult.Connected).socket
                val bobSide = bobListener.tunnels.receive()

                aliceSide.getOutputStream().write("hello".toByteArray())
                aliceSide.getOutputStream().flush()
                assertEquals("hello", readText(bobSide, 5))

                bobSide.getOutputStream().write("world!".toByteArray())
                bobSide.getOutputStream().flush()
                assertEquals("world!", readText(aliceSide, 6))

                aliceSide.shutdownOutput()
                bobSide.soTimeout = 3_000
                assertEquals(-1, bobSide.getInputStream().read())
                aliceSide.close()
                bobSide.close()
            } finally {
                bobListener.stop()
            }
        }
    }

    @Test
    fun `несколько доставок подряд и параллельно`() = runBlocking {
        FakeRelay().use { relay ->
            val bob = TestIdentity()
            val bobListener = Listener(client(relay, bob), testEndpoint())
            val alice = client(relay)
            try {
                waitUntil("регистрация Боба") { bobListener.registered() }
                val sockets = (1..3).map { i ->
                    val r = alice.connectTo(testEndpoint(), bob.ed25519Public)
                    assertTrue("доставка $i: $r", r is RelayConnectResult.Connected)
                    (r as RelayConnectResult.Connected).socket.also {
                        it.getOutputStream().write("msg$i".toByteArray())
                        it.getOutputStream().flush()
                    }
                }
                val received = (1..3).map { readText(bobListener.tunnels.receive(), 4) }.sorted()
                assertEquals(listOf("msg1", "msg2", "msg3"), received)
                sockets.forEach { it.close() }
            } finally {
                bobListener.stop()
            }
        }
    }

    @Test
    fun `connectTo — получатель не принял соединение, релей отвечает TIMEOUT`() = runBlocking {
        FakeRelay(acceptTimeoutMillis = 250).use { relay ->
            val bob = TestIdentity()
            // Получатель регистрируется вручную и игнорирует INCOMING.
            val control = PlainConnector(relay.port).connect(testEndpoint(), 2_000)
            registerManually(control, bob)
            try {
                val result = client(relay).connectTo(testEndpoint(), bob.ed25519Public)
                assertEquals(RelayConnectResult.Failed(RelayFailure.TIMEOUT), result)
            } finally {
                control.close()
            }
        }
    }

    @Test
    fun `connectTo — молчащий релей упирается в общий дедлайн`() = runBlocking {
        FakeRelay(acceptTimeoutMillis = 10_000).use { relay ->
            val bob = TestIdentity()
            val control = PlainConnector(relay.port).connect(testEndpoint(), 2_000)
            registerManually(control, bob)
            try {
                val started = System.currentTimeMillis()
                val c = client(relay, config = fastConfig().copy(connectDeadlineMillis = 300))
                val result = c.connectTo(testEndpoint(), bob.ed25519Public)
                assertEquals(RelayConnectResult.Failed(RelayFailure.TIMEOUT), result)
                assertTrue("дедлайн не соблюдён", System.currentTimeMillis() - started < 2_500)
            } finally {
                control.close()
            }
        }
    }

    @Test
    fun `connectTo — переданный бюджет заменяет дедлайн из конфигурации`() = runBlocking {
        FakeRelay(acceptTimeoutMillis = 10_000).use { relay ->
            val bob = TestIdentity()
            val control = PlainConnector(relay.port).connect(testEndpoint(), 2_000)
            registerManually(control, bob)
            try {
                val started = System.currentTimeMillis()
                // В конфигурации дедлайн 3 с, но запасной релей получает бюджет всего 300 мс.
                val result = client(relay).connectTo(testEndpoint(), bob.ed25519Public, budgetMillis = 300)
                assertEquals(RelayConnectResult.Failed(RelayFailure.TIMEOUT), result)
                assertTrue("бюджет не соблюдён", System.currentTimeMillis() - started < 2_000)
            } finally {
                control.close()
            }
        }
    }

    @Test
    fun `connectTo — достаточный бюджет не мешает успешной доставке`() = runBlocking {
        FakeRelay().use { relay ->
            val bob = TestIdentity()
            val bobListener = Listener(client(relay, bob), testEndpoint())
            try {
                waitUntil("регистрация Боба") { bobListener.registered() }
                val result = client(relay).connectTo(testEndpoint(), bob.ed25519Public, budgetMillis = 3_000)
                assertTrue("ожидалось Connected, получено $result", result is RelayConnectResult.Connected)
                (result as RelayConnectResult.Connected).socket.close()
            } finally {
                bobListener.stop()
            }
        }
    }

    private fun registerManually(socket: Socket, identity: TestIdentity) {
        socket.soTimeout = 3_000
        val rid = RelayProtocol.routingId(identity.ed25519Public)
        RelayProtocol.writeFrame(
            socket.getOutputStream(), RelayProtocol.REGISTER,
            RelayProtocol.authBody(TEST_TOKEN, rid + identity.ed25519Public)
        )
        val challenge = RelayProtocol.readFrame(socket.getInputStream())
        RelayProtocol.writeFrame(
            socket.getOutputStream(), RelayProtocol.PROOF,
            identity.sign(RelayProtocol.proofMessage(challenge.body, rid))
        )
        assertEquals(RelayProtocol.REGISTERED, RelayProtocol.readFrame(socket.getInputStream()).type)
    }

    // ---- incoming ----

    @Test
    fun `incoming — регистрация, PING-PONG и корректная остановка`() = runBlocking {
        FakeRelay().use { relay ->
            val listener = Listener(client(relay), testEndpoint())
            try {
                waitUntil("регистрация") { listener.registered() }
                assertEquals(RelayConnectionState.Connecting, listener.states.first())
                waitUntil("несколько PING") { relay.pingCount.get() >= 3 }
                assertEquals(1, relay.registeredNow)
            } finally {
                listener.stop()
            }
            waitUntil("закрытие управляющего соединения") { relay.registeredNow == 0 }
        }
    }

    @Test
    fun `incoming — неверный токен даёт AUTH и повтор не раньше fatalRetry`() = runBlocking {
        FakeRelay().use { relay ->
            val listener = Listener(client(relay), testEndpoint("wrong-token-0123456789"))
            try {
                waitUntil("состояние Failed") { listener.states.any { it is RelayConnectionState.Failed } }
                val failed = listener.states.filterIsInstance<RelayConnectionState.Failed>().first()
                assertEquals(RelayFailure.AUTH, failed.failure)
                assertEquals(200L, failed.retryInMillis)
                assertEquals(0, relay.registerCount.get())
            } finally {
                listener.stop()
            }
        }
    }

    @Test
    fun `incoming — переподключение после обрыва релея`() = runBlocking {
        FakeRelay().use { relay ->
            val listener = Listener(client(relay), testEndpoint())
            try {
                waitUntil("первая регистрация") { listener.registered() }
                relay.dropAll()
                waitUntil("сообщение об обрыве") { listener.states.any { it is RelayConnectionState.Failed } }
                waitUntil("повторная регистрация") { relay.registerCount.get() >= 2 && relay.registeredNow == 1 }
            } finally {
                listener.stop()
            }
        }
    }

    @Test
    fun `incoming — нет PONG закрывает соединение и ведёт к переподключению`() = runBlocking {
        FakeRelay().use { relay ->
            relay.respondToPing = false
            val listener = Listener(client(relay), testEndpoint())
            try {
                waitUntil("обрыв по PONG") {
                    listener.states.any { it is RelayConnectionState.Failed && it.failure == RelayFailure.TIMEOUT }
                }
                waitUntil("повторная регистрация") { relay.registerCount.get() >= 2 }
            } finally {
                listener.stop()
            }
        }
    }

    @Test
    fun `incoming — вытеснение другим устройством с тем же ключом`() = runBlocking {
        FakeRelay().use { relay ->
            val identity = TestIdentity()
            val first = Listener(client(relay, identity), testEndpoint())
            try {
                waitUntil("первая регистрация") { first.registered() }
                val second = PlainConnector(relay.port).connect(testEndpoint(), 2_000)
                registerManually(second, identity)
                waitUntil("REPLACED у первого") {
                    first.states.any { it is RelayConnectionState.Failed && it.failure == RelayFailure.REPLACED }
                }
                val replaced = first.states.filterIsInstance<RelayConnectionState.Failed>().first { it.failure == RelayFailure.REPLACED }
                assertTrue("пауза после REPLACED не меньше пола", replaced.retryInMillis >= 100)
                second.close()
            } finally {
                first.stop()
            }
        }
    }
}
