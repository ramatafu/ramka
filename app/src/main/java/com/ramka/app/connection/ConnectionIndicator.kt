package com.ramka.app.connection

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ramka.app.R

private val ColorP2p = Color(0xFF00FF41)
private val ColorTurn = Color(0xFFFFD700)
private val ColorRelay = Color(0xFF00BFFF)
private val ColorOffline = Color(0xFF808080)

private const val GLOW_ALPHA = 0.45f
private const val CHANGE_MS = 300

fun ConnectionState.indicatorColor(): Color = when (this) {
    ConnectionState.P2P -> ColorP2p
    ConnectionState.TURN -> ColorTurn
    ConnectionState.RELAY -> ColorRelay
    ConnectionState.OFFLINE -> ColorOffline
}

/**
 * Логотип приложения, окрашенный в цвет типа соединения, с мягким свечением вокруг (для OFFLINE —
 * серый без свечения). Цвет и свечение меняются плавно за 0,3 с. Касаний не обрабатывает и семантики
 * не добавляет: размер, отступы и триггер паники задаёт вызывающий через [modifier].
 */
@Composable
fun ConnectionLogo(state: ConnectionState, modifier: Modifier = Modifier) {
    val color by animateColorAsState(
        targetValue = state.indicatorColor(),
        animationSpec = tween(durationMillis = CHANGE_MS),
        label = "connectionLogoColor"
    )
    val glow by animateFloatAsState(
        targetValue = if (state == ConnectionState.OFFLINE) 0f else GLOW_ALPHA,
        animationSpec = tween(durationMillis = CHANGE_MS),
        label = "connectionLogoGlow"
    )
    Icon(
        painter = painterResource(R.drawable.ic_ramka_logo),
        contentDescription = null, // декоративный: рядом название приложения
        tint = color,
        modifier = modifier.drawBehind {
            if (glow > 0f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = glow), Color.Transparent),
                        center = center,
                        radius = size.minDimension * 0.9f
                    ),
                    radius = size.minDimension * 0.9f
                )
            }
        }
    )
}
