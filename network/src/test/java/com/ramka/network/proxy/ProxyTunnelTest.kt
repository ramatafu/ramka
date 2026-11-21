package com.ramka.network.proxy

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.DataInputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference

/** Рукопожатие проверяется на настоящем сокете: поддельный прокси на loopback читает байты и отвечает по протоколу. */
class ProxyTunnelTest {

    private val servers = mutableListOf<ServerSocket>()
    private val serverError = AtomicReference<Throwable?>(null)

    @After fun tearDown() {
        servers.forEach { runCatching { it.close() } }
        serverError.get()?.let { throw AssertionError("Ошибка в поддельном прокси", it) }
    }

    /** Поднимает прокси, который обслуживает одно соединение через [handler]. */
    private fun fakeProxy(handler: (Socket) -> Unit): Int {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        servers += server
        Thread {
            try {
                server.accept().use { s ->
                    s.soTimeout = 5_000
                    handler(s)
                }
            } catch (e: Throwable) {
                if (!server.isClosed) serverError.set(e)
            }
        }.apply { isDaemon = true }.start()
        return server.localPort
    }

    private fun settings(port: Int, type: ProxyType = ProxyType.SOCKS5, user: String = "", pass: String = "") =
        ProxySettings(true, type, "127.0.0.1", port, user, pass)

    private fun DataInputStream.bytes(n: Int) = ByteArray(n).also { readFully(it) }

    private fun open(port: Int, host: String = "relay.example.org", targetPort: Int = 443,
                     type: ProxyType = ProxyType.SOCKS5, user: String = "", pass: String = ""): Socket =
        ProxyTunnel.open(settings(port, type, user, pass), host, targetPort, 5_000)

    private fun expectHandshakeFailure(block: () -> Unit): ProxyHandshakeException {
        try {
            block()
        } catch (e: ProxyHandshakeException) {
            return e
        }
        fail("ожидали ProxyHandshakeException")
        throw IllegalStateException()
    }

    // ---- SOCKS5 -----------------------------------------------------------------------------

