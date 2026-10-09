package com.ramka.domain.relay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class RelayHealthMonitorTest {

    private class FakePoolSource(initial: RelayPool) : RelayPoolSource {
        private val _state = kotlinx.coroutines.flow.MutableStateFlow(initial)
        val state: kotlinx.coroutines.flow.MutableStateFlow<RelayPool> = _state
        override val pool: kotlinx.coroutines.flow.StateFlow<RelayPool> = _state
    }

    private class FakeChecker : RelayChecker {
        val counts = ConcurrentHashMap<String, AtomicInteger>()
        var ok = true
        fun count(address: String): Int = counts[address]?.get() ?: 0
        override suspend fun check(endpoint: RelayEndpoint): RelayCheckResult {
            counts.getOrPut(endpoint.address.format()) { AtomicInteger(0) }.incrementAndGet()
            return if (ok) RelayCheckResult.Ok else RelayCheckResult.Failed(RelayFailure.TIMEOUT)
        }
    }

    private class Env(pool: RelayPool, val interval: Long = 50) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val source = FakePoolSource(pool)
        val provider = PoolRelayProvider(source)
        val checker = FakeChecker()
        val clock = object : RelayClock { override fun nowMillis(): Long = System.currentTimeMillis() }
        val monitor = RelayHealthMonitor(
            source, provider, checker, clock,
            RelayMonitorConfig(intervalMillis = interval, batterySaverIntervalMillis = interval)
        )
        fun stop() { scope.cancel() }
    }

    private fun entry(id: String, host: String) = RelayEntry(
        id,
        (RelayAddress.parse("$host:443") as RelayAddressParseResult.Success).address,
        "x".repeat(32),
        "ab".repeat(32),
        ""
    )

    private fun poolOf(vararg hosts: String, enabled: Boolean = true, batterySaver: Boolean = false) =
        RelayPool(
            entries = hosts.mapIndexed { i, h -> entry(i.toString(), h) },
            enabled = enabled,
            batterySaver = batterySaver
        )

    private fun waitUntil(desc: String, timeoutMs: Long = 3000, block: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!block()) {
            if (System.currentTimeMillis() - start > timeoutMs) {
                throw AssertionError("Timeout: $desc")
            }
            Thread.sleep(20)
        }
    }

    private fun pause(ms: Long) = Thread.sleep(ms)

    @Test
    fun `повторный start не запускает второе наблюдение и возвращает тот же Job`() {
        val env = Env(poolOf("a"), interval = 40)
        try {
            val first = env.monitor.start(env.scope)
            val second = env.monitor.start(env.scope)
            val third = env.monitor.start(env.scope)
            assertTrue(first === second && second === third)

            waitUntil("несколько проверок") { env.checker.count("a:443") >= 3 }
            pause(400)
            assertTrue("слишком много проверок: ${env.checker.count("a:443")}", env.checker.count("a:443") < 25)
            assertEquals(1, env.scope.coroutineContext[Job]!!.children.count())
        } finally {
            env.stop()
        }
    }

    @Test
    fun `после отмены start запускает наблюдение заново`() {
        val env = Env(poolOf("a"), interval = 30)
        try {
            val first = env.monitor.start(env.scope)
            waitUntil("первая проверка") { env.checker.count("a:443") >= 1 }
            first.cancel()
            pause(120)
            val frozen = env.checker.count("a:443")
            pause(150)
            assertEquals(frozen, env.checker.count("a:443"))

            val second = env.monitor.start(env.scope)
            assertTrue(first !== second)
            waitUntil("проверки возобновились") { env.checker.count("a:443") > frozen }
        } finally {
            env.stop()
        }
    }

    @Test
    fun `выключение пула отменяет проверку, которая уже идёт`() {
        val env = Env(poolOf("a"), interval = 60_000)
        val started = AtomicBoolean(false)
        val cancelled = AtomicBoolean(false)
        val slow = object : RelayChecker {
            override suspend fun check(endpoint: RelayEndpoint): RelayCheckResult {
                started.set(true)
                try {
                    delay(60_000) // «зависший» сервер
                } catch (e: kotlinx.coroutines.CancellationException) {
                    cancelled.set(true)
                    throw e
                }
                return RelayCheckResult.Ok
            }
        }
        val monitor = RelayHealthMonitor(env.source, env.provider, slow, env.clock, RelayMonitorConfig(60_000, 60_000))
        try {
            monitor.start(env.scope)
            waitUntil("проверка началась") { started.get() }
            env.source.state.value = poolOf("a", enabled = false)
            waitUntil("проверка отменена при выключении") { cancelled.get() }
        } finally {
            env.stop()
        }
    }

    @Test
    fun `выключение, а затем включение запускает проверки заново без накопления`() {
        val env = Env(poolOf("a"), interval = 60_000)
        try {
            env.monitor.start(env.scope)
            waitUntil("первая проверка") { env.checker.count("a:443") >= 1 }
            env.source.state.value = poolOf("a", enabled = false)
            pause(150)
            val afterOff = env.checker.count("a:443")

            env.source.state.value = poolOf("a", enabled = true)
            waitUntil("проверка после включения") { env.checker.count("a:443") > afterOff }
            pause(250)
            assertEquals(afterOff + 1, env.checker.count("a:443")) // ровно одна новая проверка, интервал 60 с
        } finally {
            env.stop()
        }
    }
}