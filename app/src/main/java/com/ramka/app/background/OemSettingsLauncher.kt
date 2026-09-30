package com.ramka.app.background

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Открывает настройки, где пользователь может разрешить фоновую работу (ЭТАП B.8).
 * Для Xiaomi сначала пробуем экран «Автозапуск» MIUI/HyperOS (компонент может быть не
 * экспортирован или отсутствовать на конкретной прошивке — тогда ловим исключение),
 * во всех остальных случаях и при неудаче — страница приложения в системных настройках.
 */
object OemSettingsLauncher {

    fun open(context: Context, oem: Oem) {
        if (oem == Oem.XIAOMI && tryStart(context, miuiAutostartIntent())) return
        tryStart(context, appDetailsIntent(context))
    }

    private fun miuiAutostartIntent() = Intent().setComponent(
        ComponentName(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity"
        )
    )

    private fun appDetailsIntent(context: Context) = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    )

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) { // ActivityNotFoundException, SecurityException на закрытых прошивках
        false
    }
}
