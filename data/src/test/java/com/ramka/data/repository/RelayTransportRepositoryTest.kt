package com.ramka.data.repository

import com.ramka.domain.model.Contact
import com.ramka.domain.model.KeyVerificationStatus
import com.ramka.domain.relay.RelayAddress
import com.ramka.domain.relay.RelayEndpoint
import com.ramka.domain.relay.RelayFailure
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class RelayTransportRepositoryTest {

    private val endpoint1 = RelayEndpoint(RelayAddress("relay1.example.com", 48766), "token-token-token-1", null)
    private val endpoint2 = RelayEndpoint(RelayAddress("relay2.example.com", 48766), "token-token-token-2", null)

    private val contact = Contact(
        localId = "c1", alias = "a", publicKey = ByteArray(32) { 1 }, signingPublicKey = ByteArray(32) { 2 },
        lastKnownAddress = null, verification = KeyVerificationStatus.UNVERIFIED, addedAtEpochDay = 0
    )

    private class FakeProvider(initialOwn: List<RelayEndpoint>, var sendTo: List<RelayEndpoint>) : RelayProvider {
        val own = MutableStateFlow(initialOwn)
        val reports = CopyOnWriteArrayList<Pair<RelayEndpoint, RelayFailure?>>()
        override val ownRelays: Flow<List<RelayEndpoint>> = own
        override suspend fun relaysFor(contact: Contact): List<RelayEndpoint> = sendTo
        override fun report(endpoint: RelayEndpoint, failure: RelayFailure?) {
            reports.add(endpoint to failure)
        }
    }

    private class FakeClient : RelayTunnelClient {
        val connectResults = HashMap<RelayEndpoint, RelayConnectResult>()
        val connectCalls = CopyOnWriteArrayList<Pair<RelayEndpoint, ByteArray>>()
        val tunnels = Channel<Socket>(Channel.UNLIMITED)
        val states = Channel<RelayConnectionState>(Channel.UNLIMITED)
        val activeSubscriptions = AtomicInteger(0)

        override suspend fun connectTo(endpoint: RelayEndpoint, targetSigningPublicKey: ByteArray): RelayConnectResult {
            connectCalls.add(endpoint to targetSigningPublicKey)
            return connectResults[endpoint] ?: RelayConnectResult.Failed(RelayFailure.UNREACHABLE)
        }

        override fun incoming(endpoint: RelayEndpoint, onState: (RelayConnectionState) -> Unit): Flow<Socket> = channelFlow {
            activeSubscriptions.incrementAndGet()
            try {
                // Состояния, поставленные тестом в очередь, передаём подписчику.
                while (true) {
                    val r = states.tryReceive()
                    if (r.isSuccess) onState(r.getOrThrow()) else break
                }
                for (s in tunnels) send(s)
            } finally {
                activeSubscriptions.decrementAndGet()
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
            override suspend fun connectTo(endpoint: RelayEndpoint, targetSigningPublicKey: ByteArray): RelayConnectResult =
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
}
