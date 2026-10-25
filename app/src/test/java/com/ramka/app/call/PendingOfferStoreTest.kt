package com.ramka.app.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingOfferStoreTest {
    private var clock = 1_000L
    private val store = PendingOfferStore<String>(now = { clock }, ttlMillis = 60_000L)

    @Test fun takeReturnsFreshOfferOnceAndClears() {
        store.put("call-1", "offer")
        assertEquals("offer", store.take())
        assertNull(store.take())
    }

    @Test fun expiredOfferIsNotReturned() {
        store.put("call-1", "offer")
        clock += 60_000L
        assertNull(store.key())
        assertNull(store.take())
    }

    @Test fun keyIsVisibleWhileFresh() {
        store.put("call-1", "offer")
        clock += 59_999L
        assertEquals("call-1", store.key())
    }

    @Test fun clearOnlyRemovesMatchingCall() {
        store.put("call-1", "offer")
        store.clear("other")
        assertEquals("call-1", store.key())
        store.clear("call-1")
        assertNull(store.key())
    }

    @Test fun newerOfferReplacesOlder() {
        store.put("call-1", "a")
        store.put("call-2", "b")
        assertEquals("b", store.take())
    }

    @Test fun clockMovedBackwardsTreatsOfferAsExpired() {
        store.put("call-1", "offer")
        clock -= 1_000L
        assertNull(store.take())
    }
}
