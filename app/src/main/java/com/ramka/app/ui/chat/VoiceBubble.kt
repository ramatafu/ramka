package com.ramka.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ramka.app.ui.theme.RamkaAccent
import com.ramka.domain.model.MessageBody
import com.ramka.domain.model.MessageStatus
import com.ramka.domain.model.VoiceMessage
import com.ramka.domain.model.VoiceMessages

/**
 * Содержимое пузыря голосового сообщения: Play/Pause, простая статическая «волна» и длительность «0:45».
 * Фон, время и галочки статуса рисует [MessageBubble] — так же, как у текста. Пока файл передаётся или
 * принимается, под строкой — прогресс; у исходящего с ошибкой — «Повторить» (как у обычных файлов).
 * Нажать Play можно, только когда файл есть на устройстве ([isFileOpenable]).
 */
@Composable
internal fun VoiceBubble(
    body: MessageBody.File,
    voice: VoiceMessage,
    isOutgoing: Boolean,
    status: MessageStatus,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onRetry: () -> Unit,
    signalSent: Boolean = false
) {
    val playable = isFileOpenable(body, isOutgoing, status)
    Column(modifier = Modifier.widthIn(min = 160.dp)) {
        Row(
            modifier = Modifier.clickable(enabled = playable, onClick = onPlayPause),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Пауза" else "Воспроизвести",
                tint = if (playable) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(8.dp))
            VoiceWave(active = isPlaying)
            Spacer(Modifier.width(8.dp))
            Text(
                text = VoiceMessages.formatDuration(voice.durationSeconds),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        if (status == MessageStatus.SENDING || status == MessageStatus.FAILED) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = fileStatusLine(body, isOutgoing, status),
                style = MaterialTheme.typography.labelSmall,
                color = if (status == MessageStatus.FAILED) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.secondary
            )
            offlineSignalNote(isOutgoing, status, signalSent)?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        if (status == MessageStatus.SENDING) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { body.progress.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = RamkaAccent
            )
        }
        if (isOutgoing && status == MessageStatus.FAILED) {
            TextButton(onClick = onRetry) { Text("Повторить") }
        }
    }
}

/** Пять вертикальных штрихов разной высоты: только визуальное отличие от текста, к звуку не привязаны. */
@Composable
private fun VoiceWave(active: Boolean) {
    val color = if (active) RamkaAccent else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WAVE_HEIGHTS_DP.forEach { height ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height.dp)
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
    }
}

private val WAVE_HEIGHTS_DP = listOf(8, 16, 22, 12, 18)
