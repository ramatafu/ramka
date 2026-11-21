package com.ramka.app.outbox

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RelayRestoredEventsTest {

    private fun events(vararg states: Boolean) = runBlocking { relayRestoredEvents(flowOf(*states.toTypedArray()), 20).toList().size }

    @Test fun `no connected relay gives no event`() = assertEquals(0, events(false, false))

    @Test fun `transition from none to connected gives one event`() = assertEquals(1, events(false, true))

    @Test fun `already connected at start gives one event`() = assertEquals(1, events(true))

    @Test fun `going offline gives no event`() = assertEquals(0, events(true, false))

    @Test fun `repeated connected state is not a new event`() = assertEquals(1, events(false, true, true, true))
}
