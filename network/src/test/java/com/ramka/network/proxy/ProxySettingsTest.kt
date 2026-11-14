package com.ramka.network.proxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxySettingsTest {
    private fun settings(
        host: String = "proxy.example.org", port: Int = 1080, username: String = "", password: String = "",
        enabled: Boolean = true, type: ProxyType = ProxyType.SOCKS5
    ) = ProxySettings(enabled, type, host, port, username, password)

    @Test fun defaultsMatchTheScreen() {
        val d = ProxySettings()
        assertFalse(d.enabled)
        assertEquals(ProxyType.SOCKS5, d.type)
        assertEquals(1080, d.port)
        assertEquals(ProxySettings.DISABLED, d)
    }

    @Test fun validSettingsPass() {
        assertNull(settings().validate())
        assertNull(settings(host = "192.168.1.5").validate())
        assertNull(settings(host = "2001:db8::1").validate())
        assertNull(settings(host = "[2001:db8::1]").validate())
        assertNull(settings(username = "user", password = "pw").validate())
        assertNull(settings(username = "user").validate()) // пароль необязателен
        assertTrue(settings().isUsable)
    }

    @Test fun badHostsAreRejected() {
        for (host in listOf("", "  ", "http://proxy.example.org", "proxy example.org", "proxy.example.org/path", "proxy_name", "-bad.example.org", "host:1080")) {
            assertNotNull("«$host»", settings(host = host).validate())
        }
    }

    @Test fun portRange() {
        assertNotNull(settings(port = 0).validate())
        assertNotNull(settings(port = 65536).validate())
        assertNotNull(settings(port = -1).validate())
        assertNull(settings(port = 1).validate())
        assertNull(settings(port = 65535).validate())
    }

    @Test fun passwordWithoutLoginIsRejected() {
        assertNotNull(settings(username = "", password = "pw").validate())
    }

    @Test fun credentialLimitIsInBytesNotCharacters() {
        assertNull(settings(username = "a".repeat(255)).validate())
        assertNotNull(settings(username = "a".repeat(256)).validate())
        assertNotNull(settings(username = "я".repeat(128)).validate()) // 256 байт
    }

    @Test fun disabledIsNeverUsableEvenWhenValid() {
        assertFalse(settings(enabled = false).isUsable)
    }

    @Test fun toStringNeverLeaksSecretsOrAddress() {
        val text = settings(host = "secret-proxy.example.org", username = "topsecret-user", password = "hunter2").toString()
        assertFalse(text.contains("hunter2"))
        assertFalse(text.contains("topsecret"))
        assertFalse(text.contains("secret-proxy"))
    }

    @Test fun stripBrackets() {
        assertEquals("::1", ProxySettings.stripBrackets("[::1]"))
        assertEquals("host", ProxySettings.stripBrackets("host"))
        assertEquals("[]", ProxySettings.stripBrackets("[]"))
    }
}
