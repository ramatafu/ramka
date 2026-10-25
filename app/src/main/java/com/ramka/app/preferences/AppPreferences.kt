package com.ramka.app.preferences

import android.content.Context
import com.ramka.app.background.BackgroundDeliveryPrefs
import com.ramka.app.discovery.MdnsPrefs
import com.ramka.app.relay.LegacyRelaySnapshot
import com.ramka.app.relay.RelaySecrets

class AppPreferences(context: Context) : BackgroundDeliveryPrefs, MdnsPrefs {
    private val prefs = context.getSharedPreferences("ramka_app_prefs", Context.MODE_PRIVATE)

    var hasAskedNotificationPermission: Boolean
        get() = prefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false)
        set(value) = prefs.edit().putBoolean(KEY_ASKED_NOTIFICATIONS, value).apply()

    override var backgroundDeliveryEnabled: Boolean
        get() = prefs.getBoolean(KEY_BACKGROUND_DELIVERY, true)
        set(value) = prefs.edit().putBoolean(KEY_BACKGROUND_DELIVERY, value).apply()

    override var persistentServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_PERSISTENT_SERVICE, false)
        set(value) = prefs.edit().putBoolean(KEY_PERSISTENT_SERVICE, value).apply()

    override var backgroundHintShown: Boolean
        get() = prefs.getBoolean(KEY_BACKGROUND_HINT_SHOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_BACKGROUND_HINT_SHOWN, value).apply()

    override var mdnsEnabled: Boolean
        get() = prefs.getBoolean(KEY_MDNS, true)
        set(value) = prefs.edit().putBoolean(KEY_MDNS, value).apply()

    /** Рингтон при входящем звонке (по умолчанию включён). */
    var callRingtoneEnabled: Boolean
        get() = prefs.getBoolean(KEY_CALL_RINGTONE, true)
        set(value) = prefs.edit().putBoolean(KEY_CALL_RINGTONE, value).apply()

    /** Вибрация при входящем звонке (по умолчанию включена). */
    var callVibrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_CALL_VIBRATION, true)
        set(value) = prefs.edit().putBoolean(KEY_CALL_VIBRATION, value).apply()

    fun legacyRelaySnapshot(secrets: RelaySecrets): LegacyRelaySnapshot? {
        val enabled = prefs.getBoolean(KEY_RELAY_ENABLED, false)
        val address = prefs.getString(KEY_RELAY_ADDRESS, "")?.takeIf { it.isNotBlank() }
        val pin = prefs.getString(KEY_RELAY_PIN, "")?.takeIf { it.isNotBlank() }
        val token = secrets.get(ALIAS_LEGACY_TOKEN)?.takeIf { it.isNotBlank() }
        if (address == null && token == null && pin == null) return null
        return LegacyRelaySnapshot(enabled, address, token, pin)
    }

    fun clearLegacyRelay(secrets: RelaySecrets) {
        prefs.edit()
            .remove(KEY_RELAY_ENABLED)
            .remove(KEY_RELAY_ADDRESS)
            .remove(KEY_RELAY_PIN)
            .apply()
        secrets.remove(ALIAS_LEGACY_TOKEN)
    }

    companion object {
        private const val KEY_ASKED_NOTIFICATIONS = "asked_notification_permission"
        private const val KEY_BACKGROUND_DELIVERY = "background_delivery_enabled"
        private const val KEY_PERSISTENT_SERVICE = "persistent_service_enabled"
        private const val KEY_BACKGROUND_HINT_SHOWN = "background_hint_shown"
        private const val KEY_MDNS = "mdns_enabled"
        private const val KEY_CALL_RINGTONE = "call_ringtone_enabled"
        private const val KEY_CALL_VIBRATION = "call_vibration_enabled"
        private const val KEY_RELAY_ENABLED = "relay_enabled"
        private const val KEY_RELAY_ADDRESS = "relay_address"
        private const val KEY_RELAY_PIN = "relay_pin"
        const val ALIAS_LEGACY_TOKEN = "relay_token"
    }
}