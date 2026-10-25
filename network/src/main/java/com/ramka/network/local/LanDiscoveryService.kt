package com.ramka.network.local

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class LanDiscoveryService(context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val multicast = MulticastLockHolder(context.applicationContext)

    private val lock = Any()
    private var registrationHoldsMulticast = false
    private var registration: NsdManager.RegistrationListener? = null
    private var registered = false
    private var unregisterWhenRegistered = false

    fun registerService(port: Int, instanceName: String) {
        Log.d("ramka", "NSD: Попытка регистрации сервиса: $instanceName на порту $port")
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = instanceName
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.d("ramka", "NSD: Успешно зарегистрирован в сети как ${info.serviceName}")
                val unregisterNow = synchronized(lock) {
                    if (registration !== this) return@synchronized false
                    registered = true
                    if (unregisterWhenRegistered) {
                        unregisterWhenRegistered = false
                        registration = null
                        registered = false
                        true
                    } else false
                }
                if (unregisterNow) {
                    runCatching { nsdManager.unregisterService(this) }
                    releaseRegistrationMulticast()
                }
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e("ramka", "NSD: ОШИБКА регистрации сервиса, код: $errorCode")
                synchronized(lock) {
                    if (registration !== this) return
                    registration = null
                    registered = false
                    unregisterWhenRegistered = false
                }
                releaseRegistrationMulticast()
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Log.d("ramka", "NSD: Сервис успешно снят с регистрации")
            }
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e("ramka", "NSD: ОШИБКА снятия с регистрации, код: $errorCode")
            }
        }
        synchronized(lock) {
            if (registration != null) {
                unregisterWhenRegistered = false
                return
            }
            registration = listener
            registered = false
            unregisterWhenRegistered = false
            registrationHoldsMulticast = true
        }
        multicast.acquire()
        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.e("ramka", "NSD: Исключение при регистрации: ${e.message}")
            synchronized(lock) { if (registration === listener) registration = null }
            releaseRegistrationMulticast()
        }
    }

    fun unregisterService() {
        Log.d("ramka", "NSD: Запрос на снятие с регистрации")
        val listener = synchronized(lock) {
            val current = registration ?: return
            if (!registered) {
                unregisterWhenRegistered = true
                return
            }
            registration = null
            registered = false
            current
        }
        runCatching { nsdManager.unregisterService(listener) }
        releaseRegistrationMulticast()
    }

    private fun releaseRegistrationMulticast() {
        val release = synchronized(lock) {
            val held = registrationHoldsMulticast
            registrationHoldsMulticast = false
            held
        }
        if (release) multicast.release()
    }

    fun discoverPeers(): Flow<DiscoveredPeer> = callbackFlow {
        Log.d("ramka", "NSD: Запуск поиска устройств в сети...")
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d("ramka", "NSD: Поиск успешно запущен")
            }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("ramka", "NSD: ОШИБКА запуска поиска, код: $errorCode")
                close()
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("ramka", "NSD: ОШИБКА остановки поиска, код: $errorCode")
            }
            override fun onDiscoveryStopped(serviceType: String) {
                Log.d("ramka", "NSD: Поиск остановлен")
            }
            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d("ramka", "NSD: Устройство потеряно: ${service.serviceName}")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d("ramka", "NSD: Найдено устройство: ${service.serviceName}. Начинаем разрешение...")
                nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        Log.e("ramka", "NSD: ОШИБКА разрешения устройства, код: $errorCode")
                    }
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val host = info.host.hostAddress
                        Log.d("ramka", "NSD: Устройство успешно разрешено! IP: $host, Порт: ${info.port}")
                        if (host != null) {
                            trySend(DiscoveredPeer(host = host, port = info.port))
                        }
                    }
                })
            }
        }
        multicast.acquire()
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.e("ramka", "NSD: Исключение при запуске поиска: ${e.message}")
            multicast.release()
            throw e
        }
        awaitClose {
            try {
                nsdManager.stopServiceDiscovery(listener)
            } finally {
                multicast.release()
            }
        }
    }

    companion object {
        private const val SERVICE_TYPE = "_ramka._tcp."
    }
}

private class MulticastLockHolder(context: Context) {
    private val lock: WifiManager.MulticastLock? = try {
        (context.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.createMulticastLock("ramka_mdns_lock")
            ?.apply { setReferenceCounted(false) }
    } catch (e: Exception) {
        Log.w("ramka", "MulticastLock недоступен: ${e.javaClass.simpleName}")
        null
    }
    private var users = 0

    @Synchronized
    fun acquire() {
        users++
        if (users != 1) return
        try {
            lock?.acquire()
            Log.d("ramka", "MulticastLock получен")
        } catch (e: Exception) {
            Log.w("ramka", "MulticastLock: не удалось получить: ${e.javaClass.simpleName}")
        }
    }

    @Synchronized
    fun release() {
        if (users == 0) return
        users--
        if (users != 0) return
        try {
            if (lock?.isHeld == true) {
                lock.release()
                Log.d("ramka", "MulticastLock освобождён")
            }
        } catch (e: Exception) {
            Log.w("ramka", "MulticastLock: не удалось освободить: ${e.javaClass.simpleName}")
        }
    }
}

data class DiscoveredPeer(val host: String, val port: Int)