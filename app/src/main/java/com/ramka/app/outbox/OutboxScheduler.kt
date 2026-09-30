package com.ramka.app.outbox

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import java.util.concurrent.TimeUnit

/**
 * Регистрирует периодический sweep Outbox (ЭТАП B.2). 15 минут — минимальный
 * интервал, который `PeriodicWorkRequest` вообще разрешает; запросить чаще
 * технически нельзя, платформа сама округлит вверх.
 *
 * Constraints: NetworkType.NOT_REQUIRED — нам не нужен доступ в интернет (этап 1
 * без сервера), только сама возможность выполниться в фоне; никаких других
 * условий (зарядка, "не в режиме простоя" и т.п.) не выставляем, чтобы sweep не
 * откладывался сверх необходимого.
 */
object OutboxScheduler {
    private const val WORK_NAME = "ramka_outbox_sweep"

    fun schedulePeriodicSweep(context: Context) {
        val request = PeriodicWorkRequestBuilder<OutboxSweepWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // уже запланирован с прошлого запуска — не пересоздавать
            request
        )
    }

    /** Отмена периодического sweep — выключение тумблера «Фоновая доставка» (ЭТАП B.8). */
    fun cancelPeriodicSweep(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
