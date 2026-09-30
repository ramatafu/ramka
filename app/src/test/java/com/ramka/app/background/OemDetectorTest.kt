package com.ramka.app.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OemDetectorTest {

    /** Таблица: Build.MANUFACTURER -> ожидаемый производитель. */
    private val manufacturerTable = listOf(
        "Xiaomi" to Oem.XIAOMI,
        "xiaomi" to Oem.XIAOMI,
        "Redmi" to Oem.XIAOMI,
        "POCO" to Oem.XIAOMI,
        "HUAWEI" to Oem.HUAWEI,
        "HONOR" to Oem.HUAWEI,
        "samsung" to Oem.SAMSUNG,
        "SAMSUNG" to Oem.SAMSUNG,
        "OPPO" to Oem.OPPO,
        "realme" to Oem.OPPO,
        "OnePlus" to Oem.OPPO,
        "vivo" to Oem.VIVO,
        "iQOO" to Oem.VIVO,
        "Google" to Oem.OTHER,
        "motorola" to Oem.OTHER,
        "Sony" to Oem.OTHER
    )

    @Test
    fun `manufacturer table maps to the expected vendor`() {
        for ((manufacturer, expected) in manufacturerTable) {
            assertEquals("manufacturer=$manufacturer", expected, OemDetector.detect(manufacturer, null))
        }
    }

    @Test
    fun `surrounding whitespace and case are ignored`() {
        assertEquals(Oem.XIAOMI, OemDetector.detect("  XiAoMi \n", null))
    }

    @Test
    fun `fingerprint brand is used when manufacturer is not recognised`() {
        assertEquals(Oem.XIAOMI, OemDetector.detect("unknown", "Xiaomi/lisa/lisa:13/TKQ1/V14:user/release-keys"))
        assertEquals(Oem.XIAOMI, OemDetector.detect(null, "Redmi/spes/spes:13/TKQ1/V14:user/release-keys"))
        assertEquals(Oem.SAMSUNG, OemDetector.detect("", "samsung/a52qnsxx/a52q:13/TP1A/A525:user/release-keys"))
    }

    @Test
    fun `recognised manufacturer wins over fingerprint`() {
        assertEquals(Oem.HUAWEI, OemDetector.detect("HUAWEI", "Xiaomi/x/x:1/1/1:user/release-keys"))
    }

    @Test
    fun `null empty and unknown inputs fall back to OTHER`() {
        assertEquals(Oem.OTHER, OemDetector.detect(null, null))
        assertEquals(Oem.OTHER, OemDetector.detect("", ""))
        assertEquals(Oem.OTHER, OemDetector.detect("Google", "google/raven/raven:14/AP1A/1:user/release-keys"))
    }

    @Test
    fun `hint text per vendor starts with the common phrase and names the vendor`() {
        val expectedVendorMarker = mapOf(
            Oem.XIAOMI to "Xiaomi",
            Oem.HUAWEI to "Huawei",
            Oem.SAMSUNG to "Samsung",
            Oem.OPPO to "OPPO",
            Oem.VIVO to "vivo"
        )
        for ((oem, marker) in expectedVendorMarker) {
            val text = OemHintTexts.forOem(oem)
            assertTrue("$oem: нет общей фразы", text.startsWith(OemHintTexts.BASE))
            assertTrue("$oem: нет '$marker' в «$text»", text.contains(marker))
        }
    }

    @Test
    fun `generic text is used for other vendors and has no vendor name`() {
        val text = OemHintTexts.forOem(Oem.OTHER)
        assertTrue(text.startsWith("Разрешите фоновую работу для ramka"))
        for (name in listOf("Xiaomi", "Huawei", "Samsung", "OPPO", "vivo")) {
            assertTrue("общий текст не должен упоминать $name", !text.contains(name))
        }
    }

    @Test
    fun `all hint texts are distinct`() {
        val texts = Oem.values().map { OemHintTexts.forOem(it) }
        assertEquals(texts.size, texts.toSet().size)
    }
}
