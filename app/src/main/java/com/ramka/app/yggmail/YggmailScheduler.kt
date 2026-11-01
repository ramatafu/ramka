package com.ramka.app.yggmail

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ramka.app.di.YggmailWorkerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Периодический опрос почты (раз в 15 минут — минимум WorkManager). Планируется только пока Yggmail включён,
 * настроен и схема шифрования есть (см. [YggmailController]). Сеть не требуется как ограничение: в локальной
 * mesh-сети Yggdrasil без интернета система считает сеть «невалидированной», и воркер никогда бы не запустился.
 */
object YggmailScheduler {
    private const val WORK_NAME = "ramka_yggmail_poll"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<YggmailPollingWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}

/**
 * Воркер опроса. Всегда `success`: ошибка почты — не повод для повторов с нарастающей нагрузкой, следующий опрос
 * через 15 минут. Любое исключение (включая отсутствие классов JavaMail) поглощается: фоновый опрос необязательной
 * функции не должен ни падать, ни влиять на остальные воркеры. Как и у Outbox, зависимости берутся через EntryPoint.
 */
class YggmailPollingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            val inbox = EntryPointAccessors.fromApplication(applicationContext, YggmailWorkerEntryPoint::class.java).yggmailInbox()
            inbox.pollOnce() // результат не нужен: все ошибки уже залогированы внутри
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // поглощаем
        } catch (e: LinkageError) {
            // поглощаем
        }
        return Result.success()
    }
}
