package com.ramka.app.relay

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Подключено ли устройство к локальной сети (Wi-Fi или Ethernet): для выбора «LAN или relay». */
interface LocalNetworkDetector {
    fun isOnLocalNetwork(): Boolean
}

/**
 * Определяет по активной сети ([ConnectivityManager]): есть транспорт Wi-Fi или Ethernet.
 * Мобильная сеть и VPN поверх неё — не локальная сеть. Ошибки и отсутствие сети трактуются как «да»,
 * чтобы по умолчанию вести себя как до этапа 3 (сначала LAN).
 */
class AndroidLocalNetworkDetector(context: Context) : LocalNetworkDetector {
    private val manager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    override fun isOnLocalNetwork(): Boolean {
        return try {
            val cm = manager ?: return true
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return true
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (_: SecurityException) {
            true
        }
    }
}