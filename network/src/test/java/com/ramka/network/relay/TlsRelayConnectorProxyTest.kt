package com.ramka.network.relay

import com.ramka.domain.relay.RelayAddress
import com.ramka.domain.relay.RelayEndpoint
import com.ramka.domain.relay.RelayFailure
import com.ramka.network.proxy.ProxyManager
import com.ramka.network.proxy.ProxySettings
import com.ramka.network.proxy.ProxySettingsSource
import com.ramka.network.proxy.ProxyType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.DataInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicReference

/** Fail closed и маршрут через прокси: проверяется без настоящего релея и без TLS-сервера. */
class TlsRelayConnectorProxyTest {

    private val pin = "ab".repeat(32)
    private val servers = mutableListOf<ServerSocket>()

    @After fun tearDown() = servers.forEach { runCatching { it.close() } }

    private fun manager(settings: ProxySettings) = manager(MutableStateFlow(settings))

    private fun manager(state: MutableStateFlow<ProxySettings>) = ProxyManager(
        object : ProxySettingsSource { override val settings: Flow<ProxySettings> = state },
        CoroutineScope(Dispatchers.Unconfined)
    )

    private fun loopbackServer(): ServerSocket =
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).also { servers += it }

    private fun endpoint(host: String, port: Int) = RelayEndpoint(RelayAddress(host, port), "t".repeat(32), pin)

    private fun connectFailure(connector: TlsRelayConnector, endpoint: RelayEndpoint): RelayException {
        try {
            connector.connect(endpoint, 2_000).close()
        } catch (e: RelayException) {
            return e
        }
        fail("ожидали RelayException")
        throw IllegalStateException()
    }

    /** true, если на [server] за 300 мс никто не подключился. */
    private fun noDirectConnection(server: ServerSocket): Boolean {
        server.soTimeout = 300
        return try { server.accept().close(); false } catch (_: SocketTimeoutException) { true }
    }

    @Test fun misconfiguredProxyFailsClosedAndNeverGoesDirect() {
        val relay = loopbackServer()
        val connector = TlsRelayConnector(manager(ProxySettings(enabled = true, host = "", port = 1080)))
        val e = connectFailure(connector, endpoint("127.0.0.1", relay.localPort))
        assertEquals(RelayFailure.PROXY, e.failure)
        assertTrue(noDirectConnection(relay))
    }

    @Test fun unreachableProxyFailsClosedAndNeverGoesDirect() {
        val relay = loopbackServer()
        val deadPort = loopbackServer().also { it.close() }.localPort // порт, на котором никто не слушает
        val connector = TlsRelayConnector(manager(ProxySettings(true, ProxyType.SOCKS5, "127.0.0.1", deadPort)))
        val e = connectFailure(connector, endpoint("127.0.0.1", relay.localPort))
        assertEquals(RelayFailure.PROXY, e.failure)
        assertTrue(noDirectConnection(relay))
    }

    @Test fun relayNameGoesToProxyUnresolved() {
        val proxy = loopbackServer()
        val seen = AtomicReference<String?>(null)
        Thread {
            try {
                proxy.accept().use { s ->
                    s.soTimeout = 3_000
                    val i = DataInputStream(s.getInputStream()); val o = s.getOutputStream()
                    i.readFully(ByteArray(3)); o.write(byteArrayOf(5, 0)); o.flush()
                    val head = ByteArray(5).also { i.readFully(it) }                      // VER CMD RSV ATYP LEN
                    val name = ByteArray(head[4].toInt()).also { i.readFully(it) }
                    i.readFully(ByteArray(2))
                    seen.set("atyp=${head[3]} host=${String(name)}")
                    o.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0)); o.flush()        // успех, затем обрыв: TLS не состоится
                }
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true }.start()

        val connector = TlsRelayConnector(manager(ProxySettings(true, ProxyType.SOCKS5, "127.0.0.1", proxy.localPort)))
        // Имя не существует: если бы коннектор резолвил его сам, получили бы UNREACHABLE без обращения к прокси.
        val e = connectFailure(connector, endpoint("relay.no-such-host.invalid", 48766))
        assertTrue(e.failure == RelayFailure.TLS || e.failure == RelayFailure.UNREACHABLE)
        assertEquals("atyp=3 host=relay.no-such-host.invalid", seen.get())
    }

    @Test fun ipLiteralWithoutPinIsStillRefusedBeforeAnyNetwork() {
        val connector = TlsRelayConnector(manager(ProxySettings.DISABLED))
        val e = connectFailure(connector, RelayEndpoint(RelayAddress("127.0.0.1", 1), "t".repeat(32), null))
        assertEquals(RelayFailure.TLS, e.failure)
        assertNull(e.cause)
    }

    @Test fun routeChangeAbortsConnectionInProgressImmediately() {
        val proxy = loopbackServer()
        val accepted = CountDownLatch(1)
        Thread {
            try {
                proxy.accept().use { s ->
                    s.soTimeout = 5_000
                    DataInputStream(s.getInputStream()).readFully(ByteArray(3)) // приветствие SOCKS5
                    accepted.countDown()
                    Thread.sleep(4_000) // и молчим: рукопожатие «зависло»
                }
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true }.start()

        val state = MutableStateFlow(ProxySettings(true, ProxyType.SOCKS5, "127.0.0.1", proxy.localPort))
        val connector = TlsRelayConnector(manager(state))
        val failure = AtomicReference<Throwable?>(null)
        val finished = CountDownLatch(1)
        Thread {
            try {
                connector.connect(endpoint("relay.example.org", 443), 10_000).close()
            } catch (t: Throwable) {
                failure.set(t)
            }
            finished.countDown()
        }.apply { isDaemon = true }.start()

        assertTrue(accepted.await(3, TimeUnit.SECONDS))
        state.value = ProxySettings.DISABLED // маршрут сменился: сокет подключения по старому маршруту закрывается

        assertTrue("подключение должно оборваться сразу, а не по тайм-ауту", finished.await(2, TimeUnit.SECONDS))
        val e = failure.get() as RelayException
        assertEquals(RelayFailure.PROXY, e.failure)
    }
}
