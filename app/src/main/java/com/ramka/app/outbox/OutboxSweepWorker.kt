package com.ramka.app.outbox

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ramka.app.di.OutboxWorkerEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * Периодический sweep очереди (ЭТАП B.2) — планируется не чаще раза в 15 минут
 * (см. [com.ramka.app.outbox.OutboxScheduler]; 15 минут — это и есть минимальный
 * интервал, который сама платформа разрешает для `PeriodicWorkRequest`, меньше
 * системного ограничения всё равно не выставить).
 *
 * Обычный `CoroutineWorker`, не `@HiltWorker` — зависимости достаёт вручную через
 * [OutboxWorkerEntryPoint], чтобы не тянуть отдельный `androidx.hilt:hilt-work`.
 */
class OutboxSweepWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, OutboxWorkerEntryPoint::class.java)
        entryPoint.processOutboxUseCase().processDue(System.currentTimeMillis())
        // Неудачные попытки уже обработаны внутри processDue (новый backoff записан
        // в Outbox) — воркеру нечего ретраить самому, поэтому всегда success.
        return Result.success()
    }
}
