package com.ramka.app.preferences

import android.content.Context
import com.ramka.app.background.BackgroundDeliveryPrefs

/**
 * Несекретные UI-флаги и настройки фоновой доставки. Намеренно обычный SharedPreferences,
 * а не [com.ramka.crypto.securestorage.SecureKeyStore] — контракт KeyValueStore (копии, не
 * ссылки; см. директиву v2) писан для криптографического материала, а не для булевых
 * флагов, и смешивать их не стоит.
 */
class AppPreferences(context: Context) : BackgroundDeliveryPrefs {
    private val prefs = context.getSharedPreferences("ramka_app_prefs", Context.MODE_PRIVATE)

    var hasAskedNotificationPermission: Boolean
        get() = prefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false)
        set(value) = prefs.edit().putBoolean(KEY_ASKED_NOTIFICATIONS, value).apply()

    override var backgroundDeliveryEnabled: Boolean
        get() = prefs.getBoolean(KEY_BACKGROUND_DELIVERY, true) // по умолчанию ВКЛ
        set(value) = prefs.edit().putBoolean(KEY_BACKGROUND_DELIVERY, value).apply()

    override var persistentServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_PERSISTENT_SERVICE, false) // по умолчанию ВЫКЛ
        set(value) = prefs.edit().putBoolean(KEY_PERSISTENT_SERVICE, value).apply()

    override var backgroundHintShown: Boolean
        get() = prefs.getBoolean(KEY_BACKGROUND_HINT_SHOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_BACKGROUND_HINT_SHOWN, value).apply()

    companion object {
        private const val KEY_ASKED_NOTIFICATIONS = "asked_notification_permission"
        private const val KEY_BACKGROUND_DELIVERY = "background_delivery_enabled"
        private const val KEY_PERSISTENT_SERVICE = "persistent_service_enabled"
        private const val KEY_BACKGROUND_HINT_SHOWN = "background_hint_shown"
    }
}
