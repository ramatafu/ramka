package com.ramka.app.turn

import com.ramka.app.connection.NetworkKind
import com.ramka.app.data.TurnServerEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Проверка доступности TURN для индикатора: «неизвестно» и «недоступен» не дают жёлтого. */
class TurnServerMonitorTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After fun tearDown() = scope.cancel()

    private fun entry(id: String, host: String, enabled: Boolean = true, order: Int = 0) =
        TurnServerEntry(id = id, url = "turn:$host:3478", username = "u", isEnabled = enabled, order = order)

    private inner class Fixture(
        initial: List<TurnServerEntry>,
        refreshMillis: Long = 40,
        stopTimeoutMillis: Long = 50
    ) {
        val servers = MutableStateFlow(initial)
        val status = ConcurrentHashMap<String, TurnServerStatus>() // host -> результат; по умолчанию UNREACHABLE
        val gates = ConcurrentHashMap<String, CompletableDeferred<Unit>>() // проверка этого хоста ждёт, пока гейт не откроют
        val failing: MutableSet<String> = ConcurrentHashMap.newKeySet() // проверка этого хоста бросает исключение
        val checks = AtomicInteger(0)
        val network = MutableSharedFlow<NetworkKind>(replay = 1).also { it.tryEmit(NetworkKind.OTHER) } // начальное значение

        val monitor = TurnServerMonitor(
            servers = servers,
            check = { url ->
                checks.incrementAndGet()
                gates[url.host]?.await()
                if (url.host in failing) error("сбой проверки")
                status[url.host] ?: TurnServerStatus.UNREACHABLE
            },
            networkChanges = network,
            appScope = scope,
            refreshMillis = refreshMillis,
            stopTimeoutMillis = stopTimeoutMillis
        )

        /** Подписчик: пока он есть, монитор работает (как индикатор на главном экране). */
        fun subscribe(): Job = scope.launch { monitor.isTurnReady.collect { } }

        val ready get() = monitor.isTurnReady.value
    }

    private suspend fun awaitCondition(what: String, timeoutMs: Long = 3_000, condition: () -> Boolean) {
        try {
            withTimeout(timeoutMs) { while (!condition()) delay(10) }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("Не дождались: $what")
        }
    }

    @Test fun `unknown before the first check is false and becomes true only when a check succeeds`() = runBlocking {
        val f = Fixture(listOf(entry("a", "a.test")))
        f.status["a.test"] = TurnServerStatus.WORKING
        val gate = CompletableDeferred<Unit>().also { f.gates["a.test"] = it }
        f.subscribe()

        awaitCondition("проверка началась") { f.checks.get() >= 1 }
        assertFalse("пока проверка идёт, статус неизвестен: не жёлтый", f.ready)

        gate.complete(Unit)
        awaitCondition("TURN готов") { f.ready }
    }

    @Test fun `all servers unreachable stays false`() = runBlocking {
        val f = Fixture(listOf(entry("a", "a.test"), entry("b", "b.test", order = 1)))
        f.subscribe()

        awaitCondition("несколько циклов проверки") { f.checks.get() >= 4 }
        assertFalse(f.ready)
    }

    @Test fun `any working server is enough, even when the first one is dead`() = runBlocking {
        val f = Fixture(listOf(entry("a", "dead.test"), entry("b", "alive.test", order = 1)))
        f.status["alive.test"] = TurnServerStatus.WORKING
        f.subscribe()

        awaitCondition("TURN готов по второму серверу") { f.ready }
    }

    @Test fun `working then unreachable drops to false on the next cycle`() = runBlocking {
        val f = Fixture(listOf(entry("a", "a.test")))
        f.status["a.test"] = TurnServerStatus.WORKING
        f.subscribe()
        awaitCondition("TURN готов") { f.ready }

        f.status["a.test"] = TurnServerStatus.UNREACHABLE

        awaitCondition("TURN недоступен") { !f.ready }
    }

    @Test fun `empty list and disabled servers give false and no checks`() = runBlocking {
        val f = Fixture(emptyList())
        f.subscribe()
        delay(150)
        assertFalse(f.ready)

        f.servers.value = listOf(entry("a", "a.test", enabled = false))
        f.status["a.test"] = TurnServerStatus.WORKING
        delay(150)
        assertFalse("выключенный сервер не проверяется и жёлтого не даёт", f.ready)
        assertEquals(0, f.checks.get())
    }

    @Test fun `list change resets to false until the new check finishes`() = runBlocking {
        val f = Fixture(listOf(entry("a", "a.test")))
        f.status["a.test"] = TurnServerStatus.WORKING
        f.status["b.test"] = TurnServerStatus.WORKING
        f.subscribe()
        awaitCondition("TURN готов") { f.ready }

        val gate = CompletableDeferred<Unit>().also { f.gates["b.test"] = it }
        f.servers.value = listOf(entry("b", "b.test"))

        awaitCondition("значение сброшено на время проверки") { !f.ready }
        delay(100)
        assertFalse("пока проверка нового списка не закончилась, TURN не считается доступным", f.ready)

        gate.complete(Unit)
        awaitCondition("TURN снова готов") { f.ready }
    }

    @Test fun `enabling the only server starts checking it`() = runBlocking {
        val f = Fixture(listOf(entry("a", "a.test", enabled = false)))
        f.status["a.test"] = TurnServerStatus.WORKING
        f.subscribe()
        delay(100)
        assertFalse(f.ready)

        f.servers.value = listOf(entry("a", "a.test", enabled = true))

        awaitCondition("TURN готов") { f.ready }
    }

    @Test fun `network change resets at once and rechecks without waiting for the interval`() = runBlocking {
        val f = Fixture(listOf(entry("a", "a.test")), refreshMillis = 60_000) // обычный цикл здесь не сработает
        f.status["a.test"] = TurnServerStatus.WORKING
        f.subscribe()
        awaitCondition("TURN готов") { f.ready }
        delay(150)
        assertEquals("начальное значение сети не запускает лишнюю проверку", 1, f.checks.get())

        // Wi-Fi -> мобильная сеть: на новой сети TURN недоступен
        f.status["a.test"] = TurnServerStatus.UNREACHABLE
        f.network.emit(NetworkKind.LOCAL)

        awaitCondition("статус сброшен после смены сети") { !f.ready }
        awaitCondition("перепроверка сразу, не через 60 секунд") { f.checks.get() >= 2 }
        assertFalse(f.ready)
    }

    @Test fun `network change followed by a working server turns ready again`() = runBlocking {
        val f = Fixture(listOf(entry("a", "a.test")), refreshMillis = 60_000)
        f.status["a.test"] = TurnServerStatus.WORKING
        f.subscribe()
        awaitCondition("TURN готов") { f.ready }

        val gate = CompletableDeferred<Unit>().also { f.gates["a.test"] = it }
        f.network.emit(NetworkKind.NONE)
        awaitCondition("сброс") { !f.ready }
        gate.complete(Unit)

        awaitCondition("TURN готов после перепроверки") { f.ready }
    }

    @Test fun `check failure is treated as unreachable and the loop keeps running`() = runBlocking {
        val f = Fixture(listOf(entry("a", "a.test")))
        f.status["a.test"] = TurnServerStatus.WORKING
        f.failing += "a.test"
        f.subscribe()
        awaitCondition("несколько неудачных циклов") { f.checks.get() >= 3 }
        assertFalse(f.ready)

        f.failing -= "a.test"

        awaitCondition("после исправления TURN готов") { f.ready }
    }

    @Test fun `value is reset to false when the last subscriber is gone`() = runBlocking {
        val f = Fixture(listOf(entry("a", "a.test")), stopTimeoutMillis = 50)
        f.status["a.test"] = TurnServerStatus.WORKING
        val subscription = f.subscribe()
        awaitCondition("TURN готов") { f.ready }

        subscription.cancel()

        awaitCondition("старое true не сохраняется без подписчиков") { !f.ready }

        // Подписчик вернулся, а новая проверка ещё идёт: должно быть «неизвестно» (false), а не старое «готов».
        val gate = CompletableDeferred<Unit>().also { f.gates["a.test"] = it }
        f.subscribe()
        delay(200)
        assertFalse("до результата новой проверки TURN не считается доступным", f.ready)

        gate.complete(Unit)
        awaitCondition("после проверки TURN готов") { f.ready }
    }
}
