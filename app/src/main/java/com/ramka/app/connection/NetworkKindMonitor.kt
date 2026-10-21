package com.ramka.app.connection

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Реактивное наблюдение за видом сети. Только чтение: существующий LocalNetworkDetector (опрос по
 * требованию, при ошибках отвечает «да») не используется и не меняется. Нужно разрешение
 * ACCESS_NETWORK_STATE — оно уже в манифесте.
 */
class NetworkKindMonitor(context: Context) {

    private val manager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    fun observe(): Flow<NetworkKind> = callbackFlow {
        val cm = manager
        if (cm == null) {
            trySend(NetworkKind.OTHER)
            awaitClose { }
            return@callbackFlow
        }
        trySend(kindOf(cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }))

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(kindOf(caps))
            }

            override fun onLost(network: Network) {
                trySend(NetworkKind.NONE)
            }
        }
        try {
            cm.registerDefaultNetworkCallback(callback)
        } catch (_: SecurityException) {
            // Без права на состояние сети остаёмся при начальном значении.
        }
        awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()

    private fun kindOf(caps: NetworkCapabilities?): NetworkKind = when {
        caps == null -> NetworkKind.NONE
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkKind.LOCAL
        else -> NetworkKind.OTHER
    }
}
