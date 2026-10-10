package com.ramka.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class AppMessageTest {

    private fun uuid() = UUID.randomUUID().toString()

    @Test
    fun `text round-trips with messageId and unicode body`() {
        val original = AppMessage.Text(uuid(), "привет, мир — 你好 ✓")
        assertEquals(original, AppMessage.decode(original.encode()))
    }

    @Test
    fun `text with empty body round-trips`() {
        val original = AppMessage.Text(uuid(), "")
        assertEquals(original, AppMessage.decode(original.encode()))
    }

    @Test
    fun `delivered ack round-trips and carries only the id`() {
        val id = uuid()
        val encoded = AppMessage.DeliveredAck(id).encode()
        assertEquals(1 + 36, encoded.size) // тег + UUID, больше ничего (ни времени, ни ID контакта)
        assertEquals(AppMessage.DeliveredAck(id), AppMessage.decode(encoded))
    }

    @Test
    fun `read ack with a single id round-trips`() {
        val original = AppMessage.ReadAck(listOf(uuid()))
        assertEquals(original, AppMessage.decode(original.encode()))
    }

    @Test
    fun `read ack batch round-trips preserving order`() {
        val original = AppMessage.ReadAck(List(5) { uuid() })
        val encoded = original.encode()
        assertEquals(1 + 5 * 36, encoded.size)
        assertEquals(original, AppMessage.decode(encoded))
    }

    @Test
    fun `ack types are distinguished by tag`() {
        val id = uuid()
        assertTrue(AppMessage.decode(AppMessage.DeliveredAck(id).encode()) is AppMessage.DeliveredAck)
        assertTrue(AppMessage.decode(AppMessage.ReadAck(listOf(id)).encode()) is AppMessage.ReadAck)
    }

    @Test
    fun `empty bytes decode to null`() {
        assertNull(AppMessage.decode(ByteArray(0)))
    }

    @Test
    fun `unknown tag decodes to null`() {
        assertNull(AppMessage.decode(byteArrayOf(99) + uuid().toByteArray()))
    }

    @Test
    fun `truncated text envelope decodes to null`() {
        assertNull(AppMessage.decode(byteArrayOf(0) + "short".toByteArray()))
    }

    @Test
    fun `delivered ack with wrong id length decodes to null`() {
        assertNull(AppMessage.decode(byteArrayOf(1) + "not-a-uuid".toByteArray()))
    }

    @Test
    fun `read ack with length not multiple of uuid decodes to null`() {
        assertNull(AppMessage.decode(byteArrayOf(2) + (uuid() + "x").toByteArray()))
    }

    @Test
    fun `read ack with no ids decodes to null`() {
        assertNull(AppMessage.decode(byteArrayOf(2)))
    }

    // --- файлы (этап 6.4) ---

    private fun meta(name: String = "отчёт.pdf", mime: String = "application/pdf") =
        AppMessage.FileMeta(uuid(), name, 123_456_789_012L, 7, 65_490, mime)

    @Test
    fun `file meta round-trips with unicode name and large size`() {
        val original = meta()
        assertEquals(original, AppMessage.decode(original.encode()))
    }

    @Test
    fun `file meta with empty name and mime round-trips`() {
        val original = meta(name = "", mime = "")
        assertEquals(original, AppMessage.decode(original.encode()))
    }

    @Test
    fun `file meta too short or with mime length overflowing decodes to null`() {
        assertNull(AppMessage.decode(byteArrayOf(3) + ByteArray(10)))
        val good = meta().encode()
        good[1 + 36 + 16] = 0xFF.toByte() // mimeLen больше остатка
        assertNull(AppMessage.decode(good))
    }

    @Test
    fun `file meta with non-positive chunk size or negative values decodes to null`() {
        val encoded = AppMessage.FileMeta(uuid(), "a", 10, 1, 0, "x/y").encode()
        assertNull(AppMessage.decode(encoded))
        val negative = AppMessage.FileMeta(uuid(), "a", -1, 1, 5, "x/y").encode()
        assertNull(AppMessage.decode(negative))
    }

    @Test
    fun `file chunk round-trips including binary data`() {
        val data = ByteArray(1000) { (it * 7).toByte() }
        val original = AppMessage.FileChunk(uuid(), 41, data)
        assertEquals(original, AppMessage.decode(original.encode()))
    }

    @Test
    fun `file chunk overhead constant matches the encoding`() {
        val chunk = AppMessage.FileChunk(uuid(), 0, ByteArray(100))
        assertEquals(AppMessage.FILE_CHUNK_OVERHEAD + 100, chunk.encode().size)
    }

    @Test
    fun `file chunk truncated or with negative index decodes to null`() {
        assertNull(AppMessage.decode(byteArrayOf(4) + uuid().toByteArray()))
        val negative = byteArrayOf(4) + uuid().toByteArray() + byteArrayOf(-1, -1, -1, -1) + byteArrayOf(1)
        assertNull(AppMessage.decode(negative))
    }
}
