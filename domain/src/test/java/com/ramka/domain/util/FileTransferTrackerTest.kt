package com.ramka.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTransferTrackerTest {
    @Test fun updateClampsAndClears() {
        val t = FileTransferTracker()
        assertFalse(t.isActive("a"))
        t.update("a", 150)
        t.update("b", -5)
        assertEquals(mapOf("a" to 100, "b" to 0), t.progress.value)
        t.clear("a")
        assertTrue(t.isActive("b"))
        assertFalse(t.isActive("a"))
    }

    @Test fun chunkCountAndWireSize() {
        assertEquals(0, FileTransferConfig.chunkCount(0))
        assertEquals(1, FileTransferConfig.chunkCount(1))
        assertEquals(1, FileTransferConfig.chunkCount(FileTransferConfig.CHUNK_SIZE.toLong()))
        assertEquals(2, FileTransferConfig.chunkCount(FileTransferConfig.CHUNK_SIZE + 1L))
        // Пакет чанка с заголовком выравнивания ровно 64 КиБ.
        assertEquals(64 * 1024, 5 + com.ramka.domain.model.AppMessage.FILE_CHUNK_OVERHEAD + FileTransferConfig.CHUNK_SIZE)
    }
}
