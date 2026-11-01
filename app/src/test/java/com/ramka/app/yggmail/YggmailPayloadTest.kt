package com.ramka.app.yggmail

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YggmailPayloadTest {
    @Test fun subjectIsExactlyRamkaE2ee() {
        assertEquals("Ramka-E2EE", YggmailPayload.SUBJECT)
    }

    @Test fun roundTrip() {
        val bytes = ByteArray(1000) { (it * 7).toByte() }
        assertArrayEquals(bytes, YggmailPayload.decode(YggmailPayload.encode(bytes)))
    }

    @Test fun encodedLinesAreShort() {
        val encoded = YggmailPayload.encode(ByteArray(5000) { it.toByte() })
        assertTrue(encoded.lines().all { it.length <= 76 })
    }

    @Test fun decoderToleratesLineBreaksAndSurroundingWhitespace() {
        val bytes = ByteArray(300) { it.toByte() }
        val wrapped = "\r\n  " + YggmailPayload.encode(bytes) + "\r\n\r\n"
        assertArrayEquals(bytes, YggmailPayload.decode(wrapped))
    }

    @Test fun nonBase64BodyIsRejected() {
        assertNull(YggmailPayload.decode(""))
        assertNull(YggmailPayload.decode("   "))
        assertNull(YggmailPayload.decode("Привет, это обычное письмо"))
        assertNull(YggmailPayload.decode("<html>hello</html>"))
        assertNull(YggmailPayload.decode("abc!def"))
    }

    @Test fun oversizedBodyIsRejected() {
        assertNull(YggmailPayload.decode("A".repeat(YggmailPayload.MAX_BODY_CHARS + 4)))
        val tooBig = YggmailPayload.encode(ByteArray(YggmailPayload.MAX_SEALED_BYTES + 1))
        assertNull(YggmailPayload.decode(tooBig))
    }

    @Test fun maxSizedPayloadIsAccepted() {
        val bytes = ByteArray(YggmailPayload.MAX_SEALED_BYTES) { 1 }
        assertArrayEquals(bytes, YggmailPayload.decode(YggmailPayload.encode(bytes)))
    }
}
