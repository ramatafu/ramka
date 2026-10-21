package com.ramka.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Ключ элемента в LazyColumn: не пересекается с localId сообщений. */
internal const val ENCRYPTION_BANNER_KEY = "ramka:encryption-info"

private const val ENCRYPTION_INFO_TEXT =
    "Сообщения защищены сквозным шифрованием (Noise IK). Ключи хранятся только на ваших устройствах."

/**
 * Системная подсказка о шифровании в начале чата. Не сообщение: без пузыря, времени, статуса и записи в БД.
 * Текст по центру, приглушённого цвета, отступы сверху и снизу 16dp.
 */
@Composable
fun EncryptionInfoBanner(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.secondary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null, // декоративный: смысл несёт текст
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = ENCRYPTION_INFO_TEXT,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}
