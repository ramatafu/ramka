package com.ramka.app.background

/** Настройки фоновой доставки. Реализация — обычные SharedPreferences (не секреты). */
interface BackgroundDeliveryPrefs {
    /** Главный тумблер, по умолчанию ВКЛ: управляет периодическим sweep (WorkManager). */
    var backgroundDeliveryEnabled: Boolean
    /** Под-тумблер, по умолчанию ВЫКЛ: постоянный foreground-сервис. */
    var persistentServiceEnabled: Boolean
    /** Одноразовая OEM-подсказка уже показана. */
    var backgroundHintShown: Boolean
}

interface SweepScheduler {
    fun schedule()
    fun cancel()
}

interface PersistentService {
    fun start()
    fun stop()
}

/**
 * Единая логика включения/выключения фоновой доставки (ЭТАП B.8).
 *
 * Правила:
 * - sweep (WorkManager) работает, только если включён главный тумблер;
 * - foreground-сервис работает, только если включены И главный, И под-тумблер;
 * - выключение главного тумблера останавливает и sweep, и сервис, но НЕ стирает
 *   сохранённое значение под-тумблера — при повторном включении главного сервис
 *   вернётся, если пользователь его выбирал.
 *
 * Разделено на [applyScheduling] и [applyService] намеренно: запланировать sweep безопасно
 * из любого места (в т.ч. из Application, когда процесс поднял WorkManager), а СТАРТ
 * foreground-сервиса из фона на Android 12+ запрещён — его нужно вызывать только когда
 * приложение на экране (MainActivity.onStart, экран настроек).
 */
class BackgroundDeliveryController(
    private val prefs: BackgroundDeliveryPrefs,
    private val scheduler: SweepScheduler,
    private val service: PersistentService
) {
    fun applyScheduling() {
        if (prefs.backgroundDeliveryEnabled) scheduler.schedule() else scheduler.cancel()
    }

    fun applyService() {
        if (prefs.backgroundDeliveryEnabled && prefs.persistentServiceEnabled) service.start() else service.stop()
    }

    fun setBackgroundDelivery(enabled: Boolean) {
        prefs.backgroundDeliveryEnabled = enabled
        applyScheduling()
        applyService()
    }

    fun setPersistentService(enabled: Boolean) {
        prefs.persistentServiceEnabled = enabled
        applyService()
    }

    /** Показывать одноразовую OEM-подсказку: фоновая доставка включена и подсказку ещё не показывали. */
    fun shouldShowHint(): Boolean = prefs.backgroundDeliveryEnabled && !prefs.backgroundHintShown

    fun markHintShown() {
        prefs.backgroundHintShown = true
    }
}
