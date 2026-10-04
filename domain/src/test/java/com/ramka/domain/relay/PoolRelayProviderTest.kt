package com.ramka.domain.relay

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoolRelayProviderTest {

    private val clock = FakeClock()

    private fun setup(pool: RelayPool): Pair<PoolRelayProvider, FakePoolSource> {
        val source = FakePoolSource(pool)
        return PoolRelayProvider(source, clock) to source
    }

    private fun ep(id: String) = poolEntry(id).endpointOrNull!!

    private fun PoolRelayProvider.own() = runBlocking { ownRelays.first() }.map { it.address.host.substringBefore('.') }

    private fun PoolRelayProvider.sendOrder(contact: String = "c1") =
        runBlocking { relaysFor(testContact(contact)) }.map { it.address.host.substringBefore('.') }

    // ---- ownRelays (приём) ----

    @Test
    fun `приём идёт на всех рабочих релеях включённого пула, в порядке приоритета`() {
        val (p, _) = setup(poolOf("a", "b", "c"))
        assertEquals(listOf("a", "b", "c"), p.own())
    }

    @Test
    fun `выключенный или пустой пул — приёма нет`() {
        assertEquals(emptyList<String>(), setup(poolOf("a", "b", enabled = false)).first.own())
        assertEquals(emptyList<String>(), setup(RelayPool(enabled = true)).first.own())
    }

    @Test
    fun `неполные записи в приём не попадают`() {
        val pool = RelayPool(enabled = true, entries = listOf(poolEntry("a"), poolEntry("b", token = ""), poolEntry("c")))
        assertEquals(listOf("a", "c"), setup(pool).first.own())
    }

    @Test
    fun `endpoint содержит токен и пин записи`() {
        val pin = "ab".repeat(32)
        val (p, _) = setup(RelayPool(enabled = true, entries = listOf(poolEntry("a", pin = pin))))
        val e = runBlocking { p.ownRelays.first() }.single()
        assertEquals(RelayEndpoint(addr("a"), POOL_TEST_TOKEN, pin), e)
    }

    @Test
    fun `изменения пула отражаются в приёме`() {
        val (p, source) = setup(poolOf("a", enabled = false))
        assertEquals(emptyList<String>(), p.own())
        source.state.value = poolOf("a", "b")
        assertEquals(listOf("a", "b"), p.own())
        source.state.value = poolOf("b", "a")
        assertEquals(listOf("b", "a"), p.own()) // перестановку транспорт разбирает как набор (см. тест транспорта)
    }

    // ---- режим экономии батареи ----

    @Test
    fun `экономия батареи — приём только на первом релее`() {
        val (p, _) = setup(poolOf("a", "b", "c", batterySaver = true))
        assertEquals(listOf("a"), p.own())
    }

    @Test
    fun `экономия батареи — при ошибке первого приём переходит на следующий, после паузы возвращается`() {
        val (p, _) = setup(poolOf("a", "b", "c", batterySaver = true))
        p.report(ep("a"), RelayFailure.UNREACHABLE)
        assertEquals(listOf("b"), p.own())
        p.report(ep("b"), RelayFailure.UNREACHABLE)
        assertEquals(listOf("c"), p.own())

        clock.advance(30_000)
        p.refresh() // истёкшие паузы учитываются при пересчёте (его запускает фоновая проверка)
        assertEquals(listOf("a"), p.own())
    }

    @Test
    fun `экономия батареи — если в паузе все, приём остаётся на первом`() {
        val (p, _) = setup(poolOf("a", "b", batterySaver = true))
        p.report(ep("a"), RelayFailure.AUTH)
        p.report(ep("b"), RelayFailure.AUTH)
        assertEquals(listOf("a"), p.own())
    }

    @Test
    fun `экономия батареи не влияет на пул из одного релея, а без неё приём на всех несмотря на паузы`() {
        assertEquals(listOf("a"), setup(poolOf("a", batterySaver = true)).first.own())
        val (p, _) = setup(poolOf("a", "b", batterySaver = false))
        p.report(ep("a"), RelayFailure.UNREACHABLE)
        assertEquals(listOf("a", "b"), p.own())
    }

    @Test
    fun `переключатель экономии применяется сразу`() {
        val (p, source) = setup(poolOf("a", "b"))
        assertEquals(listOf("a", "b"), p.own())
        source.state.value = source.state.value.copy(batterySaver = true)
        assertEquals(listOf("a"), p.own())
    }

    // ---- relaysFor (отправка) ----

    @Test
    fun `отправка — порядок по приоритету, пустой пул — пустой список`() {
        assertEquals(listOf("a", "b", "c"), setup(poolOf("a", "b", "c")).first.sendOrder())
        assertEquals(emptyList<String>(), setup(poolOf("a", enabled = false)).first.sendOrder())
        assertEquals(emptyList<String>(), setup(RelayPool(enabled = true)).first.sendOrder())
        assertEquals(listOf("a"), setup(poolOf("a")).first.sendOrder())
    }

    @Test
    fun `отправка — релей после ошибки уходит в конец, но остаётся в списке`() {
        val (p, _) = setup(poolOf("a", "b", "c"))
        p.report(ep("a"), RelayFailure.UNREACHABLE)
        assertEquals(listOf("b", "c", "a"), p.sendOrder())
        clock.advance(30_000)
        assertEquals(listOf("a", "b", "c"), p.sendOrder())
    }

    @Test
    fun `отправка — TARGET_OFFLINE не штрафует, report(null) снимает паузу`() {
        val (p, _) = setup(poolOf("a", "b"))
        p.report(ep("a"), RelayFailure.TARGET_OFFLINE)
        assertEquals(listOf("a", "b"), p.sendOrder())
        p.report(ep("a"), RelayFailure.TIMEOUT)
        assertEquals(listOf("b", "a"), p.sendOrder())
        p.report(ep("a"), null)
        assertEquals(listOf("a", "b"), p.sendOrder())
    }

    @Test
    fun `отправка — залипание после доставки только для этого контакта`() {
        val (p, _) = setup(poolOf("a", "b", "c"))
        p.reportDelivery(ep("b"), testContact("alice"), 120, fallback = true)
        assertEquals(listOf("b", "a", "c"), p.sendOrder("alice"))
        assertEquals(listOf("a", "b", "c"), p.sendOrder("bob"))
        clock.advance(10 * 60_000)
        assertEquals(listOf("a", "b", "c"), p.sendOrder("alice")) // срок залипания вышел
    }

    @Test
    fun `отправка — изменение приоритета в настройках применяется сразу`() {
        val (p, source) = setup(poolOf("a", "b", "c"))
        source.state.value = poolOf("c", "a", "b")
        assertEquals(listOf("c", "a", "b"), p.sendOrder())
    }

    @Test
    fun `удалённый из пула релей не возвращается из памяти здоровья`() {
        val (p, source) = setup(poolOf("a", "b"))
        p.report(ep("a"), RelayFailure.UNREACHABLE)
        source.state.value = poolOf("b")
        p.refresh()
        source.state.value = poolOf("a", "b") // тот же адрес добавлен заново: прежняя пауза забыта
        p.refresh()
        assertEquals(listOf("a", "b"), p.sendOrder())
    }

    // ---- состояния связи ----

    @Test
    fun `состояния связи по адресам`() {
        val (p, _) = setup(poolOf("a", "b"))
        assertTrue(p.connectionStates.value.isEmpty())

        p.reportLink(ep("a"), RelayLinkState.Connecting)
        p.reportLink(ep("b"), RelayLinkState.Connecting)
        assertEquals(RelayLinkState.Connecting, p.connectionStates.value[addr("a")])

        p.reportLink(ep("a"), RelayLinkState.Connected)
        p.reportLink(ep("b"), RelayLinkState.Failed(RelayFailure.UNREACHABLE, 1_000))
        assertEquals(RelayLinkState.Connected, p.connectionStates.value[addr("a")])
        assertEquals(RelayLinkState.Failed(RelayFailure.UNREACHABLE, 1_000), p.connectionStates.value[addr("b")])
    }

    @Test
    fun `ошибка регистрации даёт паузу для отправки, успешная регистрация её снимает`() {
        val (p, _) = setup(poolOf("a", "b"))
        p.reportLink(ep("a"), RelayLinkState.Failed(RelayFailure.UNREACHABLE, 1_000))
        assertEquals(listOf("b", "a"), p.sendOrder())
        p.reportLink(ep("a"), RelayLinkState.Connected)
        assertEquals(listOf("a", "b"), p.sendOrder())
    }

    @Test
    fun `состояния исчезают при выключении пула и при удалении релея`() {
        val (p, source) = setup(poolOf("a", "b"))
        p.reportLink(ep("a"), RelayLinkState.Connected)
        p.reportLink(ep("b"), RelayLinkState.Connected)

        source.state.value = poolOf("a")
        p.refresh()
        assertEquals(setOf(addr("a")), p.connectionStates.value.keys)

        source.state.value = poolOf("a", enabled = false)
        p.refresh()
        assertTrue(p.connectionStates.value.isEmpty())
    }

    @Test
    fun `lastFailure — последняя ошибка, null при успехе, TARGET_OFFLINE не ошибка`() {
        val (p, _) = setup(poolOf("a"))
        assertNull(p.lastFailure.value)
        p.report(ep("a"), RelayFailure.AUTH)
        assertEquals(RelayFailure.AUTH, p.lastFailure.value)
        p.report(ep("a"), RelayFailure.TARGET_OFFLINE)
        assertNull(p.lastFailure.value)
        p.reportLink(ep("a"), RelayLinkState.Failed(RelayFailure.TLS, null))
        assertEquals(RelayFailure.TLS, p.lastFailure.value)
        p.reportDelivery(ep("a"), testContact(), 10, fallback = false)
        assertNull(p.lastFailure.value)
    }

    // ---- результаты фоновой проверки ----

    @Test
    fun `проверка без регистрации даёт Reachable`() {
        val (p, _) = setup(poolOf("a"))
        p.reportCheck(ep("a"), RelayCheckResult.Ok, 42)
        assertEquals(RelayLinkState.Reachable(42), p.connectionStates.value[addr("a")])
    }

    @Test
    fun `проверка не портит живую регистрацию и подключение`() {
        val (p, _) = setup(poolOf("a", "b"))
        p.reportLink(ep("a"), RelayLinkState.Connected)
        p.reportLink(ep("b"), RelayLinkState.Connecting)
        p.reportCheck(ep("a"), RelayCheckResult.Failed(RelayFailure.BUSY), 10)
        p.reportCheck(ep("b"), RelayCheckResult.Ok, 10)
        assertEquals(RelayLinkState.Connected, p.connectionStates.value[addr("a")])
        assertEquals(RelayLinkState.Connecting, p.connectionStates.value[addr("b")])
    }

    @Test
    fun `неудачная проверка даёт Failed и паузу, удачная возвращает релей`() {
        val (p, _) = setup(poolOf("a", "b"))
        p.reportCheck(ep("a"), RelayCheckResult.Failed(RelayFailure.AUTH), 5)
        assertEquals(RelayLinkState.Failed(RelayFailure.AUTH), p.connectionStates.value[addr("a")])
        assertEquals(listOf("b", "a"), p.sendOrder())

        p.reportCheck(ep("a"), RelayCheckResult.Ok, 7)
        assertEquals(RelayLinkState.Reachable(7), p.connectionStates.value[addr("a")])
        assertEquals(listOf("a", "b"), p.sendOrder())
    }

    @Test
    fun `проверка после ошибки регистрации заменяет Failed на Reachable`() {
        val (p, _) = setup(poolOf("a"))
        p.reportLink(ep("a"), RelayLinkState.Failed(RelayFailure.UNREACHABLE, 5_000))
        p.reportCheck(ep("a"), RelayCheckResult.Ok, 9)
        assertEquals(RelayLinkState.Reachable(9), p.connectionStates.value[addr("a")])
    }

    // ---- метрики ----

    @Test
    fun `метрики обновляются после доставок и ошибок и пропадают вместе с релеем`() {
        val (p, source) = setup(poolOf("a", "b"))
        assertTrue(p.metrics.value.isEmpty())

        p.reportDelivery(ep("a"), testContact(), 100, fallback = false)
        p.reportDelivery(ep("b"), testContact("other"), 300, fallback = true)
        p.report(ep("b"), RelayFailure.TIMEOUT)

        assertEquals(1L, p.metrics.value[addr("a")]!!.successCount)
        assertEquals(100L, p.metrics.value[addr("a")]!!.avgLatencyMs)
        assertEquals(1L, p.metrics.value[addr("b")]!!.fallbackCount)
        assertEquals(1L, p.metrics.value[addr("b")]!!.failureCount)

        source.state.value = poolOf("a")
        p.refresh()
        assertEquals(setOf(addr("a")), p.metrics.value.keys)
    }
}
