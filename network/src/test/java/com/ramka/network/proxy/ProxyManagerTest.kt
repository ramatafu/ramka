package com.ramka.network.proxy

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy

class ProxyManagerTest {
    private class FakeSource(initial: ProxySettings) : ProxySettingsSource {
        val state = MutableStateFlow(initial)
        override val settings: Flow<ProxySettings> = state
    }

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun enabled(type: ProxyType = ProxyType.SOCKS5, host: String = "proxy.example.org", port: Int = 1080) =
        ProxySettings(true, type, host, port)

    @Test fun disabledMeansDirect() {
        assertNull(ProxyManager(FakeSource(ProxySettings.DISABLED), scope).createProxy())
    }

    @Test fun socks5BecomesSocksProxy() {
        val proxy = ProxyManager(FakeSource(enabled(ProxyType.SOCKS5, port = 9050)), scope).createProxy()!!
        assertEquals(Proxy.Type.SOCKS, proxy.type())
        val address = proxy.address() as InetSocketAddress
        assertEquals("proxy.example.org", address.hostString)
        assertEquals(9050, address.port)
    }

    @Test fun httpBecomesHttpProxy() {
        val proxy = ProxyManager(FakeSource(enabled(ProxyType.HTTP, port = 8080)), scope).createProxy()!!
        assertEquals(Proxy.Type.HTTP, proxy.type())
        assertEquals(8080, (proxy.address() as InetSocketAddress).port)
    }

    @Test fun proxyAddressIsNotResolvedHere() {
        val proxy = ProxyManager(FakeSource(enabled(host = "no-such-host.invalid")), scope).createProxy()!!
        assertTrue((proxy.address() as InetSocketAddress).isUnresolved) // имя разрешается при подключении, без DNS здесь
    }

    @Test fun bracketedIpv6HostIsAccepted() {
        val proxy = ProxyManager(FakeSource(enabled(host = "[2001:db8::1]")), scope).createProxy()!!
        assertEquals("2001:db8::1", (proxy.address() as InetSocketAddress).hostString)
    }

    @Test fun enabledButInvalidFailsClosedInsteadOfGoingDirect() {
        val manager = ProxyManager(FakeSource(enabled(host = "")), scope)
        try {
            manager.createProxy()
            fail("должно быть исключение: прямое соединение недопустимо")
        } catch (e: ProxyMisconfiguredException) {
            assertTrue(e is IOException) // сетевой код воспринимает это как обычный сбой соединения
        }
    }

    @Test fun settingsChangesApplyToTheNextCall() {
        val source = FakeSource(ProxySettings.DISABLED)
        val manager = ProxyManager(source, scope)
        assertNull(manager.createProxy())
        source.state.value = enabled(port = 1081)
        assertEquals(1081, (manager.createProxy()!!.address() as InetSocketAddress).port)
        source.state.value = ProxySettings.DISABLED
        assertNull(manager.createProxy())
    }

    @Test fun notLoadedYetFailsClosed() {
        val silent = object : ProxySettingsSource { override val settings: Flow<ProxySettings> = MutableSharedFlow() }
        val manager = ProxyManager(silent, scope)
        try {
            manager.createProxy()
            fail("до загрузки настроек решать нельзя")
        } catch (e: ProxyNotReadyException) {
            // ожидаемо
        }
        assertEquals(ProxySettings.DISABLED, manager.settings())
    }

    @Test fun awaitProxyWaitsForFirstLoad() = runBlocking {
        val flow = MutableSharedFlow<ProxySettings>()
        val manager = ProxyManager(object : ProxySettingsSource { override val settings: Flow<ProxySettings> = flow }, scope)
        var result: Proxy? = null
        var done = false
        val job = launch(Dispatchers.Unconfined) {
            result = manager.awaitProxy()
            done = true
        }
        assertTrue(!done) // настроек ещё нет — ждём
        flow.emit(enabled(port = 1234))
        job.join()
        assertTrue(done)
        assertEquals(1234, (result!!.address() as InetSocketAddress).port)
    }
}
