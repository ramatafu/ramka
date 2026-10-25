package com.ramka.app.ui.chat

import com.ramka.domain.model.MessageBody
import com.ramka.domain.model.MessageStatus
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileBubbleTest {
    private val us = Locale.US
    private fun file(progress: Int = 0, uri: String? = null, size: Long = 2L * 1024 * 1024) =
        MessageBody.File("a.pdf", size, "application/pdf", uri, progress)

    @Test fun outgoingSendingShowsPercent() =
        assertEquals("2.0 МБ · Отправка… 40%", fileStatusLine(file(40), true, MessageStatus.SENDING, us))

    @Test fun outgoingFailed() =
        assertEquals("2.0 МБ · Ошибка отправки", fileStatusLine(file(40), true, MessageStatus.FAILED, us))

    @Test fun outgoingSentOrDeliveredShowsOnlySize() {
        assertEquals("2.0 МБ", fileStatusLine(file(100), true, MessageStatus.SENT, us))
        assertEquals("2.0 МБ", fileStatusLine(file(100), true, MessageStatus.DELIVERED, us))
        assertEquals("2.0 МБ", fileStatusLine(file(100), true, MessageStatus.READ, us))
    }

    @Test fun incomingStates() {
        assertEquals("2.0 МБ · Загрузка… 70%", fileStatusLine(file(70), false, MessageStatus.SENDING, us))
        assertEquals("2.0 МБ · Ошибка загрузки", fileStatusLine(file(70), false, MessageStatus.FAILED, us))
        assertEquals("2.0 МБ · Получено", fileStatusLine(file(100), false, MessageStatus.DELIVERED, us))
    }

    @Test fun progressIsClamped() =
        assertEquals("2.0 МБ · Отправка… 100%", fileStatusLine(file(250), true, MessageStatus.SENDING, us))

    @Test fun openableRules() {
        assertTrue(isFileOpenable(file(uri = "file:///x"), true, MessageStatus.SENDING))
        assertTrue(isFileOpenable(file(uri = "file:///x"), true, MessageStatus.FAILED))
        assertFalse(isFileOpenable(file(uri = null), true, MessageStatus.SENT))
        assertFalse(isFileOpenable(file(uri = null), false, MessageStatus.DELIVERED))
        assertFalse(isFileOpenable(file(uri = "content://m/1"), false, MessageStatus.SENDING))
        assertFalse(isFileOpenable(file(uri = "content://m/1"), false, MessageStatus.FAILED))
        assertTrue(isFileOpenable(file(uri = "content://m/1"), false, MessageStatus.DELIVERED))
        assertTrue(isFileOpenable(file(uri = "content://m/1"), false, MessageStatus.READ))
    }

    @Test fun offlineSignalNoteOnlyForOutgoingFailedWithSignal() {
        val note = "Собеседнику отправлено уведомление"
        assertEquals(note, offlineSignalNote(true, MessageStatus.FAILED, true))
        assertEquals(null, offlineSignalNote(true, MessageStatus.FAILED, false))
        assertEquals(null, offlineSignalNote(true, MessageStatus.SENT, true))
        assertEquals(null, offlineSignalNote(true, MessageStatus.SENDING, true))
        assertEquals(null, offlineSignalNote(false, MessageStatus.FAILED, true))
    }
}
