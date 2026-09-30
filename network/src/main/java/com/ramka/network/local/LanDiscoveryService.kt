package com.ramka.network.local

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Регистрация и обнаружение сервиса ramka в локальной сети через NSD (mDNS),
 * без запроса разрешений на геолокацию (см. запрет 5.2 — ACCESS_*_LOCATION запрещены).
 *
 * Согласно п. 3.6, вариант A (ручной ввод IP:port из QR) — приоритетный способ
 * обнаружения; NSD — опциональное дополнение (вариант B), не обязательное для
 * MVP этапа 1, если начнёт требовать лишние разрешения на конкретных версиях Android.
 */
class LanDiscoveryService(context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    fun registerService(port: Int, instanceName: String) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = instanceName
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        })
    }

    /** Поток найденных экземпляров сервиса ramka в локальной сети. */
    fun discoverPeers(): Flow<DiscoveredPeer> = callbackFlow {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { close() }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onServiceLost(service: NsdServiceInfo) = Unit

            override fun onServiceFound(service: NsdServiceInfo) {
                nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        trySend(DiscoveredPeer(host = info.host.hostAddress ?: return, port = info.port))
                    }
                })
            }
        }
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose { nsdManager.stopServiceDiscovery(listener) }
    }

    companion object {
        private const val SERVICE_TYPE = "_ramka._tcp."
    }
}

data class DiscoveredPeer(val host: String, val port: Int)
