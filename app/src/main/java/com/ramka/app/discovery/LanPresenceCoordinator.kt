package com.ramka.app.discovery

import com.ramka.network.local.DiscoveredPeer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.security.SecureRandom

class LanPresenceCoordinator(
    private val enabled: StateFlow<Boolean>,
    private val register: (instanceName: String) -> Unit,
    private val unregister: () -> Unit,
    private val discoverPeers: () -> Flow<DiscoveredPeer>,
    private val onPeerDiscovered: suspend (DiscoveredPeer) -> Unit,
    private val newInstanceName: () -> String = ::randomInstanceName
) {
    fun start(scope: CoroutineScope): Job = scope.launch {
        enabled.collectLatest { on ->
            if (!on) return@collectLatest
            try {
                runCatchingNonCancellation { register(newInstanceName()) }
                try {
                    discoverPeers().collect { peer ->
                        runCatchingNonCancellation { onPeerDiscovered(peer) }
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
        fun randomInstanceName(): String = "ramka-" + ByteArray(4)
            .also(SecureRandom()::nextBytes)
            .joinToString("") { "%02x".format(it) }
    }
}