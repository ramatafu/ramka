package com.ramka.app.speedtest

import java.net.URI
import java.net.URISyntaxException

enum class SpeedQuality { GOOD, FAIR, POOR }

enum class SpeedRecommendation {
    /** Скорость низкая. */
    LOW_SPEED,

    /** Высокий jitter. */
    HIGH_JITTER,

    /** Высокая задержка до релея. */
    HIGH_PING,

    /** Всё хорошо, и скорость была измерена. */
    ALL_GOOD,

    /** Задержка и jitter в порядке, но скорость не измерялась (URL не задан). */
    LATENCY_GOOD_NO_SPEED
}

/** Чистые функции: статистика замеров, оценки и рекомендации. Без Android, тестируются обычным JUnit. */
object SpeedTestAnalysis {

    // Пороги. Ping — из ТЗ; остальные выбраны под звонки и передачу файлов.
    const val PING_GOOD_BELOW_MS = 50
    const val PING_FAIR_BELOW_MS = 100
    const val JITTER_GOOD_BELOW_MS = 20
    const val JITTER_FAIR_BELOW_MS = 50
    const val SPEED_GOOD_AT_LEAST_MBPS = 5.0
    const val SPEED_FAIR_AT_LEAST_MBPS = 1.0

    fun pingQuality(ms: Int) = when {
        ms < PING_GOOD_BELOW_MS -> SpeedQuality.GOOD
        ms < PING_FAIR_BELOW_MS -> SpeedQuality.FAIR
        else -> SpeedQuality.POOR
    }

    fun jitterQuality(ms: Int) = when {
        ms < JITTER_GOOD_BELOW_MS -> SpeedQuality.GOOD
        ms < JITTER_FAIR_BELOW_MS -> SpeedQuality.FAIR
        else -> SpeedQuality.POOR
    }

    fun speedQuality(mbps: Double) = when {
        mbps >= SPEED_GOOD_AT_LEAST_MBPS -> SpeedQuality.GOOD
        mbps >= SPEED_FAIR_AT_LEAST_MBPS -> SpeedQuality.FAIR
        else -> SpeedQuality.POOR
    }

    /** Мбит/с по числу байт и времени в наносекундах; нулевое или отрицательное время даёт 0. */
    fun mbps(bytes: Long, nanos: Long): Double =
        if (bytes <= 0 || nanos <= 0) 0.0 else bytes * 8.0 / (nanos / 1_000_000_000.0) / 1_000_000.0

    /**
     * Ping — среднее, jitter — средняя абсолютная разница соседних замеров (как в RFC 3550, без сглаживания).
     * null — замеров нет. Один замер даёт jitter 0.
     */
    fun pingAndJitter(samplesMs: List<Long>): Pair<Int, Int>? {
        if (samplesMs.isEmpty()) return null
        val ping = samplesMs.average()
        val jitter = if (samplesMs.size < 2) 0.0
        else samplesMs.zipWithNext { a, b -> kotlin.math.abs(b - a).toDouble() }.average()
        return Math.round(ping).toInt() to Math.round(jitter).toInt()
    }

    fun recommendations(test: NetworkSpeedTest): List<SpeedRecommendation> {
        val result = mutableListOf<SpeedRecommendation>()
        val speeds = listOfNotNull(test.downloadSpeedMbps, test.uploadSpeedMbps)
        if (speeds.any { speedQuality(it) == SpeedQuality.POOR }) result += SpeedRecommendation.LOW_SPEED
        if (test.jitterMs?.let { jitterQuality(it) } == SpeedQuality.POOR) result += SpeedRecommendation.HIGH_JITTER
        if (test.pingMs?.let { pingQuality(it) } == SpeedQuality.POOR) result += SpeedRecommendation.HIGH_PING
        if (result.isEmpty() && test.hasAnyMeasurement) {
            result += if (speeds.isEmpty()) SpeedRecommendation.LATENCY_GOOD_NO_SPEED else SpeedRecommendation.ALL_GOOD
        }
        return result
    }
}

/** Адрес для теста скорости: только https (приложение запрещает открытый HTTP), без логина в адресе. */
object SpeedTestUrl {
    /** Нормализованный адрес; null — пусто или неверный формат. */
    fun parse(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        val uri = try {
            URI(text)
        } catch (e: URISyntaxException) {
            return null
        }
        if (!"https".equals(uri.scheme, ignoreCase = true)) return null
        if (uri.host.isNullOrBlank() || uri.userInfo != null) return null
        return text
    }
}
