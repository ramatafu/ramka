package com.ramka.app.background

/**
 * Производители, у которых агрессивное управление фоновой работой требует ручной
 * настройки пользователем (ЭТАП B.8). Чистая логика без обращения к `android.os.Build` —
 * значения передаются параметрами, поэтому тестируется pure-JVM (OemDetectorTest).
 */
enum class Oem { XIAOMI, HUAWEI, SAMSUNG, OPPO, VIVO, OTHER }

object OemDetector {

    /**
     * @param manufacturer `Build.MANUFACTURER`.
     * @param fingerprint `Build.FINGERPRINT` — запасной источник: формат
     *   `brand/product/device:...`, из него берётся brand, если manufacturer не опознан.
     *
     * Семейства прошивок: Xiaomi = Xiaomi/Redmi/POCO (MIUI, HyperOS); Huawei = Huawei/Honor
     * (EMUI, MagicOS); OPPO = OPPO/realme/OnePlus (ColorOS и родственные); vivo = vivo/iQOO.
     * Всё остальное — [Oem.OTHER] (общий текст).
     */
    fun detect(manufacturer: String?, fingerprint: String?): Oem {
        match(manufacturer)?.let { return it }
        val brandFromFingerprint = fingerprint?.substringBefore('/')
        match(brandFromFingerprint)?.let { return it }
        return Oem.OTHER
    }

    private fun match(raw: String?): Oem? {
        val value = raw?.trim()?.lowercase() ?: return null
        return when {
            value.isEmpty() -> null
            value in XIAOMI -> Oem.XIAOMI
            value in HUAWEI -> Oem.HUAWEI
            value in SAMSUNG -> Oem.SAMSUNG
            value in OPPO -> Oem.OPPO
            value in VIVO -> Oem.VIVO
            else -> null
        }
    }

    private val XIAOMI = setOf("xiaomi", "redmi", "poco", "blackshark")
    private val HUAWEI = setOf("huawei", "honor")
    private val SAMSUNG = setOf("samsung")
    private val OPPO = setOf("oppo", "realme", "oneplus")
    private val VIVO = setOf("vivo", "iqoo")
}

/** Тексты подсказки (таблица производитель -> текст). Общая фраза + короткая строка про вендора. */
object OemHintTexts {
    const val BASE = "Разрешите фоновую работу для ramka"

    fun forOem(oem: Oem): String = "$BASE. " + when (oem) {
        Oem.XIAOMI -> "Xiaomi/Redmi/POCO: включите «Автозапуск» и выберите «Нет ограничений» в настройках экономии заряда."
        Oem.HUAWEI -> "Huawei/Honor: в «Запуск приложений» отключите автоматическое управление и разрешите автозапуск и работу в фоне."
        Oem.SAMSUNG -> "Samsung: в «Батарея» уберите ramka из «Спящих приложений» и выберите «Без ограничений»."
        Oem.OPPO -> "OPPO/realme/OnePlus: разрешите «Автозапуск» и «Фоновую активность» для ramka."
        Oem.VIVO -> "vivo/iQOO: разрешите «Автозапуск» и работу в фоне с высоким потреблением для ramka."
        Oem.OTHER -> "Если сообщения приходят с задержкой, отключите оптимизацию батареи для ramka в настройках приложения."
    }
}
