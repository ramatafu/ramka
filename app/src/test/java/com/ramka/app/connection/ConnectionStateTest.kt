package com.ramka.app.connection

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionStateTest {

    @Test 
    fun `no network is offline even with call and relay`() {
        assertEquals(ConnectionState.OFFLINE, resolveConnectionState(true, NetworkKind.NONE, true, false, false))
        assertEquals(ConnectionState.OFFLINE, resolveConnectionState(false, NetworkKind.NONE, true, false, true))
    }

    @Test 
    fun `connected call is TURN on any network`() {
        assertEquals(ConnectionState.TURN, resolveConnectionState(true, NetworkKind.LOCAL, true, false, false))
        assertEquals(ConnectionState.TURN, resolveConnectionState(true, NetworkKind.OTHER, false, false, true))
    }

    @Test 
    fun `local network without call is P2P even if relay is connected`() {
        assertEquals(ConnectionState.P2P, resolveConnectionState(false, NetworkKind.LOCAL, true, true, false))
        assertEquals(ConnectionState.P2P, resolveConnectionState(false, NetworkKind.LOCAL, false, true, true))
    }

    @Test 
    fun `mobile network with relay is RELAY (priority over TURN), without relay is TURN if ready, else offline`() {
        // Relay активен → должен быть RELAY (синий), даже если TURN готов
        assertEquals(ConnectionState.RELAY, resolveConnectionState(false, NetworkKind.OTHER, true, false, true))
        
        // Relay не активен, но TURN готов → должен быть TURN (жёлтый)
        assertEquals(ConnectionState.TURN, resolveConnectionState(false, NetworkKind.OTHER, false, false, true))
        
        // Ничего не активно → OFFLINE (серый)
        assertEquals(ConnectionState.OFFLINE, resolveConnectionState(false, NetworkKind.OTHER, false, false, false))
    }

    @Test
    fun `relay with proxy enabled is RELAY_PROXY, without proxy stays RELAY`() {
        assertEquals(ConnectionState.RELAY_PROXY,
            resolveConnectionState(false, NetworkKind.OTHER, true, false, false, proxyEnabled = true))
        // TURN готов, но релей приоритетнее, как и раньше
        assertEquals(ConnectionState.RELAY_PROXY,
            resolveConnectionState(false, NetworkKind.OTHER, true, false, true, proxyEnabled = true))
        assertEquals(ConnectionState.RELAY,
            resolveConnectionState(false, NetworkKind.OTHER, true, false, false, proxyEnabled = false))
    }

    @Test
    fun `proxy flag does not change priorities of other states`() {
        // нет сети
        assertEquals(ConnectionState.OFFLINE, resolveConnectionState(false, NetworkKind.NONE, true, false, false, proxyEnabled = true))
        // звонок
        assertEquals(ConnectionState.TURN, resolveConnectionState(true, NetworkKind.OTHER, true, false, false, proxyEnabled = true))
        // LAN-пир
        assertEquals(ConnectionState.P2P, resolveConnectionState(false, NetworkKind.LOCAL, true, true, false, proxyEnabled = true))
        // Yggdrasil
        assertEquals(ConnectionState.YGGDRASIL, resolveConnectionState(false, NetworkKind.OTHER, true, false, false, yggdrasilActive = true, proxyEnabled = true))
        // прокси включён, но релей не подключён (fail-closed): TURN или офлайн, не маджента
        assertEquals(ConnectionState.TURN, resolveConnectionState(false, NetworkKind.OTHER, false, false, true, proxyEnabled = true))
        assertEquals(ConnectionState.OFFLINE, resolveConnectionState(false, NetworkKind.OTHER, false, false, false, proxyEnabled = true))
    }

    @Test
    fun `RELAY_PROXY is magenta FF6EC7 and other colors are unchanged`() {
        assertEquals(Color(0xFFFF6EC7), ConnectionState.RELAY_PROXY.indicatorColor())
        assertEquals(Color(0xFF00BFFF), ConnectionState.RELAY.indicatorColor())
        assertEquals(Color(0xFF00FF41), ConnectionState.P2P.indicatorColor())
        assertEquals(Color(0xFFB36BFF), ConnectionState.YGGDRASIL.indicatorColor())
        assertEquals(Color(0xFFFFD700), ConnectionState.TURN.indicatorColor())
        assertEquals(Color(0xFF808080), ConnectionState.OFFLINE.indicatorColor())
    }

    @Test
    fun `only RELAY_PROXY has an accessibility label`() {
        assertEquals("Подключено через прокси", ConnectionState.RELAY_PROXY.contentDescriptionOrNull())
        ConnectionState.values().filter { it != ConnectionState.RELAY_PROXY }.forEach { assertNull(it.contentDescriptionOrNull()) }
    }

    // ---- TURN: «неизвестно» и «недоступен» не дают жёлтого; приоритеты ----

    @Test
    fun `TURN not checked yet or unreachable is OFFLINE not yellow`() {
        // turnReady=false — и «ещё не проверен», и «проверен, недоступен»: оба состояния серые
        assertEquals(ConnectionState.OFFLINE, resolveConnectionState(false, NetworkKind.OTHER, false, false, false))
        assertEquals(ConnectionState.OFFLINE, LinkSnapshot(NetworkKind.OTHER, false, false).let {
            resolveConnectionState(false, it.network, it.relayConnected, false, it.turnReady)
        })
        // начальный снимок индикатора: ничего не подключено и TURN неизвестен
        assertEquals(false, LinkSnapshot(NetworkKind.NONE, false, false, false).turnReady)
    }

    @Test
    fun `TURN available and relay dead is yellow`() {
        assertEquals(ConnectionState.TURN, resolveConnectionState(false, NetworkKind.OTHER, false, false, true))
    }

    @Test
    fun `relay connected beats live TURN and keeps proxy colour`() {
        assertEquals(ConnectionState.RELAY, resolveConnectionState(false, NetworkKind.OTHER, true, false, true))
        assertEquals(ConnectionState.RELAY_PROXY, resolveConnectionState(false, NetworkKind.OTHER, true, false, true, proxyEnabled = true))
    }

    @Test
    fun `TURN going down turns the logo grey or relay colour`() {
        val before = resolveConnectionState(false, NetworkKind.OTHER, false, false, true)
        val afterNoRelay = resolveConnectionState(false, NetworkKind.OTHER, false, false, false)
        val afterWithRelay = resolveConnectionState(false, NetworkKind.OTHER, true, false, false)
        assertEquals(ConnectionState.TURN, before)
        assertEquals(ConnectionState.OFFLINE, afterNoRelay)
        assertEquals(ConnectionState.RELAY, afterWithRelay)
    }

    @Test
    fun `LAN peer beats TURN and relay`() {
        assertEquals(ConnectionState.P2P, resolveConnectionState(false, NetworkKind.LOCAL, true, true, true))
        assertEquals(ConnectionState.P2P, resolveConnectionState(false, NetworkKind.LOCAL, false, true, true))
    }

    @Test
    fun `no network is OFFLINE even with TURN reported ready`() {
        assertEquals(ConnectionState.OFFLINE, resolveConnectionState(false, NetworkKind.NONE, true, false, true))
    }
}