package com.ramka.app.discovery

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class LanPresenceCoordinatorTest {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @After
    fun tearDown() = scope.cancel()

    /** Фейковый NSD: пишет события в общий журнал и умеет «находить» пиров. */
    private class Env(initiallyEnabled: Boolean) {
        val enabled = MutableStateFlow(initiallyEnabled)
        val events = CopyOnWriteArrayList<String>()
        val peers = MutableSharedFlow<Int>(extraBufferCapacity = 16)
        val discoveryActive = AtomicInteger(0) // сколько подписок на поиск живо сейчас
        val processed = AtomicInteger(0)
        private val names = AtomicInteger(0)

        var discoveryFlow: () -> Flow<Any?> = {
            flow {
                discoveryActive.incrementAndGet()
                events += "discovery:start"
                try {
                    peers.collect { emit(it) }
                } finally {
                    discoveryActive.decrementAndGet()
                    events += "discovery:stop"
                }
            }
        }
        var onPeer: suspend () -> Unit = { processed.incrementAndGet() }

        fun coordinator() = LanPresenceCoordinator(
            enabled = enabled,
            register = { events += "register:$it" },
            unregister = { events += "unregister" },
            discoverPeers = { discoveryFlow() },
            onPeerDiscovered = { onPeer() },
            newInstanceName = { "n${names.incrementAndGet()}" }
        )
    }

    private suspend fun eventually(message: String = "условие не выполнено за 3 с", check: () -> Boolean) {
        try {
            withTimeout(3_000) { while (!check()) delay(10) }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError(message)
        }
    }

    private suspend fun Env.emitPeer() {
        // подписка на SharedFlow может оформиться чуть позже старта: ждём, пока поиск реально активен
        eventually { discoveryActive.get() > 0 }
        peers.emit(1)
    }

    @Test
    fun `enabled at start registers once and starts discovery`() = runBlocking {
        val env = Env(initiallyEnabled = true)
        env.coordinator().start(scope)

        eventually { env.discoveryActive.get() == 1 }

        assertEquals(listOf("register:n1", "discovery:start"), env.events.toList())
    }

    @Test
    fun `disabled at start does nothing at all`() = runBlocking {
        val env = Env(initiallyEnabled = false)
        env.coordinator().start(scope)
        delay(300)

        assertTrue("ни регистрации, ни поиска: ${env.events}", env.events.isEmpty())
    }

    @Test
    fun `switching off stops discovery and unregisters`() = runBlocking {
        val env = Env(initiallyEnabled = true)
        env.coordinator().start(scope)
        eventually { env.discoveryActive.get() == 1 }

        env.enabled.value = false
        eventually { env.events.contains("unregister") }

        assertEquals(0, env.discoveryActive.get())
        assertEquals(listOf("register:n1", "discovery:start", "discovery:stop", "unregister"), env.events.toList())
    }

    @Test
    fun `switching back on registers again under a new name and restarts discovery`() = runBlocking {
        val env = Env(initiallyEnabled = true)
        env.coordinator().start(scope)
        eventually { env.discoveryActive.get() == 1 }

        env.enabled.value = false
        eventually { env.events.contains("unregister") }
        env.enabled.value = true
        eventually { env.discoveryActive.get() == 1 }

        assertEquals(
            listOf(
                "register:n1", "discovery:start", "discovery:stop", "unregister",
                "register:n2", "discovery:start"
            ),
            env.events.toList()
        )
    }

    @Test
    fun `discovered peer triggers outbox processing only while enabled`() = runBlocking {
        val env = Env(initiallyEnabled = true)
        env.coordinator().start(scope)

        env.emitPeer()
        eventually { env.processed.get() == 1 }

        env.enabled.value = false
        eventually { env.discoveryActive.get() == 0 }
        env.peers.emit(2) // пира «нашли», но подписки на поиск уже нет
        delay(300)

        assertEquals(1, env.processed.get())
    }

    @Test
    fun `rapid toggling never leaves two registrations alive`() = runBlocking {
        val env = Env(initiallyEnabled = true)
        env.coordinator().start(scope)
        eventually { env.discoveryActive.get() == 1 }

        repeat(10) {
            env.enabled.value = false
            env.enabled.value = true
        }
        env.enabled.value = true
        delay(500)

        var active = 0
        for (event in env.events) {
            if (event.startsWith("register:")) active++
            if (event == "unregister") active--
            assertTrue("регистраций одновременно: $active, события=${env.events}", active in 0..1)
        }
        assertEquals("после включения регистрация ровно одна", 1, active)
        assertEquals(1, env.discoveryActive.get())
    }

    @Test
    fun `discovery failure keeps advertising and the toggle still works`() = runBlocking {
        val env = Env(initiallyEnabled = true)
        env.discoveryFlow = { flow { throw IllegalStateException("NSD недоступен") } }
        env.coordinator().start(scope)
        delay(300)

        assertEquals("поиск упал, вещание не снято", listOf("register:n1"), env.events.toList())

        env.enabled.value = false
        eventually { env.events.contains("unregister") }
        assertEquals(listOf("register:n1", "unregister"), env.events.toList())
    }

    @Test
    fun `discovery that completes by itself keeps advertising`() = runBlocking {
        val env = Env(initiallyEnabled = true)
        env.discoveryFlow = { flow { env.events += "discovery:done" } }
        env.coordinator().start(scope)
        delay(300)

        assertFalse("unregister не должен вызываться: ${env.events}", env.events.contains("unregister"))
        assertTrue(env.events.contains("register:n1"))
    }

    @Test
    fun `failing peer handler does not break discovery or the toggle`() = runBlocking {
        val env = Env(initiallyEnabled = true)
        val calls = AtomicInteger(0)
        env.onPeer = {
            if (calls.incrementAndGet() == 1) throw IllegalStateException("сбой Outbox")
            env.processed.incrementAndGet()
        }
        env.coordinator().start(scope)

        env.emitPeer()
        env.emitPeer()
        eventually { env.processed.get() == 1 } // вторая обработка прошла, первая упала молча

        env.enabled.value = false
        eventually { env.events.contains("unregister") }
    }

    @Test
    fun `random instance name is ephemeral and not a device name`() {
        val names = List(50) { LanPresenceCoordinator.randomInstanceName() }
        assertTrue(names.all { Regex("ramka-[0-9a-f]{8}").matches(it) })
        assertTrue("имена должны различаться", names.toSet().size > 40)
    }
}
