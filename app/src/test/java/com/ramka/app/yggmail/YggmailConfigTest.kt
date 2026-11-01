package com.ramka.app.yggmail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YggmailConfigTest {
    private fun config(
        smtpHost: String = "200:db8::1", smtpPort: Int = 1025,
        imapHost: String = "200:db8::1", imapPort: Int = 1143,
        login: String = "abc@yggmail", password: String = "s3cret!"
    ) = YggmailConfig(smtpHost, smtpPort, imapHost, imapPort, login, password)

    @Test fun validConfigPasses() {
        assertNull(config().validate())
        assertTrue(config().isValid())
    }

    @Test fun eachFieldIsValidated() {
        assertNotNull(config(smtpHost = "").validate())
        assertNotNull(config(smtpPort = 0).validate())
        assertNotNull(config(smtpPort = 70000).validate())
        assertNotNull(config(imapHost = "bad host").validate())
        assertNotNull(config(imapPort = -1).validate())
        assertNotNull(config(login = " ").validate())
        assertNotNull(config(login = "a b").validate())
        assertNotNull(config(password = "").validate())
        assertFalse(config(password = "").isValid())
    }

    @Test fun codecRoundTrip() {
        val original = config(smtpHost = "[200:db8::1]", login = "логин@yggmail", password = "пароль 123")
        assertEquals(original, YggmailConfigCodec.decode(YggmailConfigCodec.encode(original)))
    }

    @Test fun corruptedDataDecodesToNull() {
        assertNull(YggmailConfigCodec.decode(ByteArray(0)))
        assertNull(YggmailConfigCodec.decode(byteArrayOf(9, 1, 2, 3)))
        val bytes = YggmailConfigCodec.encode(config())
        assertNull(YggmailConfigCodec.decode(bytes.copyOf(bytes.size - 3)))
    }

    @Test fun toStringNeverLeaksCredentials() {
        val text = config(login = "topsecret-login@yggmail", password = "hunter2-password").toString()
        assertFalse(text.contains("hunter2"))
        assertFalse(text.contains("topsecret"))
        assertFalse(text.contains("200:db8"))
    }
}
