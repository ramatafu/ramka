package com.ramka.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceMessagesTest {
    private fun file(name: String, mime: String, uri: String? = "file:///x") =
        MessageBody.File(fileName = name, fileSize = 1234L, mimeType = mime, uri = uri)

    @Test fun opusVoiceIsParsedWithDuration() {
        val voice = file("voice-45s.ogg", "audio/ogg").asVoice()
        assertNotNull(voice)
        assertEquals(45, voice!!.durationSeconds)
        assertEquals(1234L, voice.fileSize)
        assertEquals("file:///x", voice.uri)
    }

    @Test fun aacFallbackIsParsed() {
        assertEquals(300, file("voice-300s.m4a", "audio/mp4").asVoice()?.durationSeconds)
    }

    @Test fun uriIsNullUntilFileIsReceived() {
        assertNull(file("voice-5s.ogg", "audio/ogg", uri = null).asVoice()!!.uri)
    }

    @Test fun regularFilesAreNotVoice() {
        assertNull(file("song.ogg", "audio/ogg").asVoice())
        assertNull(file("voice-5s.ogg", "application/octet-stream").asVoice())
        assertNull(file("voice-5s.m4a", "audio/ogg").asVoice())
        assertNull(file("voice-5s.mp3", "audio/mpeg").asVoice())
    }

    @Test fun outOfRangeDurationIsRejected() {
        assertNull(file("voice-0s.ogg", "audio/ogg").asVoice())
        assertNull(file("voice-301s.ogg", "audio/ogg").asVoice())
        assertNull(file("voice-9999s.ogg", "audio/ogg").asVoice())
    }

    @Test fun fileNameRoundTripsThroughParser() {
        for (opus in listOf(true, false)) {
            val body = file(VoiceMessages.fileName(73, opus), VoiceMessages.mimeType(opus))
            assertEquals(73, body.asVoice()?.durationSeconds)
        }
    }

    @Test fun fileNameClampsDuration() {
        assertEquals("voice-1s.ogg", VoiceMessages.fileName(0, true))
        assertEquals("voice-300s.m4a", VoiceMessages.fileName(999, false))
    }

    @Test fun durationFormatting() {
        assertEquals("0:00", VoiceMessages.formatDuration(0))
        assertEquals("0:45", VoiceMessages.formatDuration(45))
        assertEquals("1:05", VoiceMessages.formatDuration(65))
        assertEquals("5:00", VoiceMessages.formatDuration(300))
        assertEquals("0:00", VoiceMessages.formatDuration(-3))
    }
}
