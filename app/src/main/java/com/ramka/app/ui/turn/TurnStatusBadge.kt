package com.ramka.app.ui.turn

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ramka.app.turn.TurnServerStatus

/** Пояснение под адресом недоступного сервера. */
const val TURN_UNREACHABLE_HINT =
    "TURN-сервер недоступен: проверьте адрес, порт, протокол (UDP/TCP) и интернет"

private val ConnectingContainer = Color(0xFF5C4300)
private val ConnectingContent = Color(0xFFFFD98A)

/** Бейдж статуса TURN-сервера в стиле бейджа релея; цвета меняются плавно (0,3 с). */
@Composable
fun TurnStatusBadge(status: TurnServerStatus, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (text, background, content) = when (status) {
        TurnServerStatus.WORKING -> Triple("работает", scheme.primaryContainer, scheme.onPrimaryContainer)
        TurnServerStatus.CONNECTING -> Triple("подключается", ConnectingContainer, ConnectingContent)
        TurnServerStatus.UNREACHABLE -> Triple("недоступен", scheme.errorContainer, scheme.onErrorContainer)
    }
    val animatedBackground by animateColorAsState(background, tween(300), label = "turnBadgeBackground")
    val animatedContent by animateColorAsState(content, tween(300), label = "turnBadgeContent")
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = animatedBackground,
        contentColor = animatedContent
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
