package com.ramka.domain.relay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayHealthTest {

    private val clock = FakeClock()
    private val health = RelayHealth(clock)

    private val a = addr("a")
    private val b = addr("b")
    private val c = addr("c")

    private fun order(contact: String? = "c1", vararg ids: String) = health.order(contact, ids.map { addr(it) }).map { it.host.substringBefore('.') }

    // ---- cooldown ----

    @Test
    fun `нет сведений — релей не в паузе`() {
        assertFalse(health.isCoolingDown(a))
        assertEquals(0L, health.cooldownRemainingMillis(a))
    }

    @Test
    fun `пауза после ошибки инфраструктуры нарастает 30 с, 60 с, 120 с, 240 с, потолок 5 минут`() {
        val expected = listOf(30_000L, 60_000L, 120_000L, 240_000L, 300_000L, 300_000L, 300_000L)
        for ((i, pause) in expected.withIndex()) {
            health.recordFailure(a, RelayFailure.UNREACHABLE)
            assertTrue("ошибка №${i + 1}", health.isCoolingDown(a))
            assertEquals("ошибка №${i + 1}", pause, health.cooldownRemainingMillis(a))
            clock.advance(pause) // пауза закончилась, релей снова пробуют и он снова не отвечает
            assertFalse(health.isCoolingDown(a))
        }
    }

    @Test
    fun `все ошибки инфраструктуры дают одинаковую первую паузу`() {
        for (failure in listOf(RelayFailure.UNREACHABLE, RelayFailure.TLS, RelayFailure.TIMEOUT, RelayFailure.BUSY, RelayFailure.INTERNAL, RelayFailure.PROTOCOL)) {
            val h = RelayHealth(clock)
            h.recordFailure(a, failure)
            assertEquals("$failure", 30_000L, h.cooldownRemainingMillis(a))
        }
    }

    @Test
    fun `ошибки настройки сразу дают максимальную паузу`() {
        for (failure in listOf(RelayFailure.AUTH, RelayFailure.TLS13_UNSUPPORTED)) {
            val h = RelayHealth(clock)
            h.recordFailure(a, failure)
            assertEquals("$failure", 300_000L, h.cooldownRemainingMillis(a))
            clock.advance(299_999)
            assertTrue(h.isCoolingDown(a))
            clock.advance(1)
            assertFalse(h.isCoolingDown(a))
        }
    }

    @Test
    fun `пауза истекает ровно по времени`() {
        health.recordFailure(a, RelayFailure.TIMEOUT)
        clock.advance(29_999)
        assertTrue(health.isCoolingDown(a))
        assertEquals(1L, health.cooldownRemainingMillis(a))
        clock.advance(1)
        assertFalse(health.isCoolingDown(a))
    }

    @Test
    fun `TARGET_OFFLINE и REPLACED не штрафуются`() {
        for (failure in listOf(RelayFailure.TARGET_OFFLINE, RelayFailure.REPLACED)) {
            val h = RelayHealth(clock)
            h.recordFailure(a, failure)
            assertFalse("$failure", h.isCoolingDown(a))
            assertEquals(0L, h.metrics(a).failureCount)
            assertEquals(0, h.metrics(a).consecutiveFailures)
        }
    }

    @Test
    fun `нейтральная ошибка снимает действующую паузу и серию ошибок — релей же ответил`() {
        health.recordFailure(a, RelayFailure.UNREACHABLE)
        health.recordFailure(a, RelayFailure.UNREACHABLE)
        assertEquals(2, health.metrics(a).consecutiveFailures)
        health.recordFailure(a, RelayFailure.TARGET_OFFLINE)
        assertFalse(health.isCoolingDown(a))
        assertEquals(0, health.metrics(a).consecutiveFailures)
        assertEquals(2L, health.metrics(a).failureCount) // история ошибок остаётся
        health.recordFailure(a, RelayFailure.UNREACHABLE)
        assertEquals(30_000L, health.cooldownRemainingMillis(a)) // нарастание началось заново
    }

    @Test
    fun `recordHealthy снимает паузу и обнуляет серию`() {
        health.recordFailure(a, RelayFailure.AUTH)
        health.recordHealthy(a)
        assertFalse(health.isCoolingDown(a))
        assertEquals(0, health.metrics(a).consecutiveFailures)
        assertEquals(0L, health.metrics(a).successCount) // это не доставка
    }

    @Test
    fun `успех после ошибки возвращает релей и сбрасывает нарастание`() {
        health.recordFailure(a, RelayFailure.UNREACHABLE)
        health.recordFailure(a, RelayFailure.UNREACHABLE)
        health.recordDelivery(a, "c1", 100, fallback = false)
        assertFalse(health.isCoolingDown(a))
        health.recordFailure(a, RelayFailure.UNREACHABLE)
        assertEquals(30_000L, health.cooldownRemainingMillis(a))
    }

    @Test
    fun `паузы релеёв независимы`() {
        health.recordFailure(a, RelayFailure.UNREACHABLE)
        assertTrue(health.isCoolingDown(a))
        assertFalse(health.isCoolingDown(b))
    }

    @Test
    fun `классификация покрывает все ошибки`() {
        for (failure in RelayFailure.values()) RelayHealth.kindOf(failure) // when без else: компилятор уже проверил полноту
        assertEquals(RelayFailureKind.NEUTRAL, RelayHealth.kindOf(RelayFailure.TARGET_OFFLINE))
        assertEquals(RelayFailureKind.NEUTRAL, RelayHealth.kindOf(RelayFailure.REPLACED))
        assertEquals(RelayFailureKind.CONFIGURATION, RelayHealth.kindOf(RelayFailure.AUTH))
        assertEquals(RelayFailureKind.CONFIGURATION, RelayHealth.kindOf(RelayFailure.TLS13_UNSUPPORTED))
        assertEquals(RelayFailureKind.INFRASTRUCTURE, RelayHealth.kindOf(RelayFailure.TIMEOUT))
    }

    // ---- порядок ----

    @Test
    fun `без сведений порядок равен приоритету из настроек`() {
        assertEquals(listOf("a", "b", "c"), order("c1", "a", "b", "c"))
    }

    @Test
    fun `релей в паузе уходит в конец, но не пропадает`() {
        health.recordFailure(a, RelayFailure.UNREACHABLE)
        assertEquals(listOf("b", "c", "a"), order("c1", "a", "b", "c"))
    }

    @Test
    fun `несколько релеёв в паузе — живые первыми, остальные по приоритету`() {
        health.recordFailure(a, RelayFailure.UNREACHABLE)
        health.recordFailure(c, RelayFailure.UNREACHABLE)
        assertEquals(listOf("b", "a", "c"), order("c1", "a", "b", "c"))
    }

    @Test
    fun `все в паузе — пробуем всех по приоритету, не сдаёмся`() {
        health.recordFailure(a, RelayFailure.UNREACHABLE)
        health.recordFailure(b, RelayFailure.AUTH)
        health.recordFailure(c, RelayFailure.TIMEOUT)
        assertEquals(listOf("a", "b", "c"), order("c1", "a", "b", "c"))
    }

    @Test
    fun `после паузы релей возвращается на своё место по приоритету`() {
        health.recordFailure(a, RelayFailure.UNREACHABLE)
        assertEquals(listOf("b", "a"), order("c1", "a", "b"))
        clock.advance(30_000)
        assertEquals(listOf("a", "b"), order("c1", "a", "b"))
    }

    // ---- залипание ----

    @Test
    fun `после доставки релей залипает за контактом и идёт первым`() {
        health.recordDelivery(b, "c1", 50, fallback = true)
        assertEquals(b, health.stickyFor("c1"))
        assertEquals(listOf("b", "a", "c"), order("c1", "a", "b", "c"))
    }

    @Test
    fun `залипание действует только для своего контакта`() {
        health.recordDelivery(b, "c1", 50, fallback = true)
        assertEquals(listOf("a", "b", "c"), order("c2", "a", "b", "c"))
        assertEquals(listOf("a", "b", "c"), order(null, "a", "b", "c"))
        assertNull(health.stickyFor("c2"))
    }

    @Test
    fun `залипание истекает через 10 минут и порядок возвращается к настройкам`() {
        health.recordDelivery(b, "c1", 50, fallback = true)
        clock.advance(10 * 60_000 - 1)
        assertEquals(b, health.stickyFor("c1"))
        clock.advance(1)
        assertNull(health.stickyFor("c1"))
        assertEquals(listOf("a", "b", "c"), order("c1", "a", "b", "c"))
    }

    @Test
    fun `новая доставка продлевает залипание`() {
        health.recordDelivery(b, "c1", 50, fallback = true)
        clock.advance(9 * 60_000)
        health.recordDelivery(b, "c1", 50, fallback = false)
        clock.advance(9 * 60_000)
        assertEquals(b, health.stickyFor("c1"))
    }

    @Test
    fun `доставка через другой релей переносит залипание`() {
        health.recordDelivery(b, "c1", 50, fallback = true)
        health.recordDelivery(c, "c1", 50, fallback = true)
        assertEquals(c, health.stickyFor("c1"))
    }

    @Test
    fun `ошибка залипшего релея снимает залипание у всех контактов`() {
        health.recordDelivery(b, "c1", 50, fallback = true)
        health.recordDelivery(b, "c2", 50, fallback = true)
        health.recordFailure(b, RelayFailure.UNREACHABLE)
        assertNull(health.stickyFor("c1"))
        assertNull(health.stickyFor("c2"))
        clock.advance(30_000) // пауза кончилась, но залипание не возвращается само
        assertEquals(listOf("a", "b", "c"), order("c1", "a", "b", "c"))
    }

    @Test
    fun `нейтральная ошибка залипание не снимает`() {
        health.recordDelivery(b, "c1", 50, fallback = true)
        health.recordFailure(b, RelayFailure.TARGET_OFFLINE)
        assertEquals(b, health.stickyFor("c1"))
    }

    @Test
    fun `залипший релей в паузе не идёт первым, а релей вне списка игнорируется`() {
        health.recordDelivery(b, "c1", 50, fallback = true)
        health.recordFailure(a, RelayFailure.UNREACHABLE)
        assertEquals(listOf("b", "c", "a"), order("c1", "b", "c", "a")) // b первый и так; a в паузе — в конец
        assertEquals(listOf("c", "a"), order("c1", "a", "c")) // b удалили из пула: залипание игнорируется, a всё ещё в паузе
    }

    // ---- метрики ----

    @Test
    fun `метрики — успехи, ошибки, запасные варианты, средняя задержка`() {
        health.recordDelivery(a, "c1", 100, fallback = false)
        health.recordDelivery(a, "c2", 200, fallback = true)
        health.recordDelivery(a, "c3", null, fallback = true)
        health.recordFailure(a, RelayFailure.TIMEOUT)
        health.recordFailure(a, RelayFailure.TARGET_OFFLINE) // не считается ошибкой

        val m = health.metrics(a)
        assertEquals(3L, m.successCount)
        assertEquals(1L, m.failureCount)
        assertEquals(2L, m.fallbackCount)
        assertEquals(150L, m.avgLatencyMs) // (100 + 200) / 2: попытка без замера не искажает среднее
        assertEquals(0, m.consecutiveFailures)
    }

    @Test
    fun `метрики неизвестного релея пустые, средняя задержка null до первой доставки`() {
        assertEquals(RelayMetrics(), health.metrics(a))
        health.recordFailure(a, RelayFailure.UNREACHABLE)
        assertNull(health.metrics(a).avgLatencyMs)
        assertEquals(1, health.metrics(a).consecutiveFailures)
    }

    @Test
    fun `отрицательная задержка игнорируется`() {
        health.recordDelivery(a, "c1", -5, fallback = false)
        assertNull(health.metrics(a).avgLatencyMs)
        assertEquals(1L, health.metrics(a).successCount)
    }

    @Test
    fun `allMetrics и retain`() {
        health.recordDelivery(a, "c1", 10, fallback = false)
        health.recordFailure(b, RelayFailure.TLS)
        health.recordDelivery(c, "c2", 10, fallback = false)
        assertEquals(setOf(a, b, c), health.allMetrics().keys)

        health.retain(setOf(a))
        assertEquals(setOf(a), health.allMetrics().keys)
        assertNull(health.stickyFor("c2")) // залипание на удалённый релей исчезло
        assertEquals(a, health.stickyFor("c1"))
        assertFalse(health.isCoolingDown(b))
    }
}
