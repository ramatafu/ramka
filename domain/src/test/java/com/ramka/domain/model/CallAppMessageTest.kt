package com.ramka.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/** Кодирование сообщений сигналинга звонков (этап 7.3). Текст/ACK/файлы покрыты AppMessageTest. */
class CallAppMessageTest {

    private fun uuid() = UUID.randomUUID().toString()

    private val sdp = "v=0\r\no=- 4611731400430051336 2 IN IP4 127.0.0.1\r\ns=-\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\n"

    @Test
    fun `offer round-trips with sdp and video flag`() {
        val audio = AppMessage.CallOffer(uuid(), sdp, withVideo = false)
        val video = AppMessage.CallOffer(uuid(), sdp, withVideo = true)
        assertEquals(audio, AppMessage.decode(audio.encode()))
        assertEquals(video, AppMessage.decode(video.encode()))
    }

    @Test
    fun `answer round-trips`() {
        val original = AppMessage.CallAnswer(uuid(), sdp)
        assertEquals(original, AppMessage.decode(original.encode()))
    }

    @Test
    fun `ice candidate round-trips including unicode-free mid and line index`() {
        val original = AppMessage.CallIceCandidate(
            callId = uuid(),
            sdpMid = "0",
            sdpMLineIndex = 0,
            candidate = "candidate:842163049 1 udp 1677729535 192.168.1.23 54321 typ host generation 0"
        )
        assertEquals(original, AppMessage.decode(original.encode()))
    }

    @Test
    fun `ice candidate with empty mid round-trips`() {
        val original = AppMessage.CallIceCandidate(uuid(), "", 1, "candidate:1 1 udp 2122260223 10.0.0.2 40000 typ host")
        assertEquals(original, AppMessage.decode(original.encode()))
    }

    @Test
    fun `end round-trips for every reason`() {
        for (reason in AppMessage.CallEnd.Reason.entries) {
            val original = AppMessage.CallEnd(uuid(), reason)
            assertEquals(original, AppMessage.decode(original.encode()))
        }
    }

    @Test
    fun `end carries only call id and reason byte`() {
        assertEquals(1 + 36 + 1, AppMessage.CallEnd(uuid()).encode().size)
    }

    @Test
    fun `unknown end reason decodes as hangup`() {
        val bytes = AppMessage.CallEnd(uuid(), AppMessage.CallEnd.Reason.BUSY).encode()
        bytes[bytes.size - 1] = 99
        assertEquals(AppMessage.CallEnd.Reason.HANGUP, (AppMessage.decode(bytes) as AppMessage.CallEnd).reason)
    }

    @Test
    fun `call types are distinguished by tag and do not collide with chat types`() {
        val id = uuid()
        assertTrue(AppMessage.decode(AppMessage.CallOffer(id, sdp, false).encode()) is AppMessage.CallOffer)
        assertTrue(AppMessage.decode(AppMessage.CallAnswer(id, sdp).encode()) is AppMessage.CallAnswer)
        assertTrue(AppMessage.decode(AppMessage.CallEnd(id).encode()) is AppMessage.CallEnd)
        assertTrue(AppMessage.decode(AppMessage.Text(id, "hi").encode()) is AppMessage.Text)
        assertTrue(AppMessage.decode(AppMessage.DeliveredAck(id).encode()) is AppMessage.DeliveredAck)
    }

    @Test
    fun `truncated call messages decode to null`() {
        val id = uuid()
        val offer = AppMessage.CallOffer(id, sdp, false).encode()
        assertNull(AppMessage.decode(offer.copyOfRange(0, 1 + 36)))          // нет флагов и SDP
        assertNull(AppMessage.decode(offer.copyOfRange(0, 1 + 36 + 1)))      // нет SDP
        val answer = AppMessage.CallAnswer(id, sdp).encode()
        assertNull(AppMessage.decode(answer.copyOfRange(0, 1 + 36)))         // нет SDP
        val ice = AppMessage.CallIceCandidate(id, "0", 0, "candidate:1").encode()
        assertNull(AppMessage.decode(ice.copyOfRange(0, 1 + 36 + 4)))        // нет длины mid
        assertNull(AppMessage.decode(ice.copyOfRange(0, 1 + 36 + 4 + 1 + 1))) // нет кандидата
        val end = AppMessage.CallEnd(id).encode()
        assertNull(AppMessage.decode(end.copyOfRange(0, end.size - 1)))
        assertNull(AppMessage.decode(end + byteArrayOf(0)))                   // лишний хвост
    }

    @Test
    fun `oversized sdp and candidate are rejected`() {
        val id = uuid()
        val hugeSdp = "a".repeat(AppMessage.MAX_SDP_BYTES + 1)
        assertNull(AppMessage.decode(AppMessage.CallOffer(id, hugeSdp, false).encode()))
        assertNull(AppMessage.decode(AppMessage.CallAnswer(id, hugeSdp).encode()))
        val hugeCandidate = "c".repeat(AppMessage.MAX_ICE_CANDIDATE_BYTES + 1)
        assertNull(AppMessage.decode(AppMessage.CallIceCandidate(id, "0", 0, hugeCandidate).encode()))
    }

    @Test
    fun `negative m-line index is rejected`() {
        val bytes = AppMessage.CallIceCandidate(uuid(), "0", 0, "candidate:1").encode()
        // индекс лежит сразу после тега и callId
        bytes[1 + 36] = 0x80.toByte()
        assertNull(AppMessage.decode(bytes))
    }
}
