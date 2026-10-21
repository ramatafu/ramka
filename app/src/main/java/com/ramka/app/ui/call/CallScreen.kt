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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ramka.domain.call.CallState
import kotlinx.coroutines.delay
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun CallScreen(
    contactId: String,
    onClose: () -> Unit,
    startWithVideo: Boolean = false,
    viewModel: CallViewModel = activityCallViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val peerName by viewModel.peerName.collectAsState()
    val localTrack by viewModel.localVideoTrack.collectAsState()
    val remoteTrack by viewModel.remoteVideoTrack.collectAsState()
    val context = LocalContext.current

    var deniedMessage by remember { mutableStateOf<String?>(null) }
    var afterPermission by remember { mutableStateOf<((cameraGranted: Boolean) -> Unit)?>(null) }
    
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val action = afterPermission
        afterPermission = null
        if (result[Manifest.permission.RECORD_AUDIO] == true) {
            action?.invoke(result[Manifest.permission.CAMERA] == true)
        } else {
            deniedMessage = CallViewModel.MSG_NO_MIC
            if (state is CallState.Incoming) viewModel.rejectCall()
        }
    }
    
    val withCallPermissions: (video: Boolean, action: (cameraGranted: Boolean) -> Unit) -> Unit = { video, action ->
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        val micOk = granted(Manifest.permission.RECORD_AUDIO)
        val camOk = granted(Manifest.permission.CAMERA)
        if (micOk && (!video || camOk)) {
            action(camOk)
        } else {
            afterPermission = action
            val missing = buildList {
                if (!micOk) add(Manifest.permission.RECORD_AUDIO)
                if (video && !camOk) add(Manifest.permission.CAMERA)
            }
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    var started by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(contactId) {
        if (started) return@LaunchedEffect
        started = true
        val current = viewModel.state.value
        when {
            current is CallState.Idle || current is CallState.Ended || current is CallState.Error ->
                withCallPermissions(startWithVideo) { cameraGranted ->
                    viewModel.startCall(contactId, withVideo = startWithVideo && cameraGranted)
                }
            current.contactIdOrNull() != contactId -> onClose()
        }
    }

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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onUiStopped()
                Lifecycle.Event.ON_START -> viewModel.onUiStarted()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onUiStarted()
        }
    }

    DisposableEffect(Unit) { onDispose { viewModel.acknowledgeFinished() } }

    BackHandler(enabled = state is CallState.Dialing || state is CallState.Connected || state is CallState.Incoming) {}

    CallContent(
        state = state,
        peerName = peerName,
        extraMessage = deniedMessage,
        localVideoTrack = localTrack,
        remoteVideoTrack = remoteTrack,
        eglBaseContext = viewModel.eglBaseContext,
        onAnswer = { withVideo ->
            withCallPermissions(withVideo) { cameraGranted ->
                viewModel.answerCall(contactId, withVideo = withVideo && cameraGranted)
            }
        },
        onReject = viewModel::rejectCall,
        onEndCall = viewModel::endCall,
        onToggleMute = viewModel::toggleMute,
        onToggleVideo = viewModel::toggleVideo,
        onClose = onClose,
    )
}

private fun CallState.contactIdOrNull(): String? = when (this) {
    is CallState.Dialing -> contactId
    is CallState.Incoming -> contactId
    is CallState.Connected -> contactId
    else -> null
}

@Composable
fun CallContent(
    state: CallState,
    peerName: String?,
    extraMessage: String? = null,
    localVideoTrack: VideoTrack? = null,
    remoteVideoTrack: VideoTrack? = null,
    eglBaseContext: EglBase.Context? = null,
    onAnswer: (withVideo: Boolean) -> Unit = {},
    onReject: () -> Unit = {},
    onEndCall: () -> Unit = {},
    onToggleMute: () -> Unit = {},
    onToggleVideo: () -> Unit = {},
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isMuted = (state as? CallState.Connected)?.isMuted ?: false
    val isVideoEnabled = (state as? CallState.Connected)?.isVideoEnabled ?: false
    val showRemote = remoteVideoTrack != null && eglBaseContext != null
    val showLocal = localVideoTrack != null && eglBaseContext != null

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (showRemote) {
            RemoteVideoView(
                track = remoteVideoTrack,
                eglBaseContext = eglBaseContext!!,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
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
        }

        if (showLocal) {
            LocalVideoView(
                track = localVideoTrack,
                eglBaseContext = eglBaseContext!!,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(width = 110.dp, height = 150.dp),
            )
        }

        CallControls(
            state = state,
            isMuted = isMuted,
            isVideoEnabled = isVideoEnabled,
            canToggleVideo = localVideoTrack != null,
            onAnswer = onAnswer,
            onReject = onReject,
            onEndCall = onEndCall,
            onToggleMute = onToggleMute,
            onToggleVideo = onToggleVideo,
            onClose = onClose,
            // navigationBarsPadding() ПЕРЕД padding(24.dp): сначала отступ на высоту системной панели
            // (кнопки или жестовая полоса), затем прежние 24dp. Приложение рисуется под системными панелями
            // (enableEdgeToEdge), поэтому без этого кнопки оказывались под ними.
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(24.dp),
        )
    }
}

