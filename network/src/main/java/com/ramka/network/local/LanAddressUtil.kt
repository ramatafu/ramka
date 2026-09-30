package com.ramka.network.local

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Определяет собственный IPv4-адрес в локальной сети для вставки в QR-приглашение
 * (вариант A из п. 3.6 — ручной адрес имеет приоритет над mDNS).
 * Не использует разрешения на геолокацию.
 */
object LanAddressUtil {
    fun getLocalIpv4Address(): String? =
        NetworkInterface.getNetworkInterfaces()?.asSequence()
            ?.filter { !it.isLoopback && it.isUp }
            ?.flatMap { it.inetAddresses.asSequence() }
            ?.filterIsInstance<Inet4Address>()
            ?.firstOrNull()
            ?.hostAddress
}
