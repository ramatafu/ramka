package com.ramka.app.speedtest

/**
 * Результат одного прогона. Метрика, которую измерить не удалось или не требовалось, равна null
 * (причина — в [SpeedTestOutcome.skipped]).
 *
 * @property pingMs среднее время `RelayClient.check()` до релея: это полное подключение (TCP + TLS +
 *   проверка токена), поэтому оно заметно больше обычного ICMP-ping.
 * @property jitterMs средняя разница между соседними замерами [pingMs].
 * @property downloadSpeedMbps скорость скачивания по пользовательскому URL, Мбит/с.
 * @property uploadSpeedMbps скорость отправки (POST на тот же URL), Мбит/с.
 */
data class NetworkSpeedTest(
    val downloadSpeedMbps: Double?,
    val uploadSpeedMbps: Double?,
    val pingMs: Int?,
    val jitterMs: Int?,
    val timestamp: Long
) {
    val hasAnyMeasurement: Boolean
        get() = downloadSpeedMbps != null || uploadSpeedMbps != null || pingMs != null
}

/** Почему часть метрик отсутствует. */
enum class SpeedTestSkip {
    /** Пул релеёв выключен или в нём нет рабочей записи: Ping и Jitter не измерялись. */
    NO_RELAY,

    /** Релей есть, но достаточно успешных замеров получить не удалось. */
    RELAY_FAILED,

    /** URL для теста не задан: Download и Upload не измерялись (это нормальный режим по умолчанию). */
    NO_URL,

    /** URL задан, но скачать файл не удалось (сеть, сертификат, код ответа не 200, пустой ответ). */
    DOWNLOAD_FAILED,

    /** Сервер не принял POST: Upload пропущен. */
    UPLOAD_UNSUPPORTED
}

enum class SpeedTestStage { PING, DOWNLOAD, UPLOAD }

/** Итог [NetworkSpeedTester.runSpeedTest]. */
data class SpeedTestOutcome(
    val test: NetworkSpeedTest,
    val skipped: Set<SpeedTestSkip>,
    /** true — показан результат из кэша (не старше часа), сеть не использовалась. */
    val fromCache: Boolean = false
)
