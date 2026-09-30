package com.ramka.app.background

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.ramka.app.outbox.OutboxScheduler

/** Реальная реализация [SweepScheduler] поверх WorkManager (см. OutboxScheduler). */
class WorkManagerSweepScheduler(private val context: Context) : SweepScheduler {
    override fun schedule() = OutboxScheduler.schedulePeriodicSweep(context)
    override fun cancel() = OutboxScheduler.cancelPeriodicSweep(context)
}

/**
 * Реальная реализация [PersistentService]. Старт foreground-сервиса из фона на Android 12+
 * бросает ForegroundServiceStartNotAllowedException (наследник IllegalStateException) —
 * это НЕ должно ронять приложение: контроллер вызывается только с экрана, но исключение
 * всё равно перехватывается (например, если процесс потерял foreground-статус в гонке).
 */
class ForegroundPersistentService(private val context: Context) : PersistentService {
    override fun start() {
        try {
            ContextCompat.startForegroundService(context, Intent(context, PersistentDeliveryService::class.java))
        } catch (_: IllegalStateException) {
            // Запуск из фона запрещён системой — сервис поднимется при следующем показе экрана.
        } catch (_: SecurityException) {
            // Нет прав на указанный тип сервиса — не критично для работы приложения.
        }
    }

    override fun stop() {
        context.stopService(Intent(context, PersistentDeliveryService::class.java))
    }
}
