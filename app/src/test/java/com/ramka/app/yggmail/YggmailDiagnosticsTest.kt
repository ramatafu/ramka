package com.ramka.app.yggmail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YggmailDiagnosticsTest {
    @Test fun passwordIsMaskedEverywhere() {
        val line = YggmailDiagnostics.redact("C: A1 LOGIN me qwerty and qwerty again", listOf("qwerty"))
        assertFalse(line.contains("qwerty"))
        assertTrue(line.contains("***"))
    }

    @Test fun longTokensInAuthLinesAreHidden() {
        val token = "AGFiY2RlZmdoaWprbG1ub3BxcnN0dXZ3eHl6"
        val line = YggmailDiagnostics.redact("C: AUTH PLAIN $token", emptyList())
        assertFalse(line.contains(token))
    }

    @Test fun ordinaryServerLinesAreKept() {
        val raw = "S: * OK IMAP4rev1 Service Ready"
        assertEquals(raw, YggmailDiagnostics.redact(raw, listOf("qwerty")))
    }

    @Test fun longHexLoginOutsideAuthLinesIsKeptForDiagnostics() {
        val raw = "DEBUG: user 2e0309897eb2c09bafa5ce22be28de330a06ec2094e924c239edae0b8d44906f@yggmail"
        assertEquals(raw, YggmailDiagnostics.redact(raw, listOf("qwerty")))
    }

    @Test fun bufferIsCappedAndClearable() {
        YggmailDiagnostics.clear()
        repeat(500) { YggmailDiagnostics.note("line $it") }
        val lines = YggmailDiagnostics.lines()
        assertEquals(120, lines.size)
        assertEquals("line 499", lines.last())
        YggmailDiagnostics.clear()
        assertTrue(YggmailDiagnostics.lines().isEmpty())
    }

    @Test fun debugLinesArePasswordFreeInBuffer() {
        YggmailDiagnostics.clear()
        YggmailDiagnostics.addDebug("DEBUG SMTP: sending password hunter2", listOf("hunter2"))
        assertTrue(YggmailDiagnostics.lines().none { it.contains("hunter2") })
    }
}
