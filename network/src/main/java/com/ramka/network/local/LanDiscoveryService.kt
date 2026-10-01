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
 * Вещание и поиск включаются и выключаются тумблером в настройках (этап 2.5) через
 * `LanPresenceCoordinator`: [registerService]/[unregisterService] и подписка на [discoverPeers].
 *
 * Согласно п. 3.6, вариант A (ручной ввод IP:port из QR) — приоритетный способ
 * обнаружения; NSD — опциональное дополнение (вариант B), не обязательное для
 * MVP этапа 1, если начнёт требовать лишние разрешения на конкретных версиях Android.
 */
class LanDiscoveryService(context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val lock = Any()
    private var registration: NsdManager.RegistrationListener? = null
    private var registered = false
    private var unregisterWhenRegistered = false

    /**
     * Вещает сервис в локальной сети. Идемпотентно: пока регистрация активна или ещё ожидается,
     * повторный вызов ничего не создаёт (и отменяет отложенное снятие, если оно было запрошено).
     */
    fun registerService(port: Int, instanceName: String) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = instanceName
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                val unregisterNow = synchronized(lock) {
                    if (registration !== this) return@synchronized false
                    registered = true
                    if (unregisterWhenRegistered) {
                        // Тумблер выключили, пока регистрация ещё была в пути.
                        unregisterWhenRegistered = false
                        registration = null
                        registered = false
                        true
                    } else false
                }
                if (unregisterNow) runCatching { nsdManager.unregisterService(this) }
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                synchronized(lock) {
                    if (registration !== this) return
                    registration = null
                    registered = false
                    unregisterWhenRegistered = false
                }
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        synchronized(lock) {
            if (registration != null) {
                unregisterWhenRegistered = false
                return
            }
            registration = listener
            registered = false
            unregisterWhenRegistered = false
        }
        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            synchronized(lock) { if (registration === listener) registration = null }
        }
    }

    /** Снимает вещание. Безопасно вызывать, если сервис не зарегистрирован или регистрация ещё в пути. */
    fun unregisterService() {
        val listener = synchronized(lock) {
            val current = registration ?: return
            if (!registered) {
                unregisterWhenRegistered = true // снимем в onServiceRegistered
                return
            }
            registration = null
            registered = false
            current
        }
        runCatching { nsdManager.unregisterService(listener) }
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
