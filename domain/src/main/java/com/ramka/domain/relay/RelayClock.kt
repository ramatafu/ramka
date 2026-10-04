package com.ramka.domain.relay

/**
 * Источник времени для логики релеёв (cooldown, залипание, бюджет времени). Инжектируется, чтобы
 * тесты управляли временем без реальных пауз.
 */
fun interface RelayClock {
    /** Миллисекунды на монотонной шкале: годятся только для сравнения и разности, не как дата. */
    fun nowMillis(): Long

    companion object {
        /** Монотонные часы системы: не зависят от перевода времени и смены часового пояса. */
        val System: RelayClock = RelayClock { java.lang.System.nanoTime() / 1_000_000L }
    }
}
