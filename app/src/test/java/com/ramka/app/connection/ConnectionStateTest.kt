package com.ramka.app.connection

import org.junit.Assert.assertEquals
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
}