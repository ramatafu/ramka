package com.ramka.domain.usecase

import com.ramka.domain.model.Contact
import com.ramka.domain.model.ContactConstants
import com.ramka.domain.model.KeyVerificationStatus
import com.ramka.domain.repository.ContactRepository
import com.ramka.domain.repository.TransportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttemptDeliveryUseCaseTest {
    private class OneContact(private val contact: Contact) : ContactRepository by StubContactRepository() {
        override suspend fun getContact(localId: String): Contact? = contact.takeIf { it.localId == localId }
    }

    private class CountingTransport(private val result: Boolean) : TransportRepository {
        var calls = 0
        override suspend fun sendEncryptedPacket(contact: Contact, packet: ByteArray): Boolean {
            calls++
            return result
        }
        override fun incomingPackets(): Flow<Pair<ByteArray, ByteArray>> = emptyFlow()
    }

    private fun contact(id: String, isSelf: Boolean) = Contact(
        localId = id,
        alias = id,
        publicKey = if (isSelf) ByteArray(0) else ByteArray(32) { 1 },
        signingPublicKey = if (isSelf) ByteArray(0) else ByteArray(32) { 2 },
        lastKnownAddress = null,
        verification = KeyVerificationStatus.VERIFIED_BY_QR,
        addedAtEpochDay = 0,
        isSelf = isSelf
    )

    @Test fun selfContactNeverReachesTransport() = runBlocking {
        val transport = CountingTransport(result = true)
        val useCase = AttemptDeliveryUseCase(OneContact(contact(ContactConstants.SELF_CONTACT_ID, true)), transport)
        assertFalse(useCase(ContactConstants.SELF_CONTACT_ID, byteArrayOf(1)))
        assertEquals(0, transport.calls)
    }

    @Test fun regularContactStillGoesThroughTransport() = runBlocking {
        val transport = CountingTransport(result = true)
        val useCase = AttemptDeliveryUseCase(OneContact(contact("alice", false)), transport)
        assertTrue(useCase("alice", byteArrayOf(1)))
        assertEquals(1, transport.calls)
    }
}
