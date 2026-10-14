package com.ramka.app.ui.speedtest

import com.ramka.app.speedtest.SpeedQuality
import com.ramka.app.speedtest.SpeedRecommendation
import com.ramka.app.speedtest.SpeedTestSkip
import com.ramka.app.speedtest.SpeedTestStage

/** Тексты экрана проверки скорости. */

internal fun recommendationText(r: SpeedRecommendation): String = when (r) {
    SpeedRecommendation.LOW_SPEED -> "Рекомендуется использовать Wi-Fi или настроить TURN-сервер"
    SpeedRecommendation.HIGH_JITTER -> "Нестабильное соединение. Звонки могут прерываться"
    SpeedRecommendation.HIGH_PING -> "Высокая задержка до relay: в звонках возможны паузы"
    SpeedRecommendation.ALL_GOOD -> "Скорость достаточна для звонков и передачи файлов"
    SpeedRecommendation.LATENCY_GOOD_NO_SPEED ->
        "Задержка и стабильность в порядке. Чтобы проверить скорость, укажите URL для теста"
}

internal fun qualityText(q: SpeedQuality): String = when (q) {
    SpeedQuality.GOOD -> "хорошо"
    SpeedQuality.FAIR -> "средне"
    SpeedQuality.POOR -> "плохо"
}

internal fun stageText(stage: SpeedTestStage?): String = when (stage) {
    SpeedTestStage.PING -> "Измеряем задержку до relay…"
    SpeedTestStage.DOWNLOAD -> "Скачиваем тестовый файл…"
    SpeedTestStage.UPLOAD -> "Проверяем отправку…"
    null -> "Тест выполняется…"
}

/** Подпись под пустой метрикой. null — причины показывать не нужно. */
internal fun skipCaption(skip: SpeedTestSkip): String? = when (skip) {
    SpeedTestSkip.NO_RELAY -> "Включите пул релеёв, чтобы измерить задержку"
    SpeedTestSkip.RELAY_FAILED -> "Не удалось получить достаточно ответов от relay"
    SpeedTestSkip.DOWNLOAD_FAILED -> "Не удалось скачать файл по этому URL"
    SpeedTestSkip.UPLOAD_UNSUPPORTED -> "Сервер не принимает POST, отправка не проверена"
    SpeedTestSkip.NO_URL -> null
}

/** Сообщение, когда не измерено ничего. */
internal fun emptyResultMessage(skipped: Set<SpeedTestSkip>): String = when {
    SpeedTestSkip.DOWNLOAD_FAILED in skipped && SpeedTestSkip.RELAY_FAILED in skipped ->
        "Не удалось подключиться ни к relay, ни к адресу для теста. Проверьте интернет и настройки."
    SpeedTestSkip.DOWNLOAD_FAILED in skipped ->
        "Не удалось скачать файл по указанному адресу. Проверьте URL и подключение к интернету."
    SpeedTestSkip.RELAY_FAILED in skipped ->
        "Не удалось получить ответы от relay. Проверьте подключение к интернету и настройки релея."
    else ->
        "Нечего измерять: включите пул релеёв или укажите URL для теста скорости."
}
