package com.ramka.app.background

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.ramka.app.notifications.RamkaNotifications

/**
 * Постоянный foreground-сервис (ЭТАП B.8, под-тумблер «Постоянный сервис для надёжности»).
 *
 * Его единственная роль — удерживать ПРОЦЕСС живым под агрессивными OEM-ограничениями:
 * в процессе (Application-scope) уже живут LAN-приёмник входящих соединений
 * (SecureLanChannel) и mDNS-обнаружение — с убитым процессом приёма нет вовсе.
 *
 * ПРИВАТНОСТЬ (требование B.8.4): сервис намеренно ничего не хранит и ничего не делает
 * сам — ни полей с данными, ни доступа к БД/outbox, ни ключей. Ключевой материал живёт
 * только там же, где и раньше (KeyManager/поток рукопожатия), отдельного кэша в сервисе нет.
 *
 * Тип — specialUse (см. DEVIATIONS.md, «ЭТАП B.8»): dataSync имеет лимит 6 ч из 24 на
 * Android 15, у specialUse лимита нет.
 */
class PersistentDeliveryService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        RamkaNotifications.ensureServiceChannel(this)
        val notification = RamkaNotifications.buildServiceNotification(this)
        if (Build.VERSION.SDK_INT >= 34) {
            // API 34+: тип обязателен и должен совпадать с объявленным в манифесте.
            startForeground(SERVICE_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            // API < 34: тип берётся из манифеста, отдельный параметр не нужен.
            startForeground(SERVICE_NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val SERVICE_NOTIFICATION_ID = 2 // 1 занят уведомлением о сообщении
    }
}
