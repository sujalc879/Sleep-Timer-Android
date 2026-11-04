package com.sujal.sleeptimer.core.designsystem

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.*
import kotlin.math.roundToInt

@Composable
internal fun dialInput(presentation: DialPresentation, update: (Int) -> Unit, finish: () -> Unit): Modifier {
    val current by rememberUpdatedState(presentation)
    return if (presentation.running || presentation.message != null) Modifier else Modifier
        .semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(presentation.minutes.coerceIn(1, MINUTES_PER_TURN).toFloat(), 1f..MINUTES_PER_TURN.toFloat(), MINUTES_PER_TURN - 2)
            setProgress { value ->
                if (value.isFinite()) { update(value.roundToInt()); finish(); true } else false
            }
            customActions = listOf(
                CustomAccessibilityAction(presentation.increaseLabel) { update(current.minutes + 1); finish(); true },
                CustomAccessibilityAction(presentation.decreaseLabel) { update(current.minutes - 1); finish(); true },
            )
        }
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                Key.DirectionUp, Key.DirectionRight -> { update(current.minutes + 1); finish(); true }
                Key.DirectionDown, Key.DirectionLeft -> { update(current.minutes - 1); finish(); true }
                Key.MoveHome -> { update(1); finish(); true }
                Key.MoveEnd -> { update(MINUTES_PER_TURN); finish(); true }
                else -> false
            }
        }
        .focusable()
}
