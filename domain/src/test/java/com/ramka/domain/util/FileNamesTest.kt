package com.ramka.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNamesTest {
    @Test fun plainNameUnchanged() = assertEquals("отчёт 2026.pdf", FileNames.sanitizeFileName("отчёт 2026.pdf"))
    @Test fun nullAndBlankGetDefault() {
        assertEquals("file", FileNames.sanitizeFileName(null))
        assertEquals("file", FileNames.sanitizeFileName("   "))
        assertEquals("file", FileNames.sanitizeFileName("..."))
    }
    @Test fun pathComponentsStripped() {
        assertEquals("passwd", FileNames.sanitizeFileName("../../etc/passwd"))
        assertEquals("evil.exe", FileNames.sanitizeFileName("C:\\Windows\\evil.exe"))
    }
    @Test fun forbiddenAndControlCharsRemoved() =
        assertEquals("abc.txt", FileNames.sanitizeFileName("a:b*c?\u0000.t\"x<t>|"))
    @Test fun trailingDotsAndSpacesTrimmed() = assertEquals("a.pdf", FileNames.sanitizeFileName(" a.pdf. "))
    @Test fun longNameKeepsExtension() {
        val result = FileNames.sanitizeFileName("x".repeat(300) + ".jpeg")
        assertEquals(FileNames.MAX_NAME_CHARS, result.length)
        assertTrue(result.endsWith(".jpeg"))
    }
    @Test fun longNameWithoutExtensionTruncated() =
        assertEquals(FileNames.MAX_NAME_CHARS, FileNames.sanitizeFileName("y".repeat(500)).length)

    @Test fun validMimeKept() = assertEquals("image/png", FileNames.sanitizeMimeType(" Image/PNG "))
    @Test fun mimeWithPlusAndDotKept() = assertEquals("application/vnd.ms-excel", FileNames.sanitizeMimeType("application/vnd.ms-excel"))
    @Test fun invalidMimeReplaced() {
        assertEquals("application/octet-stream", FileNames.sanitizeMimeType(null))
        assertEquals("application/octet-stream", FileNames.sanitizeMimeType("text"))
        assertEquals("application/octet-stream", FileNames.sanitizeMimeType("text/plain; charset=x"))
        assertEquals("application/octet-stream", FileNames.sanitizeMimeType("a/b/c"))
        assertEquals("application/octet-stream", FileNames.sanitizeMimeType("a/b\nc"))
    }
}
