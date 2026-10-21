package com.ramka.app.panic

import android.app.Activity
import android.app.job.JobScheduler
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.ramka.app.background.PersistentDeliveryService
import com.ramka.network.local.LanDiscoveryService
import com.ramka.network.local.SecureLanChannel
import com.ramka.storage.db.RamkaDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess

/**
 * «Кнопка паники»: мгновенное стирание всех данных без диалогов и подтверждений.
 *
 * Работает в собственном потоке (не в appScope и не в viewModelScope — их мы отменяем/они умрут
 * вместе с экраном). Все шаги в try/catch, ничего не логируется и не показывается в статус-баре.
 * Зависимости берутся через dagger.Lazy и тоже в try/catch: нестабильное состояние графа
 * не должно мешать стиранию.
 */
@Singleton
class PanicButtonManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appScope: CoroutineScope,
    private val database: dagger.Lazy<RamkaDatabase>,
    private val lanChannel: dagger.Lazy<SecureLanChannel>,
    private val lanDiscovery: dagger.Lazy<LanDiscoveryService>
) {
    private val started = AtomicBoolean(false)
    private val _blackout = MutableStateFlow(false)

    /** true — показывать чёрный экран поверх всего UI (имитация «выключения»). */
    val blackout: StateFlow<Boolean> = _blackout

    fun executePanic(activity: Activity?) {
        if (!started.compareAndSet(false, true)) return
        val t0 = SystemClock.elapsedRealtime()
        _blackout.value = true
        step { vibrate() }
        Thread({ runWipe(t0, activity) }, "sync-worker").start()
    }

    private fun runWipe(t0: Long, activity: Activity?) {
        step { PanicWipe.markPending(context) }               // если нас убьют — RamkaApp дочистит

        // 1. Остановить всё живое
        step { appScope.cancel() }                            // приём, Outbox, релеи, health-monitor, mDNS
        step { WorkManager.getInstance(context).cancelAllWork() }
        step { (context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler).cancelAll() }
        step { context.stopService(Intent(context, PersistentDeliveryService::class.java)) }
        step { NotificationManagerCompat.from(context).cancelAll() }
        step { lanDiscovery.get().unregisterService() }
        step { lanChannel.get().stop() }

        // 2. Сначала ключи (крипто-шреддинг), затем БД и файлы
        step { PanicWipe.wipeKeystore() }
        step { database.get().clearAllTables() }
        step { database.get().close() }
        step { PanicWipe.wipeAll(context) }

        // 3. Чёрный экран держится минимум 1 с; второй проход добивает то, что успели
        //    дописать «хвостовые» записи (DataStore, WorkManager).
        val left = 3_000L - (SystemClock.elapsedRealtime() - t0)
        if (left > 0) step { Thread.sleep(left) }
        step { PanicWipe.wipeAll(context) }
        step { PanicWipe.clearMarker(context) }

        restart(activity)
    }

    private fun restart(activity: Activity?) {
        step { activity?.runOnUiThread { step { activity.finishAffinity() } } }
        step {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            if (intent != null) context.startActivity(intent)
        }
        Process.killProcess(Process.myPid())
        exitProcess(0)
    }

    private fun vibrate() {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private inline fun step(block: () -> Unit) {
        try { block() } catch (_: Throwable) { }
    }
}
