package com.ramka.domain.util

/**
 * Единый конфиг временной маскировки (этап 2.5, джиттер). Все константы джиттера живут здесь.
 *
 * Джиттер сглаживает временные паттерны, которые видит наблюдатель LAN: обратное соединение
 * (ACK) сразу после сообщения и строго детерминированные интервалы повторов Outbox.
 *
 * @property jitterEnabled общий выключатель (по умолчанию ВКЛ). При `false` задержка ACK нулевая,
 *   а backoff Outbox не искажается — поведение до этапа 2.5.
 * @property ackJitterMinMillis нижняя граница случайной задержки перед DELIVERED-ACK/READ-ACK.
 * @property ackJitterMaxMillis верхняя граница (включительно).
 * @property backoffJitterFraction доля случайного отклонения backoff Outbox в обе стороны
 *   (0.20 = ±20%, в том числе на потолке: 1 час -> 48–72 минуты).
 */
data class PrivacyTimingConfig(
    val jitterEnabled: Boolean = true,
    val ackJitterMinMillis: Long = 500L,
    val ackJitterMaxMillis: Long = 5_000L,
    val backoffJitterFraction: Double = 0.20
) {
    init {
        require(ackJitterMinMillis >= 0) { "ackJitterMinMillis не может быть отрицательным" }
        require(ackJitterMaxMillis >= ackJitterMinMillis) { "ackJitterMaxMillis < ackJitterMinMillis" }
        require(backoffJitterFraction >= 0.0 && backoffJitterFraction < 1.0) {
            "backoffJitterFraction должен быть в [0, 1)"
        }
    }

    companion object {
        val DEFAULT = PrivacyTimingConfig()
    }
}
