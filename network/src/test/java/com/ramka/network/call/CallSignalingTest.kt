package com.ramka.network.call

import com.ramka.domain.model.AppMessage
import com.ramka.domain.model.Contact
import com.ramka.domain.model.KeyVerificationStatus
import com.ramka.domain.model.NetworkAddress
import com.ramka.domain.repository.ContactRepository
import com.ramka.domain.repository.TransportRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

class CallSignalingTest {

    private val callId = UUID.randomUUID().toString()
    private lateinit var scope: CoroutineScope
    private val contact = Contact(
        localId = "alice", alias = "Alice", publicKey = ByteArray(32), signingPublicKey = ByteArray(32),
        lastKnownAddress = NetworkAddress.Lan("192.168.1.10", 48765),
        verification = KeyVerificationStatus.UNVERIFIED, addedAtEpochDay = 0
    )

    @Before fun setUp() { scope = CoroutineScope(Job() + Dispatchers.Default) }
    @After fun tearDown() { scope.cancel() }

    private class FakeTransport(
        private val failFirst: Int = 0,
        private val gate: CompletableDeferred<Unit>? = null
    ) : TransportRepository {
        val sent = CopyOnWriteArrayList<AppMessage>()
        @Volatile var calls = 0

        /** Завершается, когда первая отправка уже дошла до транспорта (и, возможно, ждёт [gate]). */
        val firstCallStarted = CompletableDeferred<Unit>()

        override suspend fun sendEncryptedPacket(contact: Contact, packet: ByteArray): Boolean {
            val n = ++calls
            if (n == 1) {
                firstCallStarted.complete(Unit)
                gate?.await()
            }
            if (n <= failFirst) return false
            sent += AppMessage.decode(packet)!!
            return true
        }
        override fun incomingPackets(): Flow<Pair<ByteArray, ByteArray>> = emptyFlow()
    }

    private class FakeContacts(private val known: Contact?) : ContactRepository {
        override fun observeContacts(): Flow<List<Contact>> = emptyFlow()
        override suspend fun getContact(localId: String): Contact? = known?.takeIf { it.localId == localId }
        override suspend fun addContact(contact: Contact) = Unit
        override suspend fun updateAlias(localId: String, alias: String) = Unit
        override suspend fun updateLastKnownAddress(localId: String, address: NetworkAddress) = Unit
        override suspend fun markKeyChanged(localId: String, newPublicKey: ByteArray) = Unit
        override suspend fun removeContact(localId: String) = Unit
        override suspend fun incrementUnreadCount(localId: String) = Unit
        override suspend fun resetUnreadCount(localId: String) = Unit
    }

    private fun signaling(transport: TransportRepository, known: Contact? = contact) =
        CallSignaling(FakeContacts(known), transport, scope, retryDelayMillis = 1)

    @Test
    fun `signals are delivered strictly in order`() = runBlocking {
        val transport = FakeTransport()
        val s = signaling(transport)
        val results = listOf(
            s.sendOffer("alice", callId, "sdp-offer", withVideo = false),
            s.sendIceCandidate("alice", callId, "0", 0, "candidate:1"),
            s.sendIceCandidate("alice", callId, "0", 0, "candidate:2"),
            s.sendEnd("alice", callId)
        )
        withTimeout(5_000) { results.forEach { assertTrue(it.await()) } }
        assertEquals(
            listOf(
                AppMessage.CallOffer(callId, "sdp-offer", false),
                AppMessage.CallIceCandidate(callId, "0", 0, "candidate:1"),
                AppMessage.CallIceCandidate(callId, "0", 0, "candidate:2"),
                AppMessage.CallEnd(callId)
            ),
            transport.sent.toList()
        )
    }

    @Test
    fun `answer is retried after a failed attempt`() = runBlocking {
        val transport = FakeTransport(failFirst = 1)
        val ok = withTimeout(5_000) { signaling(transport).sendAnswer("alice", callId, "sdp-answer").await() }
        assertTrue(ok)
        assertEquals(2, transport.calls)
        assertEquals(listOf<AppMessage>(AppMessage.CallAnswer(callId, "sdp-answer")), transport.sent.toList())
    }

    @Test
    fun `offer is attempted only once`() = runBlocking {
        val transport = FakeTransport(failFirst = 10)
        val ok = withTimeout(5_000) { signaling(transport).sendOffer("alice", callId, "sdp", false).await() }
        assertFalse(ok)
        assertEquals(1, transport.calls)
    }

    @Test
    fun `unknown contact fails without touching the transport`() = runBlocking {
        val transport = FakeTransport()
        val ok = withTimeout(5_000) { signaling(transport, known = null).sendEnd("alice", callId).await() }
        assertFalse(ok)
        assertEquals(0, transport.calls)
    }

    @Test
    fun `cancelled call skips queued signals but still sends the end`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val transport = FakeTransport(gate = gate)
        val s = signaling(transport)

        val offer = s.sendOffer("alice", callId, "sdp", false)   // застрянет на gate внутри транспорта
        withTimeout(5_000) { transport.firstCallStarted.await() } // offer уже «в полёте», дальше отменяем
        val ice1 = s.sendIceCandidate("alice", callId, "0", 0, "candidate:1")
        val ice2 = s.sendIceCandidate("alice", callId, "0", 0, "candidate:2")
        s.cancelCall(callId)
        val end = s.sendEnd("alice", callId)
        gate.complete(Unit)

        withTimeout(5_000) {
            assertTrue(offer.await())   // уже уходил, когда звонок отменили
            assertFalse(ice1.await())
            assertFalse(ice2.await())
            assertTrue(end.await())
        }
        assertEquals(
            listOf(AppMessage.CallOffer(callId, "sdp", false), AppMessage.CallEnd(callId)),
            transport.sent.toList()
        )
    }

    @Test
    fun `incoming exposes only call signals`() = runBlocking {
        val s = signaling(FakeTransport())
        val first = async(start = CoroutineStart.UNDISPATCHED) { s.incoming.first() }

        s.onIncoming("alice", AppMessage.Text(UUID.randomUUID().toString(), "hello"))
        s.onIncoming("alice", AppMessage.DeliveredAck(UUID.randomUUID().toString()))
        val offer = AppMessage.CallOffer(callId, "sdp", false)
        s.onIncoming("alice", offer)

        val signal = withTimeout(5_000) { first.await() }
        assertEquals(CallSignaling.Signal("alice", offer), signal)
    }

    @Test
    fun `incoming without subscribers does not fail and is not replayed`() = runBlocking {
        val s = signaling(FakeTransport())
        s.onIncoming("alice", AppMessage.CallEnd(callId))
        val late = async(start = CoroutineStart.UNDISPATCHED) { s.incoming.first() }
        delay(50)
        assertFalse(late.isCompleted)
        late.cancel()
    }
}
