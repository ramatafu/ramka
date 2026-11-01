package com.ramka.network.ygg

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

/**
 * Работа с системным интерфейсом Yggdrasil (его поднимает отдельное приложение Yggdrasil или системный
 * VPN/TUN; сама Ramka узел Yggdrasil не содержит). Адреса сети Yggdrasil — это IPv6 из `200::/7`.
 */
object YggdrasilNetwork {
    const val DEFAULT_PROBE_TIMEOUT_MS = 3_000

    /** Адрес из диапазона Yggdrasil `200::/7`: первый байт 0x02 или 0x03. */
    fun isYggdrasilAddress(address: InetAddress): Boolean {
        if (address !is Inet6Address) return false
        val first = address.address[0].toInt() and 0xFF
        return first == 0x02 || first == 0x03
    }

    /** Есть ли на устройстве поднятый интерфейс с адресом Yggdrasil (перебор интерфейсов, без запросов в сеть). */
    fun hasActiveInterface(): Boolean = try {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().any { networkInterface ->
            networkInterface.isUp && !networkInterface.isLoopback &&
                networkInterface.inetAddresses.toList().any { isYggdrasilAddress(it) }
        }
    } catch (e: Exception) {
        false
    }

    /**
     * Реальная связность: TCP-соединение с портом Ramka собеседника [port] по адресу [ipv6].
     * Это и есть проверка «Ramka там слушает и сеть Yggdrasil до него доходит». ICMP-ping приложениям
     * Android недоступен (нет raw-сокетов), а `InetAddress.isReachable` без root опрашивает порт 7 и
     * ненадёжен. Соединение закрывается сразу, рукопожатие не начинается.
     */
    suspend fun isReachable(ipv6: String, port: Int, timeoutMs: Int = DEFAULT_PROBE_TIMEOUT_MS): Boolean =
        withContext(Dispatchers.IO) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(InetAddress.getByName(ipv6), port), timeoutMs)
                    true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
        }
}
