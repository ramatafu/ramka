package com.ramka.app.preferences

import android.content.Context
import com.ramka.app.background.BackgroundDeliveryPrefs
import com.ramka.app.discovery.MdnsPrefs
import com.ramka.app.relay.RelayPrefs

/**
 * Несекретные UI-флаги и настройки фоновой доставки. Намеренно обычный SharedPreferences,
 * а не [com.ramka.crypto.securestorage.SecureKeyStore] — контракт KeyValueStore (копии, не
 * ссылки; см. директиву v2) писан для криптографического материала, а не для булевых
 * флагов, и смешивать их не стоит.
 */
class AppPreferences(context: Context) : BackgroundDeliveryPrefs, MdnsPrefs, RelayPrefs {
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

    override var mdnsEnabled: Boolean
        get() = prefs.getBoolean(KEY_MDNS, true) // по умолчанию ВКЛ
        set(value) = prefs.edit().putBoolean(KEY_MDNS, value).apply()

    // Домашний relay (этап 3). Токен доступа здесь НЕ хранится: он лежит в SecureKeyStore (см. RelaySettings).
    override var relayEnabled: Boolean
        get() = prefs.getBoolean(KEY_RELAY_ENABLED, false) // по умолчанию ВЫКЛ
        set(value) = prefs.edit().putBoolean(KEY_RELAY_ENABLED, value).apply()

    override var relayAddress: String
        get() = prefs.getString(KEY_RELAY_ADDRESS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_RELAY_ADDRESS, value).apply()

    override var relayPin: String
        get() = prefs.getString(KEY_RELAY_PIN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_RELAY_PIN, value).apply()

    companion object {
        private const val KEY_ASKED_NOTIFICATIONS = "asked_notification_permission"
        private const val KEY_BACKGROUND_DELIVERY = "background_delivery_enabled"
        private const val KEY_PERSISTENT_SERVICE = "persistent_service_enabled"
        private const val KEY_BACKGROUND_HINT_SHOWN = "background_hint_shown"
        private const val KEY_MDNS = "mdns_enabled"
        private const val KEY_RELAY_ENABLED = "relay_enabled"
        private const val KEY_RELAY_ADDRESS = "relay_address"
        private const val KEY_RELAY_PIN = "relay_pin"
    }
}
