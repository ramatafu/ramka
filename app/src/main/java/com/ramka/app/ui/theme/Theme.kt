package com.ramka.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Тёмная тема — основная, согласно п. 8.1. Светлая — заготовка на будущее,
 * не является приоритетом для этапа 1.
 */
private val RamkaDarkColors = darkColorScheme(
    background = RamkaBackground,
    surface = RamkaSurface,
    primary = RamkaAccent,
    onBackground = RamkaTextPrimary,
    onSurface = RamkaTextPrimary,
    secondary = RamkaTextSecondary,
    error = RamkaError
)

private val RamkaLightColors = lightColorScheme(
    primary = RamkaAccent
)

@Composable
fun RamkaTheme(useDarkTheme: Boolean = true, content: @Composable () -> Unit) {
    val colors = if (useDarkTheme) RamkaDarkColors else RamkaLightColors
    MaterialTheme(
        colorScheme = colors,
        typography = RamkaTypography,
        content = content
    )
}
