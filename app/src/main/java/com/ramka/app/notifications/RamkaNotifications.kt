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
import com.ramka.app.R
import com.ramka.app.call.CallActionReceiver
import com.ramka.app.call.CallAlerts
import com.ramka.domain.usecase.OfflineFileSignal

private const val CHANNEL_ID = "ramka_messages"
private const val NOTIFICATION_ID = 1 // одно и то же уведомление на всё приложение — без списка отправителей
private const val FILE_SIGNAL_NOTIFICATION_ID = 3 // отдельное: «новое сообщение» его не затирает (2 занят PersistentDeliveryService)
private const val SERVICE_CHANNEL_ID = "ramka_background"
private const val CALLS_CHANNEL_ID = "ramka_calls"
private const val INCOMING_CALL_NOTIFICATION_ID = 4
private const val MISSED_CALL_NOTIFICATION_ID = 5
private const val INCOMING_CALL_TIMEOUT_MS = 60_000L

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
 *
 * ЕДИНСТВЕННОЕ осознанное исключение — [showFileSignalNotification]: фиксированная константа
 * [OfflineFileSignal.TEXT] («Вам пытаются отправить файл…»). Она не берётся из содержимого
 * переписки и не содержит псевдонима; на экране блокировки (publicVersion) по-прежнему
 * показывается обычное «Новое сообщение».
 *
 * Второе осознанное исключение — баннер входящего звонка ([showIncomingCall]): в нём имя контакта
 * («Имя звонит вам»), иначе принять решение невозможно. На экране блокировки (publicVersion) имени нет.
 * Уведомление о пропущенном звонке ([showMissedCall]) имени не содержит.
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
        manager.createNotificationChannel(buildCallsChannel())
    }

    /**
     * Канал звонков: IMPORTANCE_HIGH — именно он даёт heads-up баннер вверху экрана. Звук и вибрацию канал
     * НЕ играет: рингтон идёт по кругу и подчиняется переключателям в настройках (CallAlerts), а звук канала
     * сыграл бы один раз поверх него. setBypassDnd — запрос обхода «Не беспокоить» (действует, только если
     * пользователь выдал приложению доступ к политике уведомлений).
     */
    private fun buildCallsChannel(): NotificationChannel =
        NotificationChannel(CALLS_CHANNEL_ID, "Звонки", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Уведомления о входящих звонках"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            setBypassDnd(true)
        }

    /**
     * Heads-up баннер входящего звонка с кнопками «Принять» и «Отклонить». БЕЗ setFullScreenIntent:
     * на заблокированном экране он открыл бы приложение на весь экран (и потребовал бы
     * USE_FULL_SCREEN_INTENT на Android 14+), а нужен именно баннер. Intent'ы с SINGLE_TOP, не CLEAR_TOP:
     * CLEAR_TOP пересоздал бы MainActivity вместе с CallViewModel и оборвал бы звонок.
     */
    fun showIncomingCall(context: Context, callId: String, contactId: String, name: String, withVideo: Boolean) {
        fun activityIntent(action: String) = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(CallAlerts.EXTRA_INCOMING_CALL, true)
            putExtra(CallAlerts.EXTRA_CALLER_ID, contactId)
            putExtra(CallAlerts.EXTRA_CALL_ID, callId)
            putExtra(CallAlerts.EXTRA_CALL_ACTION, action)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(context, 10, activityIntent(CallAlerts.ACTION_OPEN), flags)
        val accept = PendingIntent.getActivity(context, 11, activityIntent(CallAlerts.ACTION_ACCEPT), flags)
        val decline = PendingIntent.getBroadcast(
            context,
            12,
            Intent(context, CallActionReceiver::class.java).apply {
                action = CallAlerts.ACTION_DECLINE
                putExtra(CallAlerts.EXTRA_CALL_ID, callId)
                putExtra(CallAlerts.EXTRA_CALLER_ID, contactId)
            },
            flags
        )
        val title = if (withVideo) "Входящий видеозвонок" else "Входящий звонок"
        val publicVersion = NotificationCompat.Builder(context, CALLS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ramka_logo)
            .setContentTitle(TITLE)
            .setContentText(title)
            .build()
        val notification = NotificationCompat.Builder(context, CALLS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ramka_logo)
            .setContentTitle(title)
            .setContentText("$name звонит вам")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(open)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(INCOMING_CALL_TIMEOUT_MS) // страховка: баннер не зависнет, если процесс умрёт
            // Иконки 0: на Android 7+ у кнопок уведомления иконок нет, а minSdk приложения 26.
            .addAction(0, "Принять", accept)
            .addAction(0, "Отклонить", decline)
            .build()
        NotificationManagerCompat.from(context).notify(INCOMING_CALL_NOTIFICATION_ID, notification)
    }

    /**
     * Покажется ли баннер: уведомления включены в системе и пользователь не заглушил канал «Звонки»
     * (IMPORTANCE_HIGH — именно он даёт heads-up). Иначе баннер не появится, и звонок нужно показать экраном.
     */
    fun canShowCallBanner(context: Context): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        val channel = manager.getNotificationChannel(CALLS_CHANNEL_ID) ?: return true
        return channel.importance >= NotificationManager.IMPORTANCE_HIGH
    }

    fun cancelIncomingCall(context: Context) {
        NotificationManagerCompat.from(context).cancel(INCOMING_CALL_NOTIFICATION_ID)
    }

    /** Обычное (не heads-up) уведомление о пропущенном звонке: канал сообщений, без имени контакта. */
    fun showMissedCall(context: Context) {
        val open = PendingIntent.getActivity(
            context,
            13,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ramka_logo)
            .setContentTitle(TITLE)
            .setContentText("Пропущенный звонок")
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(MISSED_CALL_NOTIFICATION_ID, notification)
    }

    /**
     * Показывает (или обновляет — один и тот же NOTIFICATION_ID) уведомление о новом
     * сообщении. Если разрешение POST_NOTIFICATIONS не выдано (API 33+) — вызов
     * NotificationManagerCompat.notify() тихо ничего не сделает; запрос разрешения —
     * ответственность UI-слоя (см. ui/contacts/ContactsScreen.kt), не этого объекта.
     */
    fun showNewMessageNotification(context: Context) {
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ramka_logo)
            .setContentTitle(TITLE)
            .setContentText(TEXT)
            .setAutoCancel(true)
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ramka_logo)
            .setContentTitle(TITLE)
            .setContentText(TEXT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .setVibrate(VIBRATION_PATTERN)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    /**
     * Уведомление получателю: отправитель пытался передать файл, но не смог достучаться (см. [OfflineFileSignal]).
     * Текст — константа, а не содержимое сообщения; нажатие открывает приложение. На заблокированном
     * экране виден только обычный publicVersion «ramka — Новое сообщение».
     */
    fun showFileSignalNotification(context: Context) {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ramka_logo)
            .setContentTitle(TITLE)
            .setContentText(TEXT)
            .setAutoCancel(true)
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ramka_logo)
            .setContentTitle(TITLE)
            .setContentText(OfflineFileSignal.TEXT)
            .setStyle(NotificationCompat.BigTextStyle().bigText(OfflineFileSignal.TEXT))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setVibrate(VIBRATION_PATTERN)
            .build()
        NotificationManagerCompat.from(context).notify(FILE_SIGNAL_NOTIFICATION_ID, notification)
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
            .setSmallIcon(R.drawable.ic_ramka_logo)
            .setContentTitle(TITLE)
            .setContentText(SERVICE_TEXT)
            .build()

        return NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ramka_logo)
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
