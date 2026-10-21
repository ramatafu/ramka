package com.ramka.app.panic

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput

private const val HOLD_MS = 3_000L

/** Серия быстрых нажатий: [taps] штук, от первого до последнего не больше [windowMs]. Чистая логика, без Android. */
class TapBurstDetector(private val taps: Int = 5, private val windowMs: Long = 3_000L) {
    private val times = ArrayDeque<Long>()

    /** @return true, если это нажатие завершило серию (счётчик после этого сбрасывается). */
    fun onTap(nowMs: Long): Boolean {
        times.addLast(nowMs)
        while (times.isNotEmpty() && nowMs - times.first() > windowMs) times.removeFirst()
        if (times.size >= taps) {
            times.clear()
            return true
        }
        return false
    }

    fun reset() = times.clear()
}

/**
 * Скрытый триггер на логотипе (варианты А и Б одним детектором, чтобы они не мешали друг другу):
 *  А) 5 быстрых нажатий в пределах 3 секунд;
 *  Б) удержание 3 секунды.
 * Без ripple и без семантики для TalkBack — снаружи ничем не выдаёт функцию.
 */
@Composable
fun Modifier.panicLogoTrigger(onTrigger: () -> Unit): Modifier {
    val current by rememberUpdatedState(onTrigger)
    val detector = remember { TapBurstDetector() }
    return pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var up: PointerInputChange? = null
            var finished = false
            withTimeoutOrNull(HOLD_MS) {
                up = waitForUpOrCancellation()
                finished = true
            }
            when {
                !finished -> {                 // держали 3 с — вариант Б
                    detector.reset()
                    current()
                    waitForUpOrCancellation()
                }
                up != null ->                  // обычное короткое нажатие — вариант А
                    if (detector.onTap(SystemClock.elapsedRealtime())) current()
            }
        }
    }
}
