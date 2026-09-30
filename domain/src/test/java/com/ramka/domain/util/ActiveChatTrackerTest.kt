package com.ramka.domain.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveChatTrackerTest {

    @Test
    fun `nothing is open initially`() {
        assertFalse(ActiveChatTracker().isOpen("a"))
    }

    @Test
    fun `shown chat is open and hidden chat is closed`() {
        val tracker = ActiveChatTracker()
        tracker.onChatShown("a")
        assertTrue(tracker.isOpen("a"))
        assertFalse(tracker.isOpen("b"))
        tracker.onChatHidden("a")
        assertFalse(tracker.isOpen("a"))
    }

    @Test
    fun `hiding a stale chat does not close the newly shown one`() {
        val tracker = ActiveChatTracker()
        tracker.onChatShown("a")
        tracker.onChatShown("b")   // переход a -> b
        tracker.onChatHidden("a")  // запоздалый onStop/onCleared старого чата
        assertTrue(tracker.isOpen("b"))
    }
}
