package com.ramka.data.repository

import com.ramka.domain.model.Contact
import com.ramka.domain.model.KeyVerificationStatus
import com.ramka.domain.model.NetworkAddress
import com.ramka.domain.repository.TransportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositeTransportRepositoryTest {

    private class FakeTransport(var result: Boolean, var throwOnSend: Boolean = false) : TransportRepository {
        var calls = 0
        val incoming = MutableSharedFlow<Pair<ByteArray, ByteArray>>(extraBufferCapacity = 16)

        override suspend fun sendEncryptedPacket(contact: Contact, packet: ByteArray): Boolean {
            calls++
            if (throwOnSend) throw IllegalStateException("транспорт сломан")
            return result
        }

        override fun incomingPackets(): Flow<Pair<ByteArray, ByteArray>> = incoming
    }

    private fun contact(address: NetworkAddress?) = Contact(
        localId = "c1", alias = "a", publicKey = ByteArray(32), signingPublicKey = ByteArray(32),
        lastKnownAddress = address, verification = KeyVerificationStatus.UNVERIFIED, addedAtEpochDay = 0
    )

    private val lanAddress = NetworkAddress.Lan("192.168.1.5", 48765)
    private val relayAddress = NetworkAddress.Relay("relay.example.com:48766", "")

    private class Setup(lanResult: Boolean, relayResult: Boolean, relayOn: Boolean, onLan: Boolean) {
        val lan = FakeTransport(lanResult)
        val relay = FakeTransport(relayResult)
        val composite = CompositeTransportRepository(lan, relay, relayEnabled = { relayOn }, onLocalNetwork = { onLan })
    }

    private fun send(s: Setup, address: NetworkAddress?): Boolean =
        runBlocking { s.composite.sendEncryptedPacket(contact(address), byteArrayOf(1)) }

    @Test
    fun `relay выключен — поведение как раньше, только LAN, даже без Wi-Fi`() {
        val ok = Setup(lanResult = true, relayResult = true, relayOn = false, onLan = false)
        assertTrue(send(ok, lanAddress))
        assertEquals(1, ok.lan.calls)
        assertEquals(0, ok.relay.calls)

        val fail = Setup(lanResult = false, relayResult = true, relayOn = false, onLan = true)
        assertFalse(send(fail, lanAddress))
        assertEquals(1, fail.lan.calls)
        assertEquals(0, fail.relay.calls)
    }

    @Test
    fun `relay включён, LAN доступен и сработал — relay не трогаем`() {
        val s = Setup(lanResult = true, relayResult = true, relayOn = true, onLan = true)
        assertTrue(send(s, lanAddress))
        assertEquals(1, s.lan.calls)
        assertEquals(0, s.relay.calls)
    }

    @Test
    fun `relay включён, LAN не вышел — запасной путь через relay`() {
        val good = Setup(lanResult = false, relayResult = true, relayOn = true, onLan = true)
        assertTrue(send(good, lanAddress))
        assertEquals(1, good.lan.calls)
        assertEquals(1, good.relay.calls)

        val bad = Setup(lanResult = false, relayResult = false, relayOn = true, onLan = true)
        assertFalse(send(bad, lanAddress))
        assertEquals(1, bad.relay.calls)
    }

    @Test
    fun `relay включён, устройство вне локальной сети — relay первым, LAN не трогаем при успехе`() {
        val s = Setup(lanResult = true, relayResult = true, relayOn = true, onLan = false)
        assertTrue(send(s, lanAddress))
        assertEquals(0, s.lan.calls)
        assertEquals(1, s.relay.calls)
    }

    @Test
    fun `вне локальной сети relay не сработал — LAN остаётся запасным вариантом`() {
        val s = Setup(lanResult = true, relayResult = false, relayOn = true, onLan = false)
        assertTrue(send(s, lanAddress))
        assertEquals(1, s.relay.calls)
        assertEquals(1, s.lan.calls)

        val none = Setup(lanResult = false, relayResult = false, relayOn = true, onLan = false)
        assertFalse(send(none, lanAddress))
        assertEquals(1, none.relay.calls)
        assertEquals(1, none.lan.calls)
    }

    @Test
    fun `адрес relay или отсутствие адреса — сразу relay`() {
        for (address in listOf(relayAddress, null)) {
            val s = Setup(lanResult = true, relayResult = true, relayOn = true, onLan = true)
            assertTrue("адрес $address", send(s, address))
            assertEquals(0, s.lan.calls)
            assertEquals(1, s.relay.calls)
        }
    }

    @Test
    fun `адрес не LAN и relay выключен — ничего не вызывается, false`() {
        for (address in listOf(relayAddress, null)) {
            val s = Setup(lanResult = true, relayResult = true, relayOn = false, onLan = true)
            assertFalse("адрес $address", send(s, address))
            assertEquals(0, s.lan.calls)
            assertEquals(0, s.relay.calls)
        }
    }

    @Test
    fun `исключение транспорта — это неудача, а не падение, запасной путь всё равно пробуется`() {
        val s = Setup(lanResult = false, relayResult = true, relayOn = true, onLan = true)
        s.lan.throwOnSend = true
        assertTrue(send(s, lanAddress)) // LAN бросил, relay доставил
        assertEquals(1, s.relay.calls)

        val s2 = Setup(lanResult = true, relayResult = false, relayOn = true, onLan = true)
        s2.lan.throwOnSend = true
        s2.relay.throwOnSend = true
        assertFalse(send(s2, lanAddress)) // оба бросили: false, без исключения

        val s3 = Setup(lanResult = false, relayResult = false, relayOn = false, onLan = true)
        s3.lan.throwOnSend = true
        assertFalse(send(s3, lanAddress))
    }

    @Test
    fun `состояние relay читается на каждую отправку`() = runBlocking {
        var relayOn = false
        val lan = FakeTransport(false)
        val relay = FakeTransport(true)
        val composite = CompositeTransportRepository(lan, relay, { relayOn }, { true })
        assertFalse(composite.sendEncryptedPacket(contact(lanAddress), byteArrayOf(1)))
        relayOn = true
        assertTrue(composite.sendEncryptedPacket(contact(lanAddress), byteArrayOf(1)))
    }

    @Test
    fun `входящие объединяются из LAN и relay`() = runBlocking {
        val s = Setup(lanResult = false, relayResult = false, relayOn = true, onLan = true)
        val collected = CompletableDeferred<List<Pair<ByteArray, ByteArray>>>()
        val job = launch {
            collected.complete(s.composite.incomingPackets().take(2).toList())
        }
        // Подписчики появляются асинхронно; повторяем эмиссию, пока не получим оба пакета.
        withTimeout(3_000) {
            while (!collected.isCompleted) {
                s.lan.incoming.tryEmit(byteArrayOf(1) to byteArrayOf(10))
                s.relay.incoming.tryEmit(byteArrayOf(2) to byteArrayOf(20))
                delay(10)
            }
        }
        job.join()
        val packets = collected.await()
        assertEquals(setOf(10, 20), packets.map { it.second[0].toInt() }.toSet())
    }
}
