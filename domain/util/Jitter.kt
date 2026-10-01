package com.ramka.domain.util

import java.security.SecureRandom

/**
 * Источник случайных задержек (этап 2.5). Границы и выключатель — в [PrivacyTimingConfig].
 * Источник случайности инжектируется ([nextUnit] возвращает число из [0, 1)), поэтому тесты
 * проверяют границы и распределение без реальной случайности и без реального ожидания.
 */
class Jitter(
    private val config: PrivacyTimingConfig = PrivacyTimingConfig.DEFAULT,
    private val nextUnit: () -> Double = defaultSource()
) {
    /** Случайная задержка перед отправкой ACK, миллисекунды в [min, max] (включительно); 0 при выключенном джиттере. */
    fun ackDelayMillis(): Long {
        if (!config.jitterEnabled) return 0L
        val span = config.ackJitterMaxMillis - config.ackJitterMinMillis + 1
        val offset = (unit() * span).toLong().coerceIn(0L, span - 1)
        return config.ackJitterMinMillis + offset
    }

    /** Применяет к интервалу backoff случайный множитель в [1 − f, 1 + f]; без джиттера возвращает [baseMillis]. */
    fun applyBackoff(baseMillis: Long): Long {
        if (!config.jitterEnabled) return baseMillis
        val f = config.backoffJitterFraction
        val factor = (1.0 - f) + unit() * 2.0 * f
        return Math.round(baseMillis * factor).coerceAtLeast(0L)
    }

    private fun unit(): Double = nextUnit().coerceIn(0.0, 1.0)

    private companion object {
        fun defaultSource(): () -> Double {
            val random = SecureRandom()
            return { random.nextDouble() }
        }
    }
}
