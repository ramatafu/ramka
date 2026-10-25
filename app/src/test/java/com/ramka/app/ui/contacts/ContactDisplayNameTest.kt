package com.ramka.app.ui.contacts

import com.ramka.domain.model.Contact
import com.ramka.domain.model.KeyVerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ContactDisplayNameTest {
    private fun contact(alias: String, key: ByteArray = byteArrayOf(0x1a, 0x2b, 0x3c, 0x4d, 0x5e), photoUri: String? = null) = Contact(
        localId = "id",
        alias = alias,
        publicKey = key,
        signingPublicKey = ByteArray(0),
        lastKnownAddress = null,
        verification = KeyVerificationStatus.VERIFIED_BY_QR,
        addedAtEpochDay = 0,
        photoUri = photoUri
    )

    @Test fun usesTrimmedAliasWhenSet() {
        assertEquals("Аня", contact("  Аня ").displayName())
    }

    @Test fun blankAliasFallsBackToKeyFingerprint() {
        assertEquals("ключ 1a2b3c4d", contact("   ").displayName())
    }

    @Test fun blankAliasAndEmptyKeyFallsBackToGenericName() {
        assertEquals("Контакт", contact("", key = ByteArray(0)).displayName())
    }

    @Test fun photoUriParticipatesInEqualityAndHash() {
        val a = contact("Аня", photoUri = "file:///a.jpg")
        val b = contact("Аня", photoUri = "file:///b.jpg")
        assertNotEquals(a, b)
        assertEquals(a, contact("Аня", photoUri = "file:///a.jpg"))
        assertEquals(a.hashCode(), contact("Аня", photoUri = "file:///a.jpg").hashCode())
    }
}
