package com.ramka.app.outbox

import android.util.Log
import com.ramka.domain.relay.PoolRelayProvider
import com.ramka.domain.relay.RelayLinkState
import com.ramka.domain.usecase.AutoResendFailedFilesUseCase
import com.ramka.domain.usecase.ProcessOutboxUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Момент «подключился первый релей»: переход «нет подключённых релеев» -> «есть хотя бы один», после небольшой
 * паузы ([debounceMillis]), чтобы мигание связи не давало серию запусков.
 */
@OptIn(FlowPreview::class)
internal fun relayRestoredEvents(relayConnected: Flow<Boolean>, debounceMillis: Long = RELAY_RESTORED_DEBOUNCE_MS): Flow<Unit> =
    relayConnected
        .distinctUntilChanged()
        .debounce(debounceMillis)
        .filter { it }
        .map { }

internal const val RELAY_RESTORED_DEBOUNCE_MS = 2_000L

/**
 * Автоповтор неотправленного при появлении связи, без участия пользователя и без уведомлений:
 *  - текстовые сообщения: все записи Outbox сразу, не дожидаясь backoff ([ProcessOutboxUseCase.processAll]);
 *  - файлы и голосовые в статусе FAILED до 5 МБ: не больше 3 автопопыток ([AutoResendFailedFilesUseCase]).
 *
 * События: подключился первый релей (см. [relayRestoredEvents]) и найден LAN-пир ([onConnectionAvailable]).
 * Работает в скоупе приложения, поэтому и в фоне (пока жив процесс: фоновая доставка, сервис). Статусы в чате
 * меняются сами через Flow из БД. Саму логику переподключения релея не затрагивает.
 */
@Singleton
class ConnectionRestoredResender @Inject constructor(
    relayProvider: PoolRelayProvider,
    private val processOutbox: ProcessOutboxUseCase,
    private val resendFiles: AutoResendFailedFilesUseCase
) {
    private val relayConnected: Flow<Boolean> =
        relayProvider.connectionStates.map { states -> states.values.any { it is RelayLinkState.Connected } }

    @Volatile private var scope: CoroutineScope? = null
    private val lastRunMillis = AtomicLong(0L)

    fun start(appScope: CoroutineScope) {
        scope = appScope
        appScope.launch {
            relayRestoredEvents(relayConnected).collect { resendAll() }
        }
    }

    /** Появился другой канал (найден пир в LAN). Слишком частые сигналы (mDNS повторяет находки) отбрасываются. */
    fun onConnectionAvailable() {
        val s = scope ?: return
        val now = System.currentTimeMillis()
        val last = lastRunMillis.get()
        if (now - last < MIN_INTERVAL_MS || !lastRunMillis.compareAndSet(last, now)) return
        s.launch { resendAll() }
    }

    private suspend fun resendAll() {
        lastRunMillis.set(System.currentTimeMillis())
        try {
            processOutbox.processAll(System.currentTimeMillis())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("ramka", "Автоповтор очереди не удался: ${e.javaClass.simpleName}")
        }
        try {
            resendFiles()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("ramka", "Автоповтор файлов не удался: ${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val MIN_INTERVAL_MS = 5_000L
    }
}
