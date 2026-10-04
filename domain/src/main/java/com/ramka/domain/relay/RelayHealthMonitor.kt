package com.ramka.domain.relay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Интервалы фоновой проверки релеёв. */
data class RelayMonitorConfig(
    /** Обычный режим: раз в 5 минут. */
    val intervalMillis: Long = 5 * 60_000,
    /** Режим экономии батареи: реже, раз в 15 минут. */
    val batterySaverIntervalMillis: Long = 15 * 60_000
)

/**
 * Фоновая проверка релеёв (шаг 2): пока пул включён и в нём есть рабочие записи, сразу после включения
 * и затем каждые 5 минут (15 в режиме экономии батареи) проверяет каждый релей ([RelayChecker]: TLS, токен,
 * протокол — без регистрации, рабочие подключения не затрагиваются) и сообщает результат в
 * [PoolRelayProvider]. Благодаря этому экран показывает актуальный статус, а релей, который ожил, возвращается
 * в очередь, не дожидаясь отправки сообщения.
 *
 * Пул выключен или пуст — проверок нет вообще (к серверам приложение не обращается). Правка набора релеёв
 * запускает проверку заново; перестановка без смены набора — нет.
 */
class RelayHealthMonitor(
    private val source: RelayPoolSource,
    private val provider: PoolRelayProvider,
    private val checker: RelayChecker,
    private val clock: RelayClock = RelayClock.System,
    private val config: RelayMonitorConfig = RelayMonitorConfig()
) {
    private data class Plan(val endpoints: Set<RelayEndpoint>, val batterySaver: Boolean)

    /** Запускает наблюдение за пулом в [scope]; отмена возвращённого [Job] останавливает проверки. */
    fun start(scope: CoroutineScope): Job = scope.launch {
        source.pool
            .map { pool -> Plan(pool.activeEntries.mapNotNull { it.endpointOrNull }.toSet(), pool.batterySaver) }
            .distinctUntilChanged()
            .collectLatest { plan ->
                provider.refresh()
                if (plan.endpoints.isEmpty()) return@collectLatest
                while (true) {
                    checkAll(plan.endpoints)
                    delay(if (plan.batterySaver) config.batterySaverIntervalMillis else config.intervalMillis)
                }
            }
    }

    /** Проверяет все релеи параллельно и сообщает результаты провайдеру. Можно вызвать вручную («проверить сейчас»). */
    suspend fun checkAll(endpoints: Collection<RelayEndpoint>) = coroutineScope {
        endpoints.map { endpoint -> async { checkOne(endpoint) } }.awaitAll()
        Unit
    }

    private suspend fun checkOne(endpoint: RelayEndpoint) {
        val started = clock.nowMillis()
        val result = try {
            checker.check(endpoint)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            RelayCheckResult.Failed(RelayFailure.INTERNAL) // проверка не должна ронять наблюдение
        }
        provider.reportCheck(endpoint, result, (clock.nowMillis() - started).coerceAtLeast(0))
    }
}
