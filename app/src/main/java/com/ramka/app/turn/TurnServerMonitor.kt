package com.ramka.app.turn

import com.ramka.app.data.TurnServerEntry
import com.ramka.app.data.TurnUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Глобальный монитор доступности TURN для индикатора подключения (жёлтый «только звонки»).
 *
 * [isTurnReady] равен true ТОЛЬКО если последняя проверка нашла хотя бы один рабочий ВКЛЮЧЁННЫЙ сервер.
 * «Неизвестно» не равно «доступно», поэтому значение false в таких случаях:
 *  - проверка ещё не завершилась (старт подписки, смена списка серверов);
 *  - список пуст или серверы выключены;
 *  - сеть сменилась (Wi-Fi ↔ мобильная, потеря связи): прежний результат недействителен, значение сразу
 *    сбрасывается и проверка идёт заново;
 *  - все серверы недоступны либо проверка завершилась ошибкой;
 *  - подписчиков не осталось: через [stopTimeoutMillis] проверки останавливаются, а значение сбрасывается в false
 *    (старое true не «воскресает» при возврате на экран).
 *
 * Пока есть подписчик, проверка повторяется каждые [refreshMillis] (все включённые серверы параллельно, у
 * каждой проверки свой тайм-аут в [TurnServerChecker]). Экран списка TURN-серверов проверяет независимо.
 *
 * @param servers список серверов из настроек
 * @param check проверка одного сервера
 * @param networkChanges события сети: первое значение считается начальным, каждое следующее запускает перепроверку
 */
class TurnServerMonitor(
    servers: Flow<List<TurnServerEntry>>,
    private val check: suspend (TurnUrl) -> TurnServerStatus,
    private val networkChanges: Flow<Any>,
    appScope: CoroutineScope,
    private val refreshMillis: Long = REFRESH_MS,
    stopTimeoutMillis: Long = STOP_TIMEOUT_MS
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val isTurnReady: StateFlow<Boolean> = servers
        .map { list -> list.filter { it.isEnabled }.mapNotNull { TurnUrl.parse(it.url) }.toSet() }
        .distinctUntilChanged()
        .flatMapLatest { urls -> readiness(urls) }
        .stateIn(
            appScope,
            SharingStarted.WhileSubscribed(stopTimeoutMillis, replayExpirationMillis = 0),
            false
        )

    private fun readiness(urls: Set<TurnUrl>): Flow<Boolean> = channelFlow {
        send(false) // до первой проверки статус неизвестен: не жёлтый
        if (urls.isEmpty()) return@channelFlow

        val recheck = Channel<Unit>(Channel.CONFLATED)
        launch { networkChanges.drop(1).collect { recheck.trySend(Unit) } }

        while (true) {
            send(anyWorking(urls))
            val networkChanged = withTimeoutOrNull(refreshMillis) { recheck.receive(); true } ?: false
            if (networkChanged) send(false) // результат прошлой сети недействителен, пока не проверим заново
        }
    }

    private suspend fun anyWorking(urls: Set<TurnUrl>): Boolean = coroutineScope {
        urls.map { url -> async { safeCheck(url) } }.awaitAll().any { it == TurnServerStatus.WORKING }
    }

    private suspend fun safeCheck(url: TurnUrl): TurnServerStatus = try {
        check(url)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        TurnServerStatus.UNREACHABLE // сбой проверки не должен давать «доступно»
    }

    companion object {
        const val REFRESH_MS = 30_000L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
