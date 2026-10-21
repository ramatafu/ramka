package com.ramka.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class DeleteMessageAppMessageTest {

    private val id = UUID.randomUUID().toString()

    @Test
    fun `DeleteMessage round trips and is 37 bytes`() {
        val encoded = AppMessage.DeleteMessage(id).encode()
        assertEquals(1 + 36, encoded.size)
        assertEquals(AppMessage.DeleteMessage(id), AppMessage.decode(encoded))
    }

    @Test
    fun `DeleteMessage with wrong length is rejected`() {
        val encoded = AppMessage.DeleteMessage(id).encode()
        assertNull(AppMessage.decode(encoded.copyOf(encoded.size - 1)))
        assertNull(AppMessage.decode(encoded + byteArrayOf(0)))
    }
}
