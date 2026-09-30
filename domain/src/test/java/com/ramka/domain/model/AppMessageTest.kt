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
}
