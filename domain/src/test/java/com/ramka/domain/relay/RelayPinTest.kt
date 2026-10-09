package com.ramka.domain.relay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelayPinTest {

    private val pin = "ab".repeat(32)

    @Test
    fun `пин в нижнем регистре остаётся как есть`() {
        assertEquals(pin, RelayPin.normalize(pin))
    }

    @Test
    fun `верхний регистр приводится к нижнему`() {
        assertEquals(pin, RelayPin.normalize(pin.uppercase()))
    }

    @Test
    fun `формат openssl с двоеточиями и пробелами по краям`() {
        assertEquals(pin, RelayPin.normalize(" " + pin.chunked(2).joinToString(":").uppercase() + "\n"))
    }

    @Test
    fun `пробелы внутри пина допускаются, как при копировании из терминала`() {
        assertEquals(pin, RelayPin.normalize(pin.chunked(8).joinToString(" ")))
    }

    @Test
    fun `неверная длина и не hex-символы отклоняются`() {
        assertNull(RelayPin.normalize(""))
        assertNull(RelayPin.normalize("abc"))
        assertNull(RelayPin.normalize(pin + "ab"))
        assertNull(RelayPin.normalize(pin.dropLast(2)))
        assertNull(RelayPin.normalize("zz".repeat(32)))
        assertNull(RelayPin.normalize("не пин".repeat(11)))
    }
}