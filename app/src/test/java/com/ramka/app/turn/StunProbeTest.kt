package com.ramka.app.turn

import com.ramka.app.data.TurnUrl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket

class StunProbeTest {

    private val tx = ByteArray(12) { (it + 1).toByte() }

    private fun response(type: Int, cookieOk: Boolean = true, id: ByteArray = tx): ByteArray {
        val req = StunProbe.bindingRequest(id)
        req[0] = (type shr 8).toByte(); req[1] = type.toByte()
        if (!cookieOk) req[4] = 0
        return req
    }

    @Test fun requestLayout() {
        val r = StunProbe.bindingRequest(tx)
        assertEquals(20, r.size)
        assertArrayEquals(byteArrayOf(0x00, 0x01, 0x00, 0x00, 0x21, 0x12, 0xA4.toByte(), 0x42), r.copyOfRange(0, 8))
        assertArrayEquals(tx, r.copyOfRange(8, 20))
    }

    @Test fun acceptsSuccessAndErrorResponses() {
        assertTrue(StunProbe.isBindingResponse(response(0x0101), 20, tx))
        assertTrue(StunProbe.isBindingResponse(response(0x0111), 20, tx))
    }

    @Test fun rejectsWrongTypeCookieTransactionOrLength() {
        assertFalse(StunProbe.isBindingResponse(response(0x0001), 20, tx))
        assertFalse(StunProbe.isBindingResponse(response(0x0101, cookieOk = false), 20, tx))
        assertFalse(StunProbe.isBindingResponse(response(0x0101, id = ByteArray(12)), 20, tx))
        assertFalse(StunProbe.isBindingResponse(response(0x0101), 19, tx))
    }

    @Test fun udpServerThatAnswersIsWorking() {
        val server = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        val thread = Thread {
            val buf = ByteArray(64)
            val p = DatagramPacket(buf, buf.size)
            server.receive(p)
            val reply = buf.copyOf(20).also { it[0] = 0x01; it[1] = 0x01 }
            server.send(DatagramPacket(reply, reply.size, p.socketAddress))
        }.apply { start() }
        val status = runBlocking {
            TurnServerChecker().check(TurnUrl(secure = false, host = "127.0.0.1", port = server.localPort))
        }
        server.close(); thread.join(1000)
        assertEquals(TurnServerStatus.WORKING, status)
    }

    @Test fun closedPortIsUnreachable() {
        val port = ServerSocket(0).use { it.localPort } // порт освободили: никто не слушает
        val status = runBlocking {
            TurnServerChecker().check(TurnUrl(secure = false, host = "127.0.0.1", port = port))
        }
        assertEquals(TurnServerStatus.UNREACHABLE, status)
    }

    @Test fun turnsOpenTcpPortIsWorking() {
        ServerSocket(0).use { server ->
            val status = runBlocking {
                TurnServerChecker().check(TurnUrl(secure = true, host = "127.0.0.1", port = server.localPort))
            }
            assertEquals(TurnServerStatus.WORKING, status)
        }
    }
}
