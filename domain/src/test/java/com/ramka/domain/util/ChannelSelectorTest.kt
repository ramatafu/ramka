package com.ramka.domain.util

import com.ramka.domain.model.Contact
import com.ramka.domain.model.KeyVerificationStatus
import com.ramka.domain.model.TransportChannel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelSelectorTest {
    private fun contact(ygg: String? = "user@[324:71e::53]") = Contact(
        localId = "c", alias = "a", publicKey = ByteArray(32), signingPublicKey = ByteArray(32),
        lastKnownAddress = null, verification = KeyVerificationStatus.UNVERIFIED, addedAtEpochDay = 0,
        yggmailAddress = ygg
    )

    private fun selector(
        local: Boolean = false,
        ygg: Boolean = false,
        relay: Boolean = false,
        yggEnabled: Boolean = true,
        yggProbeCalls: MutableList<String>? = null
    ) = ChannelSelector(
        isLocalNetworkAvailable = { local },
        isYggdrasilReachable = { yggProbeCalls?.add(it.localId); ygg },
        relayAvailable = { relay },
        yggdrasilEnabled = { yggEnabled }
    )

    private fun select(s: ChannelSelector, c: Contact = contact()) = runBlocking { s.selectChannel(c) }

    @Test fun localNetworkWinsOverEverything() {
        assertEquals(TransportChannel.LOCAL_WIFI, select(selector(local = true, ygg = true, relay = true)))
    }

    @Test fun yggdrasilBeatsRelayWhenReachable() {
        assertEquals(TransportChannel.YGGDRASIL, select(selector(ygg = true, relay = true)))
    }

    @Test fun relayIsTheFallback() {
        assertEquals(TransportChannel.RELAY, select(selector(ygg = false, relay = true)))
    }

    @Test fun nullWhenNothingIsAvailable() {
        assertNull(select(selector()))
    }

    @Test fun contactWithoutYggdrasilAddressIsNeverProbed() {
        val probes = mutableListOf<String>()
        assertEquals(TransportChannel.RELAY, select(selector(ygg = true, relay = true, yggProbeCalls = probes), contact(ygg = null)))
        assertEquals(emptyList<String>(), probes)
    }

    @Test fun invalidStoredAddressIsNeverProbed() {
        val probes = mutableListOf<String>()
        assertEquals(TransportChannel.RELAY, select(selector(ygg = true, relay = true, yggProbeCalls = probes), contact(ygg = "evil.example.com")))
        assertEquals(emptyList<String>(), probes)
    }

    @Test fun disabledYggdrasilIsSkipped() {
        val probes = mutableListOf<String>()
        assertEquals(TransportChannel.RELAY, select(selector(ygg = true, relay = true, yggEnabled = false, yggProbeCalls = probes)))
        assertEquals(emptyList<String>(), probes)
    }

    private fun selectorWithMail(local: Boolean = false, ygg: Boolean = false, relay: Boolean = false, mail: Boolean = false) =
        ChannelSelector(
            isLocalNetworkAvailable = { local },
            isYggdrasilReachable = { ygg },
            relayAvailable = { relay },
            yggmailAvailable = { mail }
        )

    @Test fun yggmailIsTheVeryLastResort() {
        assertEquals(TransportChannel.YGGMAIL, select(selectorWithMail(mail = true)))
    }

    @Test fun yggmailNeverBeatsOtherChannels() {
        assertEquals(TransportChannel.LOCAL_WIFI, select(selectorWithMail(local = true, mail = true)))
        assertEquals(TransportChannel.YGGDRASIL, select(selectorWithMail(ygg = true, mail = true)))
        assertEquals(TransportChannel.RELAY, select(selectorWithMail(relay = true, mail = true)))
    }

    @Test fun nullWhenEvenYggmailIsUnavailable() {
        assertNull(select(selectorWithMail(mail = false)))
    }
}

