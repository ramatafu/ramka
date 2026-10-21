package com.ramka.app.ui.chat

import com.ramka.domain.model.MessageDirection.INCOMING
import com.ramka.domain.model.MessageDirection.OUTGOING
import com.ramka.domain.model.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageInfoTest {

    @Test fun `outgoing statuses`() {
        assertEquals("Отправляется", statusLabel(OUTGOING, MessageStatus.SENDING))
        assertEquals("Отправлено", statusLabel(OUTGOING, MessageStatus.SENT))
        assertEquals("Доставлено", statusLabel(OUTGOING, MessageStatus.DELIVERED))
        assertEquals("Прочитано", statusLabel(OUTGOING, MessageStatus.READ))
        assertEquals("Не удалось отправить", statusLabel(OUTGOING, MessageStatus.FAILED))
    }

    @Test fun `incoming statuses`() {
        assertEquals("Получено", statusLabel(INCOMING, MessageStatus.DELIVERED))
        assertEquals("Принимается", statusLabel(INCOMING, MessageStatus.SENDING))
        assertEquals("Приём прервался", statusLabel(INCOMING, MessageStatus.FAILED))
    }
}
