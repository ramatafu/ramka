package com.ramka.app.ui.chat

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PickedFileImporterTest {
    @get:Rule val tmp = TemporaryFolder()

    // --- pickExtension ---
    @Test fun extensionFromName() = assertEquals("pdf", pickExtension("Report.PDF", "bin"))
    @Test fun lastExtensionWins() = assertEquals("gz", pickExtension("a.tar.gz", null))
    @Test fun fallsBackToMime() = assertEquals("jpg", pickExtension("photo", "jpg"))
    @Test fun noDotFallsBackToMime() = assertEquals("png", pickExtension("noext", "png"))
    @Test fun garbageNameExtensionRejected() = assertEquals("txt", pickExtension("a.b/../c d", "txt"))
    @Test fun tooLongExtensionRejected() = assertNull(pickExtension("a.abcdefghijk", null))
    @Test fun nothingAvailable() = assertNull(pickExtension(null, null))

    // --- generateStoredName ---
    @Test fun storedNameWithExtension() {
        val id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        assertEquals("123e4567-e89b-12d3-a456-426614174000.pdf", generateStoredName("pdf", id))
    }
    @Test fun storedNameWithoutExtension() {
        val id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        assertEquals("123e4567-e89b-12d3-a456-426614174000", generateStoredName(null, id))
    }
    @Test fun storedNamesAreUnique() = assertTrue(generateStoredName("x") != generateStoredName("x"))

    // --- copyToFile ---
    @Test fun copiesContentAndReturnsSize() = runBlocking {
        val data = ByteArray(200_000) { (it % 251).toByte() } // больше одного буфера
        val target = File(tmp.root, "out.bin")
        val size = copyToFile(ByteArrayInputStream(data), target)
        assertEquals(200_000L, size)
        assertArrayEquals(data, target.readBytes())
        assertFalse(File(tmp.root, "out.bin.part").exists())
    }

    @Test fun exceedingLimitThrowsAndLeavesNothing() = runBlocking {
        val target = File(tmp.root, "big.bin")
        try {
            copyToFile(ByteArrayInputStream(ByteArray(1000)), target, maxBytes = 999)
            fail("expected FileTooLargeException")
        } catch (e: FileTooLargeException) {
            assertEquals(999L, e.maxBytes)
        }
        assertFalse(target.exists())
        assertFalse(File(tmp.root, "big.bin.part").exists())
    }

    @Test fun exactlyAtLimitIsAccepted() = runBlocking {
        val target = File(tmp.root, "ok.bin")
        assertEquals(1000L, copyToFile(ByteArrayInputStream(ByteArray(1000)), target, maxBytes = 1000))
    }

    @Test fun emptyInputGivesEmptyFile() = runBlocking {
        val target = File(tmp.root, "empty")
        assertEquals(0L, copyToFile(ByteArrayInputStream(ByteArray(0)), target))
        assertTrue(target.exists())
    }

    @Test fun failureRemovesPartialAndTarget() = runBlocking {
        val failing = object : InputStream() {
            private var calls = 0
            override fun read(): Int = throw IOException("boom")
            override fun read(b: ByteArray): Int {
                if (calls++ == 0) { b[0] = 1; return 1 }
                throw IOException("boom")
            }
        }
        val target = File(tmp.root, "bad.bin")
        try {
            copyToFile(failing, target)
            fail("expected IOException")
        } catch (e: IOException) {
            assertEquals("boom", e.message)
        }
        assertFalse(target.exists())
        assertFalse(File(tmp.root, "bad.bin.part").exists())
    }
}