    @Test fun socks5NoAuthSendsDomainWithoutLocalDns() {
        val seen = AtomicReference<ByteArray>()
        val port = fakeProxy { s ->
            val i = DataInputStream(s.getInputStream()); val o = s.getOutputStream()
            assertArrayEquals(byteArrayOf(5, 1, 0), i.bytes(3))                  // только «без аутентификации»
            o.write(byteArrayOf(5, 0)); o.flush()
            val host = "no-such-host.invalid"
            seen.set(i.bytes(4 + 1 + host.length + 2))
            o.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0)); o.write('X'.code); o.flush()
        }
        val tunnel = open(port, host = "no-such-host.invalid", targetPort = 443)
        tunnel.use {
            assertEquals('X'.code, it.getInputStream().read()) // привязанный адрес прочитан целиком, данные не съедены
        }
        val req = seen.get()
        assertArrayEquals(byteArrayOf(5, 1, 0, 3, 20), req.copyOfRange(0, 5)) // CONNECT, ATYP=DOMAIN, длина 20
        assertEquals("no-such-host.invalid", String(req, 5, 20, Charsets.US_ASCII))
        assertEquals(443, ((req[25].toInt() and 0xFF) shl 8) or (req[26].toInt() and 0xFF))
    }

    @Test fun socks5UserPasswordHandshake() {
        val auth = AtomicReference<ByteArray>()
        val port = fakeProxy { s ->
            val i = DataInputStream(s.getInputStream()); val o = s.getOutputStream()
            assertArrayEquals(byteArrayOf(5, 2, 0, 2), i.bytes(4))              // оба способа
            o.write(byteArrayOf(5, 2)); o.flush()
            auth.set(i.bytes(1 + 1 + 4 + 1 + 6))                                 // ver, ulen, "user", plen, "secret"
            o.write(byteArrayOf(1, 0)); o.flush()
            i.bytes(4 + 1 + "relay.example.org".length + 2)
            o.write(byteArrayOf(5, 0, 0, 3, 3, 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), 0, 80)) // привязка-домен
            o.write('Y'.code); o.flush()
        }
        open(port, user = "user", pass = "secret").use { assertEquals('Y'.code, it.getInputStream().read()) }
        val a = auth.get()
        assertEquals(1, a[0].toInt()); assertEquals(4, a[1].toInt())
        assertEquals("user", String(a, 2, 4)); assertEquals(6, a[6].toInt()); assertEquals("secret", String(a, 7, 6))
    }

    @Test fun socks5WrongPasswordFails() {
        val port = fakeProxy { s ->
            val i = DataInputStream(s.getInputStream()); val o = s.getOutputStream()
            i.bytes(4); o.write(byteArrayOf(5, 2)); o.flush()
            i.bytes(1 + 1 + 1 + 1 + 1); o.write(byteArrayOf(1, 1)); o.flush()   // статус 1 = отказ
        }
        val e = expectHandshakeFailure { open(port, user = "u", pass = "p") }
        assertTrue(e.message!!.contains("логин"))
    }

    @Test fun socks5ServerDemandsAuthButNoneConfigured() {
        val port = fakeProxy { s ->
            val i = DataInputStream(s.getInputStream()); val o = s.getOutputStream()
            i.bytes(3); o.write(byteArrayOf(5, 2)); o.flush()
        }
        expectHandshakeFailure { open(port) }
    }

    @Test fun socks5NoAcceptableMethod() {
        val port = fakeProxy { s ->
            val i = DataInputStream(s.getInputStream()); val o = s.getOutputStream()
            i.bytes(3); o.write(byteArrayOf(5, 0xFF.toByte())); o.flush()
        }
        expectHandshakeFailure { open(port) }
    }

    @Test fun socks5ConnectionRefusedByTarget() {
        val port = fakeProxy { s ->
            val i = DataInputStream(s.getInputStream()); val o = s.getOutputStream()
            i.bytes(3); o.write(byteArrayOf(5, 0)); o.flush()
            i.bytes(4 + 1 + "relay.example.org".length + 2)
            o.write(byteArrayOf(5, 5, 0, 1, 0, 0, 0, 0, 0, 0)); o.flush()
        }
        val e = expectHandshakeFailure { open(port) }
        assertTrue(e.message!!.contains("отклонено"))
    }

    @Test fun socks5Ipv4AndIpv6LiteralsUseAddressTypes() {
        val v4 = AtomicReference<ByteArray>()
        val p4 = fakeProxy { s ->
            val i = DataInputStream(s.getInputStream()); val o = s.getOutputStream()
            i.bytes(3); o.write(byteArrayOf(5, 0)); o.flush()
            v4.set(i.bytes(4 + 4 + 2)); o.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0)); o.flush()
        }
        open(p4, host = "192.0.2.10", targetPort = 48766).close()
        assertArrayEquals(byteArrayOf(5, 1, 0, 1, 192.toByte(), 0, 2, 10), v4.get().copyOfRange(0, 8))

        val v6 = AtomicReference<ByteArray>()
        val p6 = fakeProxy { s ->
            val i = DataInputStream(s.getInputStream()); val o = s.getOutputStream()
            i.bytes(3); o.write(byteArrayOf(5, 0)); o.flush()
            v6.set(i.bytes(4 + 16 + 2)); o.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0)); o.flush()
        }
        open(p6, host = "[2001:db8::1]", targetPort = 443).close()
        val r = v6.get()
        assertEquals(4, r[3].toInt())                                            // ATYP=IPv6
        assertEquals(0x20, r[4].toInt()); assertEquals(0x01, r[5].toInt()); assertEquals(1, r[19].toInt())
    }

    @Test fun nonSocksServerIsRejected() {
        val port = fakeProxy { s ->
            DataInputStream(s.getInputStream()).bytes(3)
            s.getOutputStream().write("HTTP/1.1 400 Bad Request\r\n\r\n".toByteArray()); s.getOutputStream().flush()
        }
        expectHandshakeFailure { open(port) }
    }

    // ---- HTTP CONNECT -----------------------------------------------------------------------

    private fun readHead(input: InputStream): String {
        val sb = StringBuilder()
        while (!sb.endsWith("\r\n\r\n")) sb.append(input.read().toChar())
        return sb.toString()
    }

    @Test fun httpConnectSendsRequestAndAuthorization() {
        val head = AtomicReference<String>()
        val port = fakeProxy { s ->
            head.set(readHead(s.getInputStream()))
            s.getOutputStream().write("HTTP/1.1 200 Connection established\r\nProxy-Agent: t\r\n\r\nZ".toByteArray())
            s.getOutputStream().flush()
        }
        open(port, type = ProxyType.HTTP, user = "Aladdin", pass = "open sesame").use {
            assertEquals('Z'.code, it.getInputStream().read()) // за заголовками не прочитано лишнего
        }
        val lines = head.get().split("\r\n")
        assertEquals("CONNECT relay.example.org:443 HTTP/1.1", lines[0])
        assertEquals("Host: relay.example.org:443", lines[1])
        assertTrue(lines.contains("Proxy-Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ=="))
    }

    @Test fun httpConnectWithoutCredentialsSendsNoAuthorization() {
        val head = AtomicReference<String>()
        val port = fakeProxy { s ->
            head.set(readHead(s.getInputStream()))
            s.getOutputStream().write("HTTP/1.0 200 OK\r\n\r\n".toByteArray()); s.getOutputStream().flush() // HTTP/1.0 тоже годится
        }
        open(port, type = ProxyType.HTTP).close()
        assertFalse(head.get().contains("Proxy-Authorization"))
    }

    @Test fun httpConnectIpv6IsBracketed() {
        val head = AtomicReference<String>()
        val port = fakeProxy { s ->
            head.set(readHead(s.getInputStream()))
            s.getOutputStream().write("HTTP/1.1 200 OK\r\n\r\n".toByteArray()); s.getOutputStream().flush()
        }
        open(port, host = "::1", targetPort = 443, type = ProxyType.HTTP).close()
        assertTrue(head.get().startsWith("CONNECT [::1]:443 HTTP/1.1\r\nHost: [::1]:443\r\n"))
    }

    @Test fun http407IsReportedWithoutLeakingCredentials() {
        val port = fakeProxy { s ->
            readHead(s.getInputStream())
            s.getOutputStream().write("HTTP/1.1 407 Proxy Authentication Required\r\n\r\n".toByteArray()); s.getOutputStream().flush()
        }
        val e = expectHandshakeFailure { open(port, type = ProxyType.HTTP, user = "u", pass = "topsecret") }
        assertTrue(e.message!!.contains("407"))
        assertFalse(e.message!!.contains("topsecret"))
    }

    @Test fun httpOtherStatusIsRejected() {
        val port = fakeProxy { s ->
            readHead(s.getInputStream())
            s.getOutputStream().write("HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray()); s.getOutputStream().flush()
        }
        val e = expectHandshakeFailure { open(port, type = ProxyType.HTTP) }
        assertTrue(e.message!!.contains("502"))
    }

    @Test fun garbageInsteadOfHttpIsRejected() {
        val port = fakeProxy { s ->
            readHead(s.getInputStream())
            s.getOutputStream().write("\u0005\u0000garbage\r\n\r\n".toByteArray()); s.getOutputStream().flush()
        }
        expectHandshakeFailure { open(port, type = ProxyType.HTTP) }
    }

    // ---- Общее ------------------------------------------------------------------------------

    @Test fun invalidSettingsFailClosed() {
        try {
            ProxyTunnel.open(ProxySettings(enabled = true, host = "", port = 1080), "relay.example.org", 443, 1_000)
            fail("ожидали ProxyMisconfiguredException")
        } catch (_: ProxyMisconfiguredException) {
        }
    }

    @Test fun silentProxyTimesOut() {
        val port = fakeProxy { s -> Thread.sleep(3_000); s.close() } // принимает и молчит
        val started = System.nanoTime()
        try {
            ProxyTunnel.open(settings(port), "relay.example.org", 443, 300)
            fail("ожидали тайм-аут")
        } catch (_: java.net.SocketTimeoutException) {
        }
        assertTrue((System.nanoTime() - started) / 1_000_000L < 2_000)
    }

    @Test fun targetHostWithControlCharactersIsRejected() {
        expectHandshakeFailure { ProxyTunnel.open(settings(1, ProxyType.HTTP), "relay.example.org\r\nX: y", 443, 1_000) }
    }

    @Test fun classifyRecognisesAddressKinds() {
        assertTrue(ProxyTunnel.classify("203.0.113.5") is ProxyTunnel.Target.V4)
        assertTrue(ProxyTunnel.classify("2001:db8::2") is ProxyTunnel.Target.V6)
        assertTrue(ProxyTunnel.classify("relay.example.org") is ProxyTunnel.Target.Domain)
        assertTrue(ProxyTunnel.classify("999.1.1.1") is ProxyTunnel.Target.Domain) // не IPv4 → имя, в DNS локально не идём
    }
}
