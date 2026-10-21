package com.ramka.app.panic

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private const val FADE_MS = 300
private const val PAUSE_BETWEEN_LINES_MS = 800L
private val TerminalTextColor = Color.White // для зелёного терминала: Color(0xFF00FF41)

/**
 * Оверлей экстренного сброса: чёрный экран и две строки моноширинным шрифтом.
 * Показывается только пока PanicButtonManager.blackout == true, поэтому отсчёт анимации
 * стартует в момент срабатывания. Касания поглощаются, интерактивных элементов нет.
 *
 * Время: строка 1 появляется 0,3 с; через 0,8 с — строка 2 (ещё 0,3 с). Дальше текст просто висит.
 */
@Composable
fun PanicBlackout() {
    val firstLine = remember { Animatable(0f) }
    val secondLine = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        firstLine.animateTo(1f, tween(FADE_MS, easing = LinearEasing))
        delay(PAUSE_BETWEEN_LINES_MS)
        secondLine.animateTo(1f, tween(FADE_MS, easing = LinearEasing))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent() }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Привет, друг.",
                color = TerminalTextColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = firstLine.value }
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Данные уничтожены.",
                color = TerminalTextColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = secondLine.value }
            )
        }
    }
}
