package com.ramka.crypto.offline

import com.ramka.crypto.keys.InMemoryKeyValueStore
import com.ramka.crypto.keys.KeyManager
import com.ramka.crypto.noise.IkHandshake
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineSealTest {
    private val alice = KeyManager(InMemoryKeyValueStore())
    private val bob = KeyManager(InMemoryKeyValueStore())
    private val mallory = KeyManager(InMemoryKeyValueStore())

    private val alicePub get() = alice.getOrCreateIdentity().x25519Public
    private val bobPub get() = bob.getOrCreateIdentity().x25519Public
    private val malloryPub get() = mallory.getOrCreateIdentity().x25519Public

    private fun seal(plaintext: ByteArray, from: KeyManager = alice, to: ByteArray = bobPub): ByteArray =
        OfflineSeal.seal(from, to, plaintext)!!

    private fun contains(haystack: ByteArray, needle: ByteArray): Boolean {
        for (i in 0..haystack.size - needle.size) {
            var match = true
            for (j in needle.indices) if (haystack[i + j] != needle[j]) { match = false; break }
            if (match) return true
        }
        return false
    }

    private fun shareWindow(a: ByteArray, b: ByteArray, size: Int): Boolean {
        val seen = HashSet<String>()
        for (i in 0..a.size - size) seen += a.copyOfRange(i, i + size).joinToString(",")
        for (i in 0..b.size - size) if (b.copyOfRange(i, i + size).joinToString(",") in seen) return true
        return false
    }

    @Test fun `round trip delivers plaintext and the authenticated sender key`() {
        val message = "привет, это офлайн-сообщение".toByteArray()
        val opened = OfflineSeal.open(bob, seal(message))
        assertNotNull(opened)
        assertArrayEquals(alicePub, opened!!.senderStaticPublic)
        assertArrayEquals(message, opened.plaintext)
    }

    @Test fun `empty and maximum plaintexts round trip`() {
        assertArrayEquals(ByteArray(0), OfflineSeal.open(bob, seal(ByteArray(0)))!!.plaintext)
        val big = ByteArray(OfflineSeal.MAX_PLAINTEXT_SIZE) { it.toByte() }
        assertArrayEquals(big, OfflineSeal.open(bob, seal(big))!!.plaintext)
    }

    @Test fun `oversized plaintext and bad recipient keys are refused`() {
        assertNull(OfflineSeal.seal(alice, bobPub, ByteArray(OfflineSeal.MAX_PLAINTEXT_SIZE + 1)))
        assertNull(OfflineSeal.seal(alice, ByteArray(31), ByteArray(10)))
        assertNull(OfflineSeal.seal(alice, ByteArray(33), ByteArray(10)))
        assertNull(OfflineSeal.seal(alice, ByteArray(32), ByteArray(10))) // low-order точка
    }

    @Test fun `sealed size depends only on the padding bucket`() {
        val ack = seal(ByteArray(37))
        val shortText = seal(ByteArray(100))
        assertEquals(ack.size, shortText.size)
        assertEquals(OfflineSeal.HEADER_SIZE + 128 + 16, ack.size)
        assertEquals(OfflineSeal.sealedSize(37), ack.size)
        assertEquals(OfflineSeal.sealedSize(5000), seal(ByteArray(5000)).size)
    }

    @Test fun `sender static key is not visible in the sealed bytes`() {
        val sealed = seal("секрет".toByteArray())
        assertFalse(contains(sealed, alicePub))
        assertFalse(contains(sealed, bobPub))
    }

    @Test fun `two seals of the same message look unrelated`() {
        val message = "одно и то же".toByteArray()
        val first = seal(message)
        val second = seal(message)
        assertFalse(first.contentEquals(second))
        assertFalse(shareWindow(first, second, 16))
    }

    @Test fun `plaintext is not visible in the sealed bytes`() {
        val message = "очень секретный текст сообщения".toByteArray()
        assertFalse(contains(seal(message), message))
    }

    @Test fun `only the addressee can open`() {
        val sealed = seal("для Боба".toByteArray())
        assertNull(OfflineSeal.open(mallory, sealed))
        assertNull(OfflineSeal.open(alice, sealed)) // даже отправитель свою посылку не вскроет
    }

    @Test fun `sender reported by open is whoever really sealed it`() {
        val opened = OfflineSeal.open(bob, seal("от Мэллори".toByteArray(), from = mallory))!!
        assertArrayEquals(malloryPub, opened.senderStaticPublic)
        assertFalse(opened.senderStaticPublic.contentEquals(alicePub))
    }

    @Test fun `any single byte change is rejected`() {
        val sealed = seal("целостность".toByteArray())
        for (i in sealed.indices) {
            val tampered = sealed.copyOf()
            tampered[i] = (tampered[i].toInt() xor 0x01).toByte()
            assertNull("байт $i", OfflineSeal.open(bob, tampered))
        }
    }

    @Test fun `high bit of the ephemeral key is covered by the key derivation`() {
        val sealed = seal("старший бит".toByteArray())
        val tampered = sealed.copyOf()
        tampered[31] = (tampered[31].toInt() xor 0x80).toByte() // X25519 игнорирует этот бит, HKDF/AAD — нет
        assertNull(OfflineSeal.open(bob, tampered))
    }

    @Test fun `truncated extended and garbage inputs are rejected without exceptions`() {
        val sealed = seal("x".toByteArray())
        assertNull(OfflineSeal.open(bob, ByteArray(0)))
        assertNull(OfflineSeal.open(bob, ByteArray(79)))
        assertNull(OfflineSeal.open(bob, ByteArray(80)))
        assertNull(OfflineSeal.open(bob, ByteArray(95)))
        assertNull(OfflineSeal.open(bob, sealed.copyOf(sealed.size - 1)))
        assertNull(OfflineSeal.open(bob, sealed + byteArrayOf(0)))
        assertNull(OfflineSeal.open(bob, ByteArray(sealed.size) { (it * 31).toByte() }))
        assertNull(OfflineSeal.open(bob, ByteArray(sealed.size)))
    }

    @Test fun `low order ephemeral point is rejected without exception`() {
        val sealed = seal("точка".toByteArray())
        val broken = sealed.copyOf()
        for (i in 0 until 32) broken[i] = 0
        assertNull(OfflineSeal.open(bob, broken))
    }

    @Test fun `ik handshake messages are not accepted as offline packets`() {
        val message1 = IkHandshake.startInitiator(alice, bobPub).message1
        assertEquals(IkHandshake.MESSAGE1_SIZE, message1.size)
        assertNull(OfflineSeal.open(bob, message1))
    }

    @Test fun `scheme is stateless so a replayed packet opens again and dedup belongs to the message layer`() {
        val sealed = seal("повтор".toByteArray())
        assertNotNull(OfflineSeal.open(bob, sealed))
        assertNotNull(OfflineSeal.open(bob, sealed))
    }
}
