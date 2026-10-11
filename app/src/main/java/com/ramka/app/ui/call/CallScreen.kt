package com.ramka.app.ui.call

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ramka.domain.call.CallState
import kotlinx.coroutines.delay

/**
 * Экран звонка (шаг 7.3, только аудио). Маршрут `call/{contactId}`.
 *
 * Открывается двумя путями: из чата (звонка ещё нет — запрашиваем микрофон и начинаем исходящий) и
 * автоматически по входящему звонку (состояние [CallState.Incoming] — показываем «Принять/Отклонить»).
 * Состояние и логика — в [CallViewModel] (одна на Activity); здесь только отображение и запрос разрешения.
 */
@Composable
fun CallScreen(
    contactId: String,
    onClose: () -> Unit,
    viewModel: CallViewModel = activityCallViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val peerName by viewModel.peerName.collectAsState()
    val context = LocalContext.current

    var deniedMessage by remember { mutableStateOf<String?>(null) }
    var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = afterPermission
        afterPermission = null
        if (granted) {
            action?.invoke()
        } else {
            deniedMessage = CallViewModel.MSG_NO_MIC
            // Входящий без микрофона принять нельзя — отклоняем, иначе он завис бы до таймаута.
            if (state is CallState.Incoming) viewModel.rejectCall()
        }
    }
    val withMicPermission: (() -> Unit) -> Unit = { action ->
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            action()
        } else {
            afterPermission = action
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Исходящий звонок: экран открыт из чата, активного звонка нет. rememberSaveable — чтобы поворот экрана
    // после завершения звонка не начал новый.
    var started by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(contactId) {
        if (started) return@LaunchedEffect
        started = true
        val current = viewModel.state.value
        when {
            current is CallState.Idle || current is CallState.Ended || current is CallState.Error ->
                withMicPermission { viewModel.startCall(contactId, withVideo = false) }
            // Идёт звонок с другим контактом — не подменяем его экраном этого контакта.
            current.contactIdOrNull() != contactId -> onClose()
        }
    }

    // Итог звонка показываем недолго и закрываем экран.
    LaunchedEffect(state) {
        when (state) {
            is CallState.Ended -> { delay(800); onClose() }
            is CallState.Error -> { delay(3_000); onClose() }
            else -> Unit
        }
    }
    LaunchedEffect(deniedMessage) {
        if (deniedMessage != null) { delay(2_500); onClose() }
    }

    // После закрытия возвращаем ViewModel в Idle, чтобы следующий звонок начался с чистого состояния.
    DisposableEffect(Unit) { onDispose { viewModel.acknowledgeFinished() } }

    // Пока звонок идёт, «Назад» не должен оставить его без экрана и без кнопки завершения.
    BackHandler(enabled = state is CallState.Dialing || state is CallState.Connected || state is CallState.Incoming) {}

    CallContent(
        state = state,
        peerName = peerName,
        extraMessage = deniedMessage,
        onAnswer = { withMicPermission { viewModel.answerCall(contactId, withVideo = false) } },
        onReject = viewModel::rejectCall,
        onEndCall = viewModel::endCall,
        onToggleMute = viewModel::toggleMute,
        onClose = onClose,
    )
}

private fun CallState.contactIdOrNull(): String? = when (this) {
    is CallState.Dialing -> contactId
    is CallState.Incoming -> contactId
    is CallState.Connected -> contactId
    else -> null
}

/** Отображение без логики (state hoisting): удобно для превью и для видео-версии в 7.4. */
@Composable
fun CallContent(
    state: CallState,
    peerName: String?,
    extraMessage: String? = null,
    onAnswer: () -> Unit = {},
    onReject: () -> Unit = {},
    onEndCall: () -> Unit = {},
    onToggleMute: () -> Unit = {},
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isMuted = (state as? CallState.Connected)?.isMuted ?: false

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = peerName ?: "…",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = extraMessage ?: statusText(state),
                style = MaterialTheme.typography.bodyLarge,
                color = if (state is CallState.Error || extraMessage != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
            )
        }

        CallControls(
            state = state,
            isMuted = isMuted,
            onAnswer = onAnswer,
            onReject = onReject,
            onEndCall = onEndCall,
            onToggleMute = onToggleMute,
            onClose = onClose,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp),
        )
    }
}

private fun statusText(state: CallState): String = when (state) {
    is CallState.Idle -> "Подготовка…"
    is CallState.Dialing -> "Соединение…"
    is CallState.Incoming ->
        if (state.withVideo) "Входящий звонок\nВидео пока не поддерживается — разговор будет голосовым" else "Входящий звонок"
    is CallState.Connected -> "Разговор"
    is CallState.Ended -> "Звонок завершён"
    is CallState.Error -> state.message
}

/** Заглушка превью собственной камеры (позже — SurfaceViewRenderer/VideoTrack, шаг 7.4). */
@Composable
fun LocalVideoView(modifier: Modifier = Modifier) {
    VideoPlaceholder(label = "Вы", modifier = modifier)
}

/** Заглушка видео собеседника (позже — SurfaceViewRenderer/VideoTrack, шаг 7.4). */
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
    state: CallState,
    isMuted: Boolean,
    onAnswer: () -> Unit,
    onReject: () -> Unit,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state) {
            is CallState.Incoming -> {
                DangerButton(text = "Отклонить", onClick = onReject)
                Button(onClick = onAnswer) { Text("Принять") }
            }
            is CallState.Dialing -> DangerButton(text = "Завершить", onClick = onEndCall)
            is CallState.Connected -> {
                FilledTonalButton(onClick = onToggleMute) {
                    Text(if (isMuted) "Микрофон выкл." else "Микрофон")
                }
                DangerButton(text = "Завершить", onClick = onEndCall)
                // Кнопка камеры вернётся в шаге 7.4 вместе с видео.
            }
            is CallState.Ended, is CallState.Error -> FilledTonalButton(onClick = onClose) { Text("Закрыть") }
            is CallState.Idle -> DangerButton(text = "Отмена", onClick = onClose)
        }
    }
}

@Composable
private fun DangerButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
    ) {
        Text(text)
    }
}
