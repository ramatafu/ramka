package com.ramka.app.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * ШАГ 7.1 — UI-скелет экрана звонка. Заглушка: не подключён к навигации, не знает про WebRTC.
 *
 * Состояние и действия приходят параметрами (state hoisting), чтобы позже подключить CallManager
 * без переписывания разметки. Видео-области пока обычные Box с подписью.
 */
@Composable
fun CallScreen(
    modifier: Modifier = Modifier,
    isMuted: Boolean = false,
    isVideoEnabled: Boolean = true,
    onEndCall: () -> Unit = {},
    onToggleMute: () -> Unit = {},
    onToggleVideo: () -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        RemoteVideoView(modifier = Modifier.fillMaxSize())

        LocalVideoView(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(width = 110.dp, height = 160.dp),
        )

        CallControls(
            isMuted = isMuted,
            isVideoEnabled = isVideoEnabled,
            onEndCall = onEndCall,
            onToggleMute = onToggleMute,
            onToggleVideo = onToggleVideo,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp),
        )
    }
}

/** Заглушка превью собственной камеры (позже — SurfaceViewRenderer/VideoTrack). */
@Composable
fun LocalVideoView(modifier: Modifier = Modifier) {
    VideoPlaceholder(label = "Вы", modifier = modifier)
}

/** Заглушка видео собеседника (позже — SurfaceViewRenderer/VideoTrack). */
@Composable
fun RemoteVideoView(modifier: Modifier = Modifier) {
    VideoPlaceholder(label = "Собеседник", modifier = modifier, rounded = false)
}

@Composable
private fun VideoPlaceholder(
    label: String,
    modifier: Modifier = Modifier,
    rounded: Boolean = true,
) {
    val shape = if (rounded) RoundedCornerShape(12.dp) else RoundedCornerShape(0.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CallControls(
    isMuted: Boolean,
    isVideoEnabled: Boolean,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleVideo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalButton(onClick = onToggleMute) {
            Text(if (isMuted) "Микрофон выкл." else "Микрофон")
        }
        Button(
            onClick = onEndCall,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Text("Завершить")
        }
        FilledTonalButton(onClick = onToggleVideo) {
            Text(if (isVideoEnabled) "Камера" else "Камера выкл.")
        }
    }
}
