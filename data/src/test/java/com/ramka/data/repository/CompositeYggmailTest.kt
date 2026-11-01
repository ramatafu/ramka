package com.ramka.data.repository

import com.ramka.domain.model.Contact
import com.ramka.domain.model.KeyVerificationStatus
import com.ramka.domain.model.NetworkAddress
import com.ramka.domain.model.TransportChannel
import com.ramka.domain.repository.TransportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Yggmail — самый последний канал и полностью изолирован: его сбои не влияют на остальные. */
class CompositeYggmailTest {
    private class Fake(
        var result: Boolean,
        val name: String,
        val log: MutableList<String>,
        val onSend: () -> Unit = {},
        val incoming: Flow<Pair<ByteArray, ByteArray>> = emptyFlow()
    ) : TransportRepository {
        override suspend fun sendEncryptedPacket(contact: Contact, packet: ByteArray): Boolean {
            log += name
            onSend()
            return result
        }
        override fun incomingPackets(): Flow<Pair<ByteArray, ByteArray>> = incoming
    }

    private fun contact(lan: Boolean = true) = Contact(
        localId = "c1", alias = "a", publicKey = ByteArray(32), signingPublicKey = ByteArray(32),
        lastKnownAddress = if (lan) NetworkAddress.Lan("192.168.1.5", 48765) else null,
        verification = KeyVerificationStatus.UNVERIFIED, addedAtEpochDay = 0,
        yggmailAddress = "user@[324:71e::53]"
    )

    private class Setup(
        val log: MutableList<String> = mutableListOf(),
        lanOk: Boolean = false, yggOk: Boolean = false, relayOk: Boolean = false, mailOk: Boolean = true,
        mailEnabled: Boolean = true, mailOnSend: () -> Unit = {},
        mailIncoming: Flow<Pair<ByteArray, ByteArray>> = emptyFlow(),
        lanIncoming: Flow<Pair<ByteArray, ByteArray>> = emptyFlow()
    ) {
        val reported = mutableListOf<TransportChannel?>()
        val composite = CompositeTransportRepository(
            lan = Fake(lanOk, "lan", log, incoming = lanIncoming),
            relay = Fake(relayOk, "relay", log),
            relayEnabled = { true },
            onLocalNetwork = { true },
            ygg = Fake(yggOk, "ygg", log),
            yggdrasilEnabled = { true },
            onChannelUsed = { _, c -> reported += c },
            yggmail = Fake(mailOk, "mail", log, onSend = mailOnSend, incoming = mailIncoming),
            yggmailEnabled = { mailEnabled }
        )
        fun send(c: Contact): Boolean = runBlocking { composite.sendEncryptedPacket(c, byteArrayOf(1)) }
    }

    @Test fun `Yggmail пробуется последним, после LAN, Yggdrasil и relay`() {
        val s = Setup()
        assertTrue(s.send(contact()))
        assertEquals(listOf("lan", "ygg", "relay", "mail"), s.log)
        assertEquals(listOf<TransportChannel?>(TransportChannel.YGGMAIL), s.reported)
    }

    @Test fun `если сработал более ранний канал, Yggmail не трогается`() {
        val s = Setup(relayOk = true)
        assertTrue(s.send(contact()))
        assertEquals(listOf("lan", "ygg", "relay"), s.log)
        assertEquals(listOf<TransportChannel?>(TransportChannel.RELAY), s.reported)
    }

    @Test fun `выключенный Yggmail не пробуется`() {
        val s = Setup(mailEnabled = false)
        assertFalse(s.send(contact()))
        assertEquals(listOf("lan", "ygg", "relay"), s.log)
        assertEquals(listOf<TransportChannel?>(null), s.reported)
    }

    @Test fun `исключение Yggmail не ломает отправку`() {
        val s = Setup(mailOnSend = { throw IllegalStateException("boom") })
        assertFalse(s.send(contact()))
        assertEquals(listOf<TransportChannel?>(null), s.reported)
    }

    @Test fun `ошибка загрузки классов Yggmail тоже изолирована`() {
        val s = Setup(mailOnSend = { throw NoClassDefFoundError("javax/mail/Session") })
        assertFalse(s.send(contact()))
        assertEquals(listOf("lan", "ygg", "relay", "mail"), s.log)
    }

    @Test fun `Yggmail отказал — результат false`() {
        val s = Setup(mailOk = false)
        assertFalse(s.send(contact()))
    }

    @Test fun `сбой входящего потока Yggmail не обрывает приём по LAN`() = runBlocking {
        val packet = byteArrayOf(1) to byteArrayOf(2)
        val s = Setup(
            lanIncoming = flow { emit(packet) },
            mailIncoming = flow { throw IllegalStateException("boom") }
        )
        val received = s.composite.incomingPackets().toList()
        assertEquals(1, received.size)
        assertTrue(received[0].first.contentEquals(packet.first))
    }

    @Test fun `входящие пакеты Yggmail доходят до общего потока`() = runBlocking {
        val packet = byteArrayOf(7) to byteArrayOf(8)
        val s = Setup(mailIncoming = flow { emit(packet) })
        val received = s.composite.incomingPackets().toList()
        assertEquals(1, received.size)
        assertTrue(received[0].second.contentEquals(packet.second))
    }
}
