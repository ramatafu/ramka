package com.ramka.app.notifications

import android.app.NotificationChannel
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ramka.app.MainActivity

private const val CHANNEL_ID = "ramka_messages"
private const val NOTIFICATION_ID = 1 // одно и то же уведомление на всё приложение — без списка отправителей
private const val SERVICE_CHANNEL_ID = "ramka_background"

/**
 * Уведомления ramka (ЭТАП B.7). ЕДИНСТВЕННЫЙ разрешённый контент: title "ramka",
 * text "Новое сообщение" — без псевдонима контакта и без содержимого сообщения,
 * при любом количестве непрочитанных и от кого угодно. Это не техническое
 * ограничение (Android спокойно позволяет показать что угодно), а осознанное
 * решение — не менять НИ ПРИ КАКИХ обстоятельствах.
 *
 * publicVersion — ИДЕНТИЧНА основному уведомлению (тот же title/text), не
 * "более скрытая" версия: скрывать уже нечего, оба варианта одинаково не несут
 * ничего чувствительного. Ревью ЭТАП B, п.5: никаких extras/ticker с содержимым
 * или псевдонимом — здесь их нет и не будет.
 */
object RamkaNotifications {

    private const val TITLE = "ramka"
    private const val TEXT = "Новое сообщение"

    private val VIBRATION_PATTERN = longArrayOf(0, 250, 250, 250)

    /**
     * Вибрация включена по умолчанию на уровне канала (пользователю не нужно
     * ничего искать в системных настройках) — importance IMPORTANCE_DEFAULT,
     * НЕ LOW: каналы с LOW на части OEM-прошивок молча не вибрируют и даже не
     * показываются (см. README, раздел «Уведомления»).
     */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL_ID, TITLE, NotificationManager.IMPORTANCE_DEFAULT).apply {
            enableVibration(true)
            vibrationPattern = VIBRATION_PATTERN
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Показывает (или обновляет — один и тот же NOTIFICATION_ID) уведомление о новом
     * сообщении. Если разрешение POST_NOTIFICATIONS не выдано (API 33+) — вызов
     * NotificationManagerCompat.notify() тихо ничего не сделает; запрос разрешения —
     * ответственность UI-слоя (см. ui/contacts/ContactsScreen.kt), не этого объекта.
     */
    fun showNewMessageNotification(context: Context) {
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(TITLE)
            .setContentText(TEXT)
            .setAutoCancel(true)
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(TITLE)
            .setContentText(TEXT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .setVibrate(VIBRATION_PATTERN)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    // ---------- Постоянный сервис (ЭТАП B.8) ----------

    private const val SERVICE_TEXT = "Фоновая доставка активна"

    /**
     * Отдельный канал для уведомления foreground-сервиса. Важность DEFAULT (не LOW: на части
     * OEM-прошивок каналы LOW молча не показываются, а для foreground-сервиса видимое
     * уведомление обязательно), но БЕЗ звука и вибрации, чтобы «дефолтная» важность не
     * превращалась в звонок. Пользователь может выключить канал в системных настройках
     * (сервис при этом продолжит работать, просто уведомление скроется).
     */
    fun ensureServiceChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            SERVICE_CHANNEL_ID,
            "Фоновая доставка",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Постоянное уведомление сервиса: title «ramka», text «Фоновая доставка активна» — и всё.
     * Никакого содержимого переписки, псевдонимов и счётчиков; publicVersion идентична.
     */
    fun buildServiceNotification(context: Context): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val publicVersion = NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(TITLE)
            .setContentText(SERVICE_TEXT)
            .build()

        return NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(TITLE)
            .setContentText(SERVICE_TEXT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openApp)
            .build()
    }
}
