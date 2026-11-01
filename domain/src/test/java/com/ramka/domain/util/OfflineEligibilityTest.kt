package com.ramka.domain.util

import com.ramka.domain.model.AppMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineEligibilityTest {
    private val id = "123e4567-e89b-12d3-a456-426614174000"

    @Test fun textAndAcksAndDeletionAreEligible() {
        assertTrue(OfflineEligibility.isEligible(AppMessage.Text(id, "привет").encode()))
        assertTrue(OfflineEligibility.isEligible(AppMessage.DeliveredAck(id).encode()))
        assertTrue(OfflineEligibility.isEligible(AppMessage.ReadAck(listOf(id)).encode()))
        assertTrue(OfflineEligibility.isEligible(AppMessage.DeleteMessage(id).encode()))
    }

    @Test fun callsAreNotEligible() {
        assertFalse(OfflineEligibility.isEligible(AppMessage.CallOffer(id, "v=0", false).encode()))
        assertFalse(OfflineEligibility.isEligible(AppMessage.CallAnswer(id, "v=0").encode()))
        assertFalse(OfflineEligibility.isEligible(AppMessage.CallEnd(id).encode()))
    }

    @Test fun fileChunksAreNotEligible() {
        assertFalse(OfflineEligibility.isEligible(AppMessage.FileChunk(id, 0, ByteArray(100)).encode()))
    }

    @Test fun garbageAndEmptyAreNotEligible() {
        assertFalse(OfflineEligibility.isEligible(ByteArray(0)))
        assertFalse(OfflineEligibility.isEligible(byteArrayOf(99, 1, 2, 3)))
    }

    @Test fun oversizedTextIsNotEligible() {
        assertFalse(OfflineEligibility.isEligible(AppMessage.Text(id, "я".repeat(20_000)).encode()))
    }
}
