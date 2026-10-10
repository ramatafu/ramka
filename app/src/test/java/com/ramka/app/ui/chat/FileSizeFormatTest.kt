package com.ramka.app.ui.chat

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class FileSizeFormatTest {
    private val us = Locale.US

    @Test fun zero() = assertEquals("0 КБ", formatFileSize(0, us))
    @Test fun negativeTreatedAsZero() = assertEquals("0 КБ", formatFileSize(-5, us))
    @Test fun tinyFileIsAtLeastOneKb() = assertEquals("1 КБ", formatFileSize(1, us))
    @Test fun kilobytes() = assertEquals("123 КБ", formatFileSize(123L * 1024, us))
    @Test fun justBelowMegabyteStaysInKb() = assertEquals("1023 КБ", formatFileSize(1024L * 1024 - 1, us))
    @Test fun exactMegabyte() = assertEquals("1.0 МБ", formatFileSize(1024L * 1024, us))
    @Test fun megabytesWithFraction() = assertEquals("1.5 МБ", formatFileSize(1536L * 1024, us))
    @Test fun russianLocaleUsesComma() = assertEquals("1,5 МБ", formatFileSize(1536L * 1024, Locale("ru", "RU")))
}
