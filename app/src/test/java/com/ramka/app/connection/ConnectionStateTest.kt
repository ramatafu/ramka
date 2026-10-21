package com.ramka.app.connection

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionStateTest {

    @Test fun `no network is offline even with call and relay`() {
        assertEquals(ConnectionState.OFFLINE, resolveConnectionState(true, NetworkKind.NONE, true))
    }

    @Test fun `connected call is TURN on any network`() {
        assertEquals(ConnectionState.TURN, resolveConnectionState(true, NetworkKind.LOCAL, true))
        assertEquals(ConnectionState.TURN, resolveConnectionState(true, NetworkKind.OTHER, false))
    }

    @Test fun `local network without call is P2P even if relay is connected`() {
        assertEquals(ConnectionState.P2P, resolveConnectionState(false, NetworkKind.LOCAL, true))
        assertEquals(ConnectionState.P2P, resolveConnectionState(false, NetworkKind.LOCAL, false))
    }

    @Test fun `mobile network with relay is RELAY, without relay is offline`() {
        assertEquals(ConnectionState.RELAY, resolveConnectionState(false, NetworkKind.OTHER, true))
        assertEquals(ConnectionState.OFFLINE, resolveConnectionState(false, NetworkKind.OTHER, false))
    }
}
