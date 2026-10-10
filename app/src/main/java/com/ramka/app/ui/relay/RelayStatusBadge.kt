package com.ramka.app.ui.relay

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Бейдж статуса релея: «выключен / неизвестен / работает / ошибка / проверка». */
@Composable
fun RelayStatusBadge(status: RelayBadgeState, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (text, background, content) = when (status) {
        RelayBadgeState.Disabled -> Triple("выключен", scheme.surfaceVariant, scheme.onSurfaceVariant)
        RelayBadgeState.Unknown -> Triple("неизвестен", scheme.surfaceVariant, scheme.onSurfaceVariant)
        RelayBadgeState.Checking -> Triple("проверка", scheme.secondaryContainer, scheme.onSecondaryContainer)
        RelayBadgeState.Ok -> Triple("работает", scheme.primaryContainer, scheme.onPrimaryContainer)
        is RelayBadgeState.Error -> Triple("ошибка", scheme.errorContainer, scheme.onErrorContainer)
    }
    Badge(text, background, content, modifier)
}

@Composable
private fun Badge(text: String, background: Color, content: Color, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = background,
        contentColor = content
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
