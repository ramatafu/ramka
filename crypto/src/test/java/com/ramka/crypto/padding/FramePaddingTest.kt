package com.ramka.crypto.padding

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FramePaddingTest {

    private fun bytes(size: Int): ByteArray = ByteArray(size) { ((it % 251) + 1).toByte() }

    /** Размер `AppMessage.DeliveredAck.encode()`: 1 байт тега + UUID из 36 ASCII-символов. */
    private val ackSize = 1 + 36

    @Test
    fun `round trip for a range of sizes`() {
        for (size in listOf(0, 1, 37, 100, 123, 124, 250, 251, 1000, 4091, 4092, 4096, 5000, 9000, 70_000)) {
            val payload = bytes(size)
            val unpadded = FramePadding.unpad(FramePadding.pad(payload))
            assertNotNull("size=$size", unpadded)
            assertArrayEquals("size=$size", payload, unpadded!!.payload)
            assertEquals(FramePadding.FLAG_NONE, unpadded.flags)
        }
    }

    @Test
    fun `ack and short text have identical padded size`() {
        val ack = FramePadding.pad(bytes(ackSize))
        val shortText = FramePadding.pad(bytes(ackSize + 40)) // текст из 40 байт
        val longestInFirstBucket = FramePadding.pad(bytes(FramePadding.BUCKETS[0] - FramePadding.HEADER_SIZE))
        assertEquals(FramePadding.BUCKETS[0], ack.size)
        assertEquals(ack.size, shortText.size)
        assertEquals(ack.size, longestInFirstBucket.size)
    }

    @Test
    fun `read ack with a few ids stays in the smallest bucket`() {
        // 1 байт тега + 3 UUID по 36 байт = 109; с заголовком 114 <= 128.
        assertEquals(FramePadding.BUCKETS[0], FramePadding.pad(bytes(1 + 36 * 3)).size)
    }

    @Test
    fun `bucket boundaries`() {
        assertEquals(128, FramePadding.paddedSize(0))
        assertEquals(128, FramePadding.paddedSize(123))
        assertEquals(256, FramePadding.paddedSize(124))
        assertEquals(4096, FramePadding.paddedSize(4091))
        assertEquals(8192, FramePadding.paddedSize(4092))
        assertEquals(8192, FramePadding.paddedSize(8187))
        assertEquals(12288, FramePadding.paddedSize(8188))
    }

    @Test
    fun `padded size is always a bucket or a multiple of the large step`() {
        for (size in 0..20_000 step 7) {
            val padded = FramePadding.pad(bytes(size))
            val ok = padded.size in FramePadding.BUCKETS.toList() || padded.size % FramePadding.LARGE_STEP == 0
            assertTrue("size=$size padded=${padded.size}", ok)
            assertTrue(padded.size >= size + FramePadding.HEADER_SIZE)
        }
    }

    @Test
    fun `flags survive round trip`() {
        val unpadded = FramePadding.unpad(FramePadding.pad(bytes(10), FramePadding.FLAG_COVER))
        assertNotNull(unpadded)
        assertEquals(FramePadding.FLAG_COVER, unpadded!!.flags)
    }

    @Test
    fun `pad does not modify input`() {
        val payload = bytes(50)
        val copy = payload.copyOf()
        FramePadding.pad(payload)
        assertArrayEquals(copy, payload)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `pad rejects oversized payload`() {
        FramePadding.paddedSize(FramePadding.MAX_PAYLOAD_SIZE + 1)
    }

    @Test
    fun `max payload size fits exactly`() {
        assertEquals(FramePadding.MAX_PADDED_SIZE, FramePadding.paddedSize(FramePadding.MAX_PAYLOAD_SIZE))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `pad rejects unknown flags`() {
        FramePadding.pad(bytes(3), flags = 0x80)
    }

    @Test
    fun `unpad rejects too short input`() {
        assertNull(FramePadding.unpad(ByteArray(0)))
        assertNull(FramePadding.unpad(ByteArray(FramePadding.HEADER_SIZE - 1)))
    }

    @Test
    fun `unpad rejects length larger than body`() {
        val padded = FramePadding.pad(bytes(10))
        padded[4] = 0x7F // заявленная длина 127 больше, чем помещается в 128-байтное тело (123)
        assertNull(FramePadding.unpad(padded))
    }

    @Test
    fun `unpad rejects negative length`() {
        val padded = FramePadding.pad(bytes(10))
        padded[1] = 0x80.toByte()
        assertNull(FramePadding.unpad(padded))
    }

    @Test
    fun `unpad rejects non canonical size`() {
        val padded = FramePadding.pad(bytes(10))
        assertNull(FramePadding.unpad(padded + ByteArray(2))) // 130 байт
        assertNull(FramePadding.unpad(padded.copyOf(FramePadding.BUCKETS[1]))) // корзина 256 для тела из 10 байт
    }

    @Test
    fun `unpad rejects non zero tail`() {
        val padded = FramePadding.pad(bytes(10))
        padded[padded.size - 1] = 1
        assertNull(FramePadding.unpad(padded))
    }

    @Test
    fun `unpad rejects unknown flags`() {
        val padded = FramePadding.pad(bytes(10))
        padded[0] = 0x02
        assertNull(FramePadding.unpad(padded))
        padded[0] = 0x80.toByte()
        assertNull(FramePadding.unpad(padded))
    }

    @Test
    fun `unpad result is independent copy`() {
        val padded = FramePadding.pad(bytes(10))
        val unpadded = FramePadding.unpad(padded)!!
        unpadded.payload[0] = 0
        assertFalse(padded[FramePadding.HEADER_SIZE].toInt() == 0)
    }
}
