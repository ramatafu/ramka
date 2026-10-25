package com.ramka.domain.usecase

import com.ramka.domain.model.ContactConstants
import com.ramka.domain.model.MessageBody
import com.ramka.domain.model.MessageDirection
import com.ramka.domain.model.MessageStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveSelfNoteUseCaseTest {
    private val repo = StubMessageRepository()
    private val useCase = SaveSelfNoteUseCase(repo)

    @Test fun textNoteIsStoredLocallyAsSentOutgoingForSelfContact() = runBlocking {
        val saved = useCase.saveText("купить хлеб", nowEpochMillis = 42L)
        val stored = repo.all().single()
        assertEquals(saved, stored)
        assertEquals(ContactConstants.SELF_CONTACT_ID, stored.contactId)
        assertEquals(MessageDirection.OUTGOING, stored.direction)
        assertEquals(MessageStatus.SENT, stored.status)
        assertEquals(MessageBody.Text("купить хлеб"), stored.body)
        assertEquals(42L, stored.localCreatedAtEpochMillis)
    }

    @Test fun fileNoteKeepsLocalCopyIsCompleteAndSanitized() = runBlocking {
        useCase.saveFile("../evil/name.txt", 10L, "text/plain", "file:///data/outgoing_files/x.txt")
        val stored = repo.all().single()
        val body = stored.body as MessageBody.File
        assertEquals(ContactConstants.SELF_CONTACT_ID, stored.contactId)
        assertEquals(MessageStatus.SENT, stored.status)
        assertEquals(100, body.progress)
        assertEquals("file:///data/outgoing_files/x.txt", body.uri)
        assertTrue("имя файла без путей: ${body.fileName}", !body.fileName.contains('/'))
    }

    @Test fun notesAreNeverReportedAsPendingForRetry() = runBlocking {
        useCase.saveText("a")
        useCase.saveFile("f.bin", 1L, "application/octet-stream", "file:///x")
        assertTrue(repo.all().none { it.status == MessageStatus.SENDING || it.status == MessageStatus.FAILED })
    }
}
