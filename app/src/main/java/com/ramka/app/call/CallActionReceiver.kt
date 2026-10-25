package com.ramka.app.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Кнопка «Отклонить» в уведомлении о входящем звонке. Приёмник не открывает экран (на Android 12+ запуск
 * Activity из приёмника-«трамплина» запрещён) и ничего не делает сам: передаёт решение в [CallAlerts].
 * Hilt-entry point вместо @AndroidEntryPoint — чтобы не зависеть от super.onReceive в Kotlin.
 */
class CallActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun callAlerts(): CallAlerts
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CallAlerts.ACTION_DECLINE) return
        val callId = intent.getStringExtra(CallAlerts.EXTRA_CALL_ID) ?: return
        val contactId = intent.getStringExtra(CallAlerts.EXTRA_CALLER_ID) ?: return
        EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
            .callAlerts()
            .declineFromNotification(callId, contactId)
    }
}
