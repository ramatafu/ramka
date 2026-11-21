package com.ramka.network.proxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProxyAuthTest {
    @Test fun basicMatchesRfc7617Example() {
        assertEquals("Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==", ProxyAuth.basic("Aladdin", "open sesame"))
    }

    @Test fun utf8CredentialsAreEncodedAsUtf8() {
        assertEquals("Basic dGVzdDoxMjPCow==", ProxyAuth.basic("test", "123£"))
    }

    @Test fun headerOnlyForHttpProxyWithLogin() {
        val http = ProxySettings(true, ProxyType.HTTP, "p.example.org", 8080, "u", "p")
        assertEquals(ProxyAuth.basic("u", "p"), ProxyAuth.headerFor(http))
        assertNull(ProxyAuth.headerFor(http.copy(username = "", password = "")))
        assertNull(ProxyAuth.headerFor(http.copy(type = ProxyType.SOCKS5)))
    }
}
