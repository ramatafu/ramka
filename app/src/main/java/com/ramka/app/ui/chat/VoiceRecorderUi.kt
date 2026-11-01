package com.ramka.app.ui.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.ramka.domain.model.VoiceMessages

/** На сколько нужно сдвинуть палец влево, чтобы запись отменилась. */
private const val CANCEL_DRAG_DP = 96

/**
 * Кнопка микрофона. Долгое нажатие — старт записи, отпускание — отправка, сдвиг пальца влево
 * дальше [CANCEL_DRAG_DP] и отпускание — отмена. Короткое нажатие подсказывает, что кнопку надо удерживать.
 *
 * Без разрешения на микрофон запись не начинается: жест тратится на запрос разрешения (системный диалог
 * прервал бы удержание), после выдачи нужно нажать и удерживать ещё раз.
 * Жест один и тот же во время записи — кнопка не пересоздаётся, поэтому палец не «теряется».
 */
@Composable
internal fun VoiceRecordButton(
    recording: Boolean,
    willCancel: Boolean,
    hasPermission: () -> Boolean,
    onRequestPermission: () -> Unit,
    onTap: () -> Unit,
    onRecordStart: () -> Unit,
    onDragStateChange: (willCancel: Boolean) -> Unit,
    onRecordEnd: (cancelled: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val cancelDistancePx = with(LocalDensity.current) { CANCEL_DRAG_DP.dp.toPx() }
    // pointerInput(Unit) живёт всё время: актуальные колбэки берём через rememberUpdatedState.
    val hasPermissionState by rememberUpdatedState(hasPermission)
    val requestPermissionState by rememberUpdatedState(onRequestPermission)
    val tapState by rememberUpdatedState(onTap)
    val startState by rememberUpdatedState(onRecordStart)
    val dragState by rememberUpdatedState(onDragStateChange)
    val endState by rememberUpdatedState(onRecordEnd)

    val background = when {
        !recording -> null
        willCancel -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .then(if (background != null) Modifier.background(background) else Modifier)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!hasPermissionState()) {
                        requestPermissionState()
                        waitForUpOrCancellation()
                        return@awaitEachGesture
                    }
                    // Долгое нажатие: если палец не отпущен за longPressTimeout — это запись, иначе обычный тап.
                    val releasedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        var up = false
                        while (!up) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                            up = change == null || !change.pressed
                        }
                        true
                    }
                    if (releasedEarly != null) {
                        tapState()
                        return@awaitEachGesture
                    }

                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    startState()
                    var cancelled = false
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val nowCancelled = (change.position.x - down.position.x) < -cancelDistancePx
                        if (nowCancelled != cancelled) {
                            cancelled = nowCancelled
                            dragState(cancelled)
                        }
                        change.consume()
                    }
                    endState(cancelled)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = "Голосовое сообщение: удерживайте для записи",
            tint = if (recording) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Строка поля ввода во время записи: ✕ (красная, слева), пульсирующая точка, таймер и подсказка про свайп.
 * Основной способ отмены — свайп влево, пока палец на микрофоне; ✕ работает от второго пальца или после
 * автоматической остановки. При приближении к порогу отмены подсказка меняется на «Отпустите для отмены».
 */
@Composable
internal fun RecordingBar(
    seconds: Int,
    willCancel: Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulse by rememberInfiniteTransition(label = "rec-dot").animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 700), RepeatMode.Reverse),
        label = "rec-dot-alpha"
    )
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onCancel) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Отменить запись",
                tint = MaterialTheme.colorScheme.error
            )
        }
        Box(
            modifier = Modifier
                .size(10.dp)
                .alpha(pulse)
                .background(MaterialTheme.colorScheme.error, CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = VoiceMessages.formatDuration(seconds),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = if (willCancel) "Отпустите, чтобы отменить" else "Свайп влево — отмена",
            style = MaterialTheme.typography.labelSmall,
            color = if (willCancel) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
        )
    }
}
