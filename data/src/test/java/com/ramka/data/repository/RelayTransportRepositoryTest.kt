package com.ramka.data.repository

import com.ramka.domain.model.Contact
import com.ramka.domain.model.KeyVerificationStatus
import com.ramka.domain.relay.PoolRelayProvider
import com.ramka.domain.relay.RelayAddress
import com.ramka.domain.relay.RelayClock
import com.ramka.domain.relay.RelayEndpoint
import com.ramka.domain.relay.RelayEntry
import com.ramka.domain.relay.RelayFailure
import com.ramka.domain.relay.RelayLinkState
import com.ramka.domain.relay.RelayPool
import com.ramka.domain.relay.RelayPoolSource
import com.ramka.domain.relay.RelayProvider
import com.ramka.network.local.IncomingMessage
import com.ramka.network.local.TunnelMessageChannel
import com.ramka.network.relay.RelayConnectResult
import com.ramka.network.relay.RelayConnectionState
import com.ramka.network.relay.RelayTunnelClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class RelayTransportRepositoryTest {

    private val endpoint1 = RelayEndpoint(RelayAddress("relay1.example.com", 48766), "token-token-token-1", null)
    private val endpoint2 = RelayEndpoint(RelayAddress("relay2.example.com", 48766), "token-token-token-2", null)
    private val endpoint3 = RelayEndpoint(RelayAddress("relay3.example.com", 48766), "token-token-token-3", null)
    private val endpoint4 = RelayEndpoint(RelayAddress("relay4.example.com", 48766), "token-token-token-4", null)

    private class FakeClock(var now: Long = 10_000L) : RelayClock {
        override fun nowMillis(): Long = now
        fun advance(millis: Long) {
            now += millis
        }
    }

    private val contact = Contact(
        localId = "c1", alias = "a", publicKey = ByteArray(32) { 1 }, signingPublicKey = ByteArray(32) { 2 },
        lastKnownAddress = null, verification = KeyVerificationStatus.UNVERIFIED, addedAtEpochDay = 0
    )

    private class FakeProvider(initialOwn: List<RelayEndpoint>, var sendTo: List<RelayEndpoint>) : RelayProvider {
        val own = MutableStateFlow(initialOwn)
        val reports = CopyOnWriteArrayList<Pair<RelayEndpoint, RelayFailure?>>()
        val deliveries = CopyOnWriteArrayList<Triple<RelayEndpoint, Contact, Boolean>>()
        override val ownRelays: Flow<List<RelayEndpoint>> = own
        override suspend fun relaysFor(contact: Contact): List<RelayEndpoint> = sendTo
        override fun report(endpoint: RelayEndpoint, failure: RelayFailure?) {
            reports.add(endpoint to failure)
        }

        override fun reportDelivery(endpoint: RelayEndpoint, contact: Contact, latencyMillis: Long, fallback: Boolean) {
            deliveries.add(Triple(endpoint, contact, fallback))
        }
    }

    private class FakeClient : RelayTunnelClient {
        val connectResults = HashMap<RelayEndpoint, RelayConnectResult>()
        val connectCalls = CopyOnWriteArrayList<Pair<RelayEndpoint, ByteArray>>()
        val tunnels = Channel<Socket>(Channel.UNLIMITED)
        val states = Channel<RelayConnectionState>(Channel.UNLIMITED)
        val activeSubscriptions = AtomicInteger(0)
        val budgets = CopyOnWriteArrayList<Long?>()
        val startedBy = ConcurrentHashMap<RelayEndpoint, AtomicInteger>()
        val activeBy = ConcurrentHashMap<RelayEndpoint, AtomicInteger>()

        /** Вызывается при каждой попытке подключения (тесты двигают здесь часы). */
        @Volatile var onConnect: (RelayEndpoint) -> Unit = {}

        override suspend fun connectTo(endpoint: RelayEndpoint, targetSigningPublicKey: ByteArray, budgetMillis: Long?): RelayConnectResult {
            connectCalls.add(endpoint to targetSigningPublicKey)
            budgets.add(budgetMillis)
            onConnect(endpoint)
            return connectResults[endpoint] ?: RelayConnectResult.Failed(RelayFailure.UNREACHABLE)
        }

        fun started(endpoint: RelayEndpoint) = startedBy[endpoint]?.get() ?: 0
        fun active(endpoint: RelayEndpoint) = activeBy[endpoint]?.get() ?: 0

        override fun incoming(endpoint: RelayEndpoint, onState: (RelayConnectionState) -> Unit): Flow<Socket> = channelFlow {
            activeSubscriptions.incrementAndGet()
            startedBy.getOrPut(endpoint) { AtomicInteger() }.incrementAndGet()
            activeBy.getOrPut(endpoint) { AtomicInteger() }.incrementAndGet()
            try {
                // Состояния, поставленные тестом в очередь, передаём подписчику.
                while (true) {
                    val r = states.tryReceive()
                    if (r.isSuccess) onState(r.getOrThrow()) else break
                }
                for (s in tunnels) send(s)
            } finally {
                activeSubscriptions.decrementAndGet()
                activeBy[endpoint]?.decrementAndGet()
            }
        }
    }

    private class FakeChannel : TunnelMessageChannel {
        var sendResult = true
        val sendCalls = CopyOnWriteArrayList<List<ByteArray>>()
        val incoming = HashMap<Socket, IncomingMessage?>()

        override suspend fun sendMessageOver(
            socket: Socket, remoteStaticX25519: ByteArray, remoteSigningPublicKey: ByteArray,
            plaintext: ByteArray, timeoutMillis: Int
        ): Boolean {
            sendCalls.add(listOf(remoteStaticX25519, remoteSigningPublicKey, plaintext))
            return sendResult
        }

        override suspend fun processIncomingSocket(socket: Socket): IncomingMessage? = incoming[socket]
    }

    // ---- отправка ----

    @Test
    fun `нет релеев — false, к релею не обращаемся`() = runBlocking {
        val client = FakeClient()
        val repo = RelayTransportRepository(FakeChannel(), client, FakeProvider(emptyList(), emptyList()))
        assertFalse(repo.sendEncryptedPacket(contact, byteArrayOf(1)))
        assertTrue(client.connectCalls.isEmpty())
    }

    @Test
    fun `труба установлена — рукопожатие с ключами контакта, сокет закрывается, релей помечен исправным`() = runBlocking {
        val client = FakeClient()
        val socket = Socket()
        client.connectResults[endpoint1] = RelayConnectResult.Connected(socket)
        val channel = FakeChannel()
        val provider = FakeProvider(emptyList(), listOf(endpoint1))
        val repo = RelayTransportRepository(channel, client, provider)

        assertTrue(repo.sendEncryptedPacket(contact, byteArrayOf(9, 9)))

        assertEquals(contact.signingPublicKey.toList(), client.connectCalls.single().second.toList())
        val args = channel.sendCalls.single()
        assertEquals(contact.publicKey.toList(), args[0].toList())
        assertEquals(contact.signingPublicKey.toList(), args[1].toList())
        assertEquals(listOf<Byte>(9, 9), args[2].toList())
        assertTrue(socket.isClosed)
        assertEquals(listOf<Pair<RelayEndpoint, RelayFailure?>>(endpoint1 to null), provider.reports.toList())
    }

    @Test
    fun `рукопожатие не удалось — false и сокет закрыт, без перебора других релеев`() = runBlocking {
        val client = FakeClient()
        val socket = Socket()
        client.connectResults[endpoint1] = RelayConnectResult.Connected(socket)
        client.connectResults[endpoint2] = RelayConnectResult.Connected(Socket())
        val channel = FakeChannel().also { it.sendResult = false }
        val repo = RelayTransportRepository(channel, client, FakeProvider(emptyList(), listOf(endpoint1, endpoint2)))

        assertFalse(repo.sendEncryptedPacket(contact, byteArrayOf(1)))
        assertTrue(socket.isClosed)
        assertEquals(1, client.connectCalls.size)
    }

    @Test
    fun `получатель не в сети — релей исправен, пробуем следующий релей из списка`() = runBlocking {
        val client = FakeClient()
        client.connectResults[endpoint1] = RelayConnectResult.Failed(RelayFailure.TARGET_OFFLINE)
        client.connectResults[endpoint2] = RelayConnectResult.Connected(Socket())
        val provider = FakeProvider(emptyList(), listOf(endpoint1, endpoint2))
        val repo = RelayTransportRepository(FakeChannel(), client, provider)

        assertTrue(repo.sendEncryptedPacket(contact, byteArrayOf(1)))
        assertEquals(listOf<Pair<RelayEndpoint, RelayFailure?>>(endpoint1 to null, endpoint2 to null), provider.reports.toList())
    }

    @Test
    fun `ошибка релея — false и причина попадает в провайдер`() = runBlocking {
        val client = FakeClient()
        client.connectResults[endpoint1] = RelayConnectResult.Failed(RelayFailure.AUTH)
        val provider = FakeProvider(emptyList(), listOf(endpoint1))
        val repo = RelayTransportRepository(FakeChannel(), client, provider)

        assertFalse(repo.sendEncryptedPacket(contact, byteArrayOf(1)))
        assertEquals(listOf<Pair<RelayEndpoint, RelayFailure?>>(endpoint1 to RelayFailure.AUTH), provider.reports.toList())
    }

    // ---- регрессия: крах отправителя в момент успешной доставки ----

    /** Сокет, у которого close() бросает исключение: так ведёт себя TLS-сокет, когда релей уже закрыл соединение. */
    private class ThrowingCloseSocket : Socket() {
        override fun close() {
            super.close()
            throw java.io.IOException("Broken pipe при отправке close_notify")
        }
    }

    @Test
    fun `ошибка при закрытии сокета после успешной отправки не превращается в исключение и не меняет результат`() = runBlocking {
        val client = FakeClient()
        val socket = ThrowingCloseSocket()
        client.connectResults[endpoint1] = RelayConnectResult.Connected(socket)
        val repo = RelayTransportRepository(FakeChannel(), client, FakeProvider(emptyList(), listOf(endpoint1)))

        assertTrue(repo.sendEncryptedPacket(contact, byteArrayOf(1))) // сообщение ушло: true, а не исключение
        assertTrue(socket.isClosed)
    }

    @Test
    fun `исключение в обмене по трубе не выходит наружу, сокет закрыт`() = runBlocking {
        val client = FakeClient()
        val socket = Socket()
        client.connectResults[endpoint1] = RelayConnectResult.Connected(socket)
        val throwing = object : TunnelMessageChannel {
            override suspend fun sendMessageOver(
                socket: Socket, remoteStaticX25519: ByteArray, remoteSigningPublicKey: ByteArray,
                plaintext: ByteArray, timeoutMillis: Int
            ): Boolean = throw IllegalStateException("неожиданная ошибка")

            override suspend fun processIncomingSocket(socket: Socket): IncomingMessage? = null
        }
        val repo = RelayTransportRepository(throwing, client, FakeProvider(emptyList(), listOf(endpoint1)))

        assertFalse(repo.sendEncryptedPacket(contact, byteArrayOf(1)))
        assertTrue(socket.isClosed)
    }

    @Test
    fun `исключение провайдера или клиента не выходит наружу`() = runBlocking {
        val brokenProvider = object : RelayProvider {
            override val ownRelays: Flow<List<RelayEndpoint>> = MutableStateFlow(emptyList())
            override suspend fun relaysFor(contact: Contact): List<RelayEndpoint> = throw RuntimeException("boom")
            override fun report(endpoint: RelayEndpoint, failure: RelayFailure?) = Unit
        }
        assertFalse(RelayTransportRepository(FakeChannel(), FakeClient(), brokenProvider).sendEncryptedPacket(contact, byteArrayOf(1)))

        val brokenClient = object : RelayTunnelClient {
            override suspend fun connectTo(endpoint: RelayEndpoint, targetSigningPublicKey: ByteArray, budgetMillis: Long?): RelayConnectResult =
                throw IllegalStateException("boom")

            override fun incoming(endpoint: RelayEndpoint, onState: (RelayConnectionState) -> Unit): Flow<Socket> =
                channelFlow { }
        }
        val repo = RelayTransportRepository(FakeChannel(), brokenClient, FakeProvider(emptyList(), listOf(endpoint1)))
        assertFalse(repo.sendEncryptedPacket(contact, byteArrayOf(1)))
    }

    // ---- приём ----

    @Test
    fun `входящая труба превращается в пакет (ключ отправителя, открытый текст)`() = runBlocking {
        val client = FakeClient()
        val channel = FakeChannel()
        val good = Socket()
        val broken = Socket()
        channel.incoming[good] = IncomingMessage(byteArrayOf(5), byteArrayOf(6, 7))
        channel.incoming[broken] = null // сбой рукопожатия или фиктивный пакет
        val repo = RelayTransportRepository(channel, client, FakeProvider(listOf(endpoint1), emptyList()))

        client.tunnels.send(broken)
        client.tunnels.send(good)
        val packet = withTimeout(3_000) { repo.incomingPackets().first() }
        assertEquals(listOf<Byte>(5), packet.first.toList())
        assertEquals(listOf<Byte>(6, 7), packet.second.toList())
    }

    @Test
    fun `состояния регистрации попадают в провайдер`() = runBlocking {
        val client = FakeClient()
        val channel = FakeChannel()
        val provider = FakeProvider(listOf(endpoint1), emptyList())
        val repo = RelayTransportRepository(channel, client, provider)
        val good = Socket()
        channel.incoming[good] = IncomingMessage(byteArrayOf(1), byteArrayOf(2))

        client.states.send(RelayConnectionState.Connecting)
        client.states.send(RelayConnectionState.Failed(RelayFailure.UNREACHABLE, 1_000))
        client.states.send(RelayConnectionState.Registered)
        client.tunnels.send(good)
        withTimeout(3_000) { repo.incomingPackets().first() }

        assertEquals(
            listOf<Pair<RelayEndpoint, RelayFailure?>>(endpoint1 to RelayFailure.UNREACHABLE, endpoint1 to null),
            provider.reports.toList()
        )
    }

    private fun collectInBackground(repo: RelayTransportRepository) =
        CoroutineScope(Dispatchers.Default).launch { repo.incomingPackets().collect { } }

    private suspend fun awaitSubscriptions(client: FakeClient, expected: Int) {
        withTimeout(3_000) { while (client.activeSubscriptions.get() != expected) delay(10) }
    }

    @Test
    fun `выключение релея в настройках отменяет подписку`() = runBlocking {
        val client = FakeClient()
        val provider = FakeProvider(listOf(endpoint1), emptyList())
        val job = collectInBackground(RelayTransportRepository(FakeChannel(), client, provider))
        try {
            awaitSubscriptions(client, 1)
            provider.own.value = emptyList()
            awaitSubscriptions(client, 0)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun `смена релея пересоздаёт подписку`() = runBlocking {
        val client = FakeClient()
        val provider = FakeProvider(listOf(endpoint1), emptyList())
        val job = collectInBackground(RelayTransportRepository(FakeChannel(), client, provider))
        try {
            awaitSubscriptions(client, 1)
            provider.own.value = listOf(endpoint2)
            delay(300)
            awaitSubscriptions(client, 1) // старая отменена, новая работает
        } finally {
            job.cancel()
        }
        awaitSubscriptions(client, 0)
    }

    // ---- пул: запасные релеи, бюджет времени, залипание ----

    private fun send(repo: RelayTransportRepository, c: Contact = contact) = runBlocking { repo.sendEncryptedPacket(c, byteArrayOf(1)) }

    @Test
    fun `первый релей недоступен — доставка через второй, это запасной вариант`() {
        val client = FakeClient()
        val socket = Socket()
        client.connectResults[endpoint1] = RelayConnectResult.Failed(RelayFailure.UNREACHABLE)
        client.connectResults[endpoint2] = RelayConnectResult.Connected(socket)
        val provider = FakeProvider(emptyList(), listOf(endpoint1, endpoint2))
        val repo = RelayTransportRepository(FakeChannel(), client, provider)

        assertTrue(send(repo))
        assertEquals(listOf(endpoint1, endpoint2), client.connectCalls.map { it.first })
        assertEquals(listOf<Pair<RelayEndpoint, RelayFailure?>>(endpoint1 to RelayFailure.UNREACHABLE, endpoint2 to null), provider.reports.toList())
        assertEquals(listOf(Triple(endpoint2, contact, true)), provider.deliveries.toList())
        assertTrue(socket.isClosed)
    }

    @Test
    fun `доставка через первый релей — не запасной вариант`() {
        val client = FakeClient()
        client.connectResults[endpoint1] = RelayConnectResult.Connected(Socket())
        val provider = FakeProvider(emptyList(), listOf(endpoint1, endpoint2))
        assertTrue(send(RelayTransportRepository(FakeChannel(), client, provider)))
        assertEquals(listOf(Triple(endpoint1, contact, false)), provider.deliveries.toList())
        assertEquals(1, client.connectCalls.size)
    }

    @Test
    fun `рукопожатие не удалось — доставки нет и она не отмечается`() {
        val client = FakeClient()
        client.connectResults[endpoint1] = RelayConnectResult.Connected(Socket())
        val provider = FakeProvider(emptyList(), listOf(endpoint1, endpoint2))
        val channel = FakeChannel().also { it.sendResult = false }
        assertFalse(send(RelayTransportRepository(channel, client, provider)))
        assertTrue(provider.deliveries.isEmpty())
    }

    @Test
    fun `получатель не в сети ни на одном релее — false, релеи не штрафуются, доставки нет`() {
        val client = FakeClient()
        for (e in listOf(endpoint1, endpoint2, endpoint3)) client.connectResults[e] = RelayConnectResult.Failed(RelayFailure.TARGET_OFFLINE)
        val provider = FakeProvider(emptyList(), listOf(endpoint1, endpoint2, endpoint3))
        assertFalse(send(RelayTransportRepository(FakeChannel(), client, provider)))
        assertEquals(3, client.connectCalls.size)
        assertTrue(provider.reports.all { it.second == null })
        assertTrue(provider.deliveries.isEmpty())
    }

    @Test
    fun `бюджет времени — первому релею до 15 с, запасным до 8 с, остаток урезается`() {
        val clock = FakeClock()
        val client = FakeClient()
        val spent = listOf(12_000L, 8_000L, 5_000L, 1_000L)
        var attempt = 0
        client.onConnect = { clock.advance(spent[attempt++]) }
        val provider = FakeProvider(emptyList(), listOf(endpoint1, endpoint2, endpoint3, endpoint4))
        val repo = RelayTransportRepository(FakeChannel(), client, provider, clock)

        assertFalse(send(repo))
        // 25 с всего: 15 (потолок) → после 12 с осталось 13, берём потолок запасного 8 → после 8 осталось 5 →
        // после 5 осталось 0, четвёртый релей не пробуем.
        assertEquals(listOf<Long?>(15_000L, 8_000L, 5_000L), client.budgets.toList())
        assertEquals(3, client.connectCalls.size)
    }

    @Test
    fun `бюджет — быстрые отказы оставляют всем запасным релеям полный запасной потолок`() {
        val clock = FakeClock()
        val client = FakeClient()
        client.onConnect = { clock.advance(100) }
        val provider = FakeProvider(emptyList(), listOf(endpoint1, endpoint2, endpoint3))
        send(RelayTransportRepository(FakeChannel(), client, provider, clock))
        assertEquals(listOf<Long?>(15_000L, 8_000L, 8_000L), client.budgets.toList())
    }

    @Test
    fun `бюджет — меньше минимума на попытку не начинаем, первый релей съел почти всё`() {
        val clock = FakeClock()
        val client = FakeClient()
        client.onConnect = { clock.advance(24_500) }
        val provider = FakeProvider(emptyList(), listOf(endpoint1, endpoint2))
        assertFalse(send(RelayTransportRepository(FakeChannel(), client, provider, clock)))
        assertEquals(1, client.connectCalls.size) // осталось 500 мс < 1 с
    }

    @Test
    fun `свой бюджет задаётся конфигурацией`() {
        val clock = FakeClock()
        val client = FakeClient()
        val config = RelaySendConfig(totalBudgetMillis = 10_000, firstAttemptMillis = 6_000, fallbackAttemptMillis = 3_000, minAttemptMillis = 500)
        val provider = FakeProvider(emptyList(), listOf(endpoint1, endpoint2))
        send(RelayTransportRepository(FakeChannel(), client, provider, clock, config))
        assertEquals(listOf<Long?>(6_000L, 3_000L), client.budgets.toList())
    }

    private fun poolSourceOf(vararg endpoints: RelayEndpoint, batterySaver: Boolean = false) = object : RelayPoolSource {
        override val pool: StateFlow<RelayPool> = MutableStateFlow(
            RelayPool(
                enabled = true,
                batterySaver = batterySaver,
                entries = endpoints.mapIndexed { i, e -> RelayEntry("id$i", e.address, e.token, e.pinSha256) }
            )
        )
    }

    @Test
    fun `залипание — после доставки через запасной релей он идёт первым для этого контакта, для других нет`() {
        val clock = FakeClock()
        val client = FakeClient()
        client.connectResults[endpoint1] = RelayConnectResult.Failed(RelayFailure.TARGET_OFFLINE) // собеседник Алисы не на нём
        client.connectResults[endpoint2] = RelayConnectResult.Connected(Socket())
        val provider = PoolRelayProvider(poolSourceOf(endpoint1, endpoint2), clock)
        val repo = RelayTransportRepository(FakeChannel(), client, provider, clock)
        val alice = contact.copy(localId = "alice")
        val bob = contact.copy(localId = "bob")

        assertTrue(send(repo, alice))
        assertEquals(listOf(endpoint1, endpoint2), client.connectCalls.map { it.first })

        client.connectCalls.clear()
        client.connectResults[endpoint2] = RelayConnectResult.Connected(Socket())
        assertTrue(send(repo, alice))
        assertEquals(listOf(endpoint2), client.connectCalls.map { it.first }) // залипший релей первым, второй не нужен

        client.connectCalls.clear()
        client.connectResults[endpoint2] = RelayConnectResult.Connected(Socket())
        assertTrue(send(repo, bob))
        assertEquals(listOf(endpoint1, endpoint2), client.connectCalls.map { it.first }) // у Боба порядок из настроек

        client.connectCalls.clear()
        clock.advance(10 * 60_000) // залипание истекло
        client.connectResults[endpoint2] = RelayConnectResult.Connected(Socket())
        assertTrue(send(repo, alice))
        assertEquals(listOf(endpoint1, endpoint2), client.connectCalls.map { it.first })
    }

    @Test
    fun `мёртвый релей уходит в конец очереди после ошибки и возвращается после паузы`() {
        val clock = FakeClock()
        val client = FakeClient()
        client.connectResults[endpoint1] = RelayConnectResult.Failed(RelayFailure.UNREACHABLE)
        client.connectResults[endpoint2] = RelayConnectResult.Connected(Socket())
        val provider = PoolRelayProvider(poolSourceOf(endpoint1, endpoint2), clock)
        val repo = RelayTransportRepository(FakeChannel(), client, provider, clock)

        assertTrue(send(repo, contact.copy(localId = "x")))
        assertEquals(listOf(endpoint1, endpoint2), client.connectCalls.map { it.first })

        client.connectCalls.clear()
        client.connectResults[endpoint2] = RelayConnectResult.Connected(Socket())
        assertTrue(send(repo, contact.copy(localId = "y"))) // другой контакт: залипания нет, но релей 1 в паузе
        assertEquals(listOf(endpoint2), client.connectCalls.map { it.first })

        client.connectCalls.clear()
        clock.advance(30_000)
        client.connectResults[endpoint1] = RelayConnectResult.Connected(Socket())
        assertTrue(send(repo, contact.copy(localId = "z")))
        assertEquals(listOf(endpoint1), client.connectCalls.map { it.first }) // пауза кончилась, приоритет вернулся
    }

    @Test
    fun `если мертвы все релеи, пробуются все по приоритету`() {
        val clock = FakeClock()
        val client = FakeClient()
        val provider = PoolRelayProvider(poolSourceOf(endpoint1, endpoint2, endpoint3), clock)
        val repo = RelayTransportRepository(FakeChannel(), client, provider, clock)

        assertFalse(send(repo))
        client.connectCalls.clear()
        assertFalse(send(repo)) // все уже в паузе
        assertEquals(listOf(endpoint1, endpoint2, endpoint3), client.connectCalls.map { it.first })
    }

    // ---- приём: подписки по релеям ----

    private suspend fun awaitCondition(what: String, condition: () -> Boolean) {
        withTimeout(3_000) { while (!condition()) delay(10) }
    }

    @Test
    fun `перестановка релеёв не перезапускает рабочие регистрации`() = runBlocking {
        val client = FakeClient()
        val provider = FakeProvider(listOf(endpoint1, endpoint2), emptyList())
        val job = collectInBackground(RelayTransportRepository(FakeChannel(), client, provider))
        try {
            awaitCondition("обе регистрации") { client.active(endpoint1) == 1 && client.active(endpoint2) == 1 }

            provider.own.value = listOf(endpoint2, endpoint1)
            delay(300)
            assertEquals(1, client.started(endpoint1))
            assertEquals(1, client.started(endpoint2))
            assertEquals(1, client.active(endpoint1))
            assertEquals(1, client.active(endpoint2))
        } finally {
            job.cancel()
        }
    }

    @Test
    fun `удаление одного релея отменяет только его регистрацию, добавление запускает только новую`() = runBlocking {
        val client = FakeClient()
        val provider = FakeProvider(listOf(endpoint1, endpoint2), emptyList())
        val job = collectInBackground(RelayTransportRepository(FakeChannel(), client, provider))
        try {
            awaitCondition("обе регистрации") { client.active(endpoint1) == 1 && client.active(endpoint2) == 1 }

            provider.own.value = listOf(endpoint2)
            awaitCondition("регистрация 1 отменена") { client.active(endpoint1) == 0 }
            assertEquals(1, client.active(endpoint2))
            assertEquals(1, client.started(endpoint2)) // вторая не перезапускалась

            provider.own.value = listOf(endpoint2, endpoint3)
            awaitCondition("регистрация 3 запущена") { client.active(endpoint3) == 1 }
            assertEquals(1, client.started(endpoint2))
            assertEquals(1, client.started(endpoint1))
        } finally {
            job.cancel()
        }
    }

    @Test
    fun `смена токена релея перезапускает именно его`() = runBlocking {
        val client = FakeClient()
        val provider = FakeProvider(listOf(endpoint1, endpoint2), emptyList())
        val job = collectInBackground(RelayTransportRepository(FakeChannel(), client, provider))
        try {
            awaitCondition("обе регистрации") { client.active(endpoint1) == 1 && client.active(endpoint2) == 1 }
            val renewed = endpoint1.copy(token = "another-token-0123456789")
            provider.own.value = listOf(renewed, endpoint2)
            awaitCondition("новая регистрация") { client.active(renewed) == 1 }
            assertEquals(0, client.active(endpoint1))
            assertEquals(1, client.started(endpoint2))
        } finally {
            job.cancel()
        }
    }

    @Test
    fun `отмена приёма закрывает все регистрации`() = runBlocking {
        val client = FakeClient()
        val provider = FakeProvider(listOf(endpoint1, endpoint2, endpoint3), emptyList())
        val job = collectInBackground(RelayTransportRepository(FakeChannel(), client, provider))
        awaitCondition("три регистрации") { client.activeSubscriptions.get() == 3 }
        job.cancel()
        awaitCondition("всё закрыто") { client.activeSubscriptions.get() == 0 }
    }

    @Test
    fun `состояния регистраций попадают в пул — подключено и ошибка по адресам`() = runBlocking {
        val client = FakeClient()
        val clock = FakeClock()
        val provider = PoolRelayProvider(poolSourceOf(endpoint1), clock)
        client.states.send(RelayConnectionState.Registered)
        val job = collectInBackground(RelayTransportRepository(FakeChannel(), client, provider, clock))
        try {
            awaitCondition("состояние Connected") { provider.connectionStates.value[endpoint1.address] == RelayLinkState.Connected }
        } finally {
            job.cancel()
        }
    }

    @Test
    fun `режим экономии — в приёме только один релей пула`() = runBlocking {
        val client = FakeClient()
        val provider = PoolRelayProvider(poolSourceOf(endpoint1, endpoint2, endpoint3, batterySaver = true), FakeClock())
        val job = collectInBackground(RelayTransportRepository(FakeChannel(), client, provider))
        try {
            awaitCondition("одна регистрация") { client.activeSubscriptions.get() == 1 }
            delay(200)
            assertEquals(1, client.active(endpoint1))
            assertEquals(0, client.active(endpoint2))
            assertEquals(0, client.active(endpoint3))
        } finally {
            job.cancel()
        }
    }
}
