package com.ramka.app.discovery

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.security.SecureRandom

/**
 * Включает и выключает всё, что приложение делает в mDNS (этап 2.5), по флагу [enabled]:
 *
 * - ВКЛ: регистрирует сервис `_ramka._tcp.` под новым случайным именем и подписывается на
 *   обнаружение других экземпляров; каждое найденное устройство — повод вызвать
 *   [onPeerDiscovered] (разбор Outbox);
 * - ВЫКЛ: подписка на обнаружение отменяется, регистрация снимается. mDNS-трафика приложение
 *   больше не создаёт (ни объявлений, ни запросов).
 *
 * `collectLatest` отменяет блок предыдущего значения и дожидается его завершения, поэтому
 * `unregister` всегда выполняется раньше следующего `register`, а быстрые переключения
 * не оставляют двух регистраций одновременно. Ошибка обнаружения или обработчика пира не
 * останавливает ни вещание, ни наблюдение за тумблером.
 *
 * Функции регистрации и обнаружения переданы лямбдами, чтобы класс не зависел от Android
 * (в приложении это `LanDiscoveryService`).
 */
class LanPresenceCoordinator(
    private val enabled: StateFlow<Boolean>,
    private val register: (instanceName: String) -> Unit,
    private val unregister: () -> Unit,
    private val discoverPeers: () -> Flow<Any?>,
    private val onPeerDiscovered: suspend () -> Unit,
    private val newInstanceName: () -> String = ::randomInstanceName
) {
    fun start(scope: CoroutineScope): Job = scope.launch {
        enabled.collectLatest { on ->
            if (!on) return@collectLatest
            try {
                runCatchingNonCancellation { register(newInstanceName()) }
                try {
                    discoverPeers().collect {
                        runCatchingNonCancellation { onPeerDiscovered() }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Поиск не стартовал или оборвался: вещание продолжается, как и раньше.
                }
                awaitCancellation() // держим регистрацию, пока тумблер не выключат
            } finally {
                unregister()
            }
        }
    }

    private inline fun runCatchingNonCancellation(block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // не роняем наблюдение за тумблером
        }
    }

    companion object {
        /**
         * Случайное эфемерное имя экземпляра: не имя устройства и не пользователя, нигде не
         * сохраняется, при каждом включении новое.
         */
        fun randomInstanceName(): String = "ramka-" + ByteArray(4)
            .also(SecureRandom()::nextBytes)
            .joinToString("") { "%02x".format(it) }
    }
}
