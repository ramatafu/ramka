package com.ramka.app.connection

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionStatusHelpTest {

    @Test
    fun `help lists every connection state exactly once`() {
        val states = connectionStatusHelpItems.map { it.state }
        assertEquals(ConnectionState.values().toSet(), states.toSet())
        assertEquals(states.size, states.toSet().size)
    }

    @Test
    fun `every state has its own colour so the dots in the help are distinguishable`() {
        val colours = connectionStatusHelpItems.map { it.state.indicatorColor() }
        assertEquals(colours.size, colours.toSet().size)
    }

    @Test
    fun `proxy row is magenta FF6EC7 like the indicator`() {
        val proxy = connectionStatusHelpItems.single { it.state == ConnectionState.RELAY_PROXY }
        assertEquals(Color(0xFFFF6EC7), proxy.state.indicatorColor())
        assertEquals("Маджента (#FF6EC7) — Через прокси", proxy.title)
    }

    @Test
    fun `texts match the agreed wording`() {
        val byState = connectionStatusHelpItems.associateBy { it.state }
        assertEquals("Прямое P2P-соединение с собеседником в той же сети.", byState.getValue(ConnectionState.P2P).description)
        assertEquals("Соединение с релеем через прокси-сервер. IP скрыт.", byState.getValue(ConnectionState.RELAY_PROXY).description)
        assertEquals("Прямое зашифрованное соединение через сервер-релей.", byState.getValue(ConnectionState.RELAY).description)
        assertEquals("Соединение через mesh-сеть или резервный почтовый канал.", byState.getValue(ConnectionState.YGGDRASIL).description)
        assertEquals("Релей недоступен, доступны только голосовые вызовы.", byState.getValue(ConnectionState.TURN).description)
        assertEquals("Нет активного соединения.", byState.getValue(ConnectionState.OFFLINE).description)
    }
}
