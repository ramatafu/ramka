package com.ramka.domain.util

/**
 * Расчёт задержки перед следующей попыткой доставки (§ЭТАП B.6 директивы v2):
 * экспоненциальный backoff, база 30 секунд, потолок 1 час. Чистая функция —
 * без побочных эффектов, без обращения к системному времени внутри, легко
 * тестируется unit-тестом.
 */
object RetryBackoff {
    private const val BASE_MILLIS = 30_000L        // 30 секунд
    private const val CAP_MILLIS = 3_600_000L       // 1 час

    /**
     * @param attemptCount сколько попыток уже было (0 — перед самой первой попыткой).
     * @return задержка в миллисекундах до следующей попытки, начиная с [BASE_MILLIS]
     *   и удваивающаяся на каждую последующую неудачную попытку, но не выше [CAP_MILLIS].
     */
    fun nextDelayMillis(attemptCount: Int): Long {
        require(attemptCount >= 0) { "attemptCount не может быть отрицательным" }
        // Ограничиваем показатель степени заранее, чтобы не словить переполнение
        // Long при большом attemptCount (потолок и так достигается задолго до этого).
        val cappedExponent = attemptCount.coerceAtMost(20)
        val raw = BASE_MILLIS * (1L shl cappedExponent)
        return if (raw < 0 || raw > CAP_MILLIS) CAP_MILLIS else raw
    }
}