private fun statusText(state: CallState): String = when (state) {
    is CallState.Idle -> "Подготовка…"
    is CallState.Dialing -> "Соединение…"
    is CallState.Incoming -> if (state.withVideo) "Входящий видеозвонок" else "Входящий звонок"
    is CallState.Connected -> "Разговор"
    is CallState.Ended -> "Звонок завершён"
    is CallState.Error -> state.message
}

@Composable
fun LocalVideoView(
    track: VideoTrack?,
    eglBaseContext: EglBase.Context,
    modifier: Modifier = Modifier,
) {
    WebRTCVideoView(
        track = track,
        eglBaseContext = eglBaseContext,
        mirror = true,
        modifier = modifier.clip(RoundedCornerShape(12.dp))
    )
}

@Composable
fun RemoteVideoView(
    track: VideoTrack?,
    eglBaseContext: EglBase.Context,
    modifier: Modifier = Modifier,
) {
    WebRTCVideoView(
        track = track,
        eglBaseContext = eglBaseContext,
        mirror = false,
        modifier = modifier
    )
}

// КЛЮЧЕВОЕ ИСПРАВЛЕНИЕ: Правильный жизненный цикл для Compose
@Composable
fun WebRTCVideoView(
    track: VideoTrack?,
    eglBaseContext: EglBase.Context,
    modifier: Modifier = Modifier,
    mirror: Boolean = false,
) {
    val context = LocalContext.current
    // 1. Создаем рендерер ОДИН раз, пока существует этот Composable
    val renderer = remember { SurfaceViewRenderer(context) }

    // 2. Инициализируем и освобождаем рендерер ОДИН раз за жизненный цикл экрана
    DisposableEffect(Unit) {
        renderer.init(eglBaseContext, null)
        renderer.setMirror(mirror)
        renderer.setEnableHardwareScaler(true)
        renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        
        onDispose {
            renderer.release()
        }
    }

    // 3. Привязываем видеопоток. Перезапускается ТОЛЬКО если изменился сам трек
    DisposableEffect(track) {
        track?.addSink(renderer)
        onDispose {
            track?.removeSink(renderer)
        }
    }

    // 4. Встраиваем в Compose
    AndroidView(
        factory = { renderer },
        modifier = modifier
    )
}

@Composable
private fun CallControls(
    state: CallState,
    isMuted: Boolean,
    isVideoEnabled: Boolean,
    canToggleVideo: Boolean,
    onAnswer: (withVideo: Boolean) -> Unit,
    onReject: () -> Unit,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleVideo: () -> Unit,
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
                CircleCallButton("📵", "Отклонить", CallButtonStyle.Danger, onReject)
                if (state.withVideo) {
                    CircleCallButton("📞", "Ответить голосом", CallButtonStyle.Tonal) { onAnswer(false) }
                    CircleCallButton("📹", "Ответить с видео", CallButtonStyle.Primary) { onAnswer(true) }
                } else {
                    CircleCallButton("📞", "Принять", CallButtonStyle.Primary) { onAnswer(false) }
                }
            }
            is CallState.Dialing -> CircleCallButton("📵", "Завершить звонок", CallButtonStyle.Danger, onEndCall)
            is CallState.Connected -> {
                CircleCallButton(
                    emoji = if (isMuted) "🔇" else "🎤",
                    description = if (isMuted) "Микрофон выключен, включить" else "Микрофон включён, выключить",
                    style = CallButtonStyle.Tonal,
                    onClick = onToggleMute
                )
                if (canToggleVideo) {
                    CircleCallButton(
                        emoji = if (isVideoEnabled) "📹" else "📷",
                        description = if (isVideoEnabled) "Камера включена, выключить" else "Камера выключена, включить",
                        style = CallButtonStyle.Tonal,
                        onClick = onToggleVideo
                    )
                }
                CircleCallButton("📵", "Завершить звонок", CallButtonStyle.Danger, onEndCall)
            }
            is CallState.Ended, is CallState.Error -> CircleCallButton("❌", "Закрыть", CallButtonStyle.Tonal, onClose)
            is CallState.Idle -> CircleCallButton("❌", "Отмена", CallButtonStyle.Danger, onClose)
        }
    }
}

private enum class CallButtonStyle { Primary, Tonal, Danger }

/**
 * Круглая кнопка звонка с одним эмодзи (в стиле остального приложения: 📹 📞 📎 текстом).
 * Цвета прежние: основной (Primary), тональный (Tonal), красный (Danger). Подпись для TalkBack —
 * в [description]; сам эмодзи из семантики убран, чтобы он не зачитывался дважды.
 */
@Composable
private fun CircleCallButton(
    emoji: String,
    description: String,
    style: CallButtonStyle,
    onClick: () -> Unit,
) {
    val colors = when (style) {
        CallButtonStyle.Primary -> ButtonDefaults.buttonColors()
        CallButtonStyle.Tonal -> ButtonDefaults.filledTonalButtonColors()
        CallButtonStyle.Danger -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        )
    }
    Button(
        onClick = onClick,
        modifier = Modifier
            .size(64.dp)
            .semantics { contentDescription = description },
        shape = CircleShape,
        colors = colors,
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}
