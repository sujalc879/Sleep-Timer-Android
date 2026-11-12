package com.sujal.sleeptimer.core.designsystem

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import kotlin.math.roundToInt

@Composable
internal fun Modifier.dialInput(presentation: DialPresentation, update: (Int) -> Unit, finish: () -> Unit): Modifier {
    val current by rememberUpdatedState(presentation)
    return if (presentation.running || presentation.message != null) {
        this
    } else {
        this
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    presentation.minutes.coerceIn(1, MINUTES_PER_TURN).toFloat(),
                    1f..MINUTES_PER_TURN.toFloat(),
                    MINUTES_PER_TURN - 2,
                )
                setProgress { value ->
                    if (value.isFinite()) {
                        update(value.roundToInt())
                        finish()
                        true
                    } else {
                        false
                    }
                }
                customActions = listOf(
                    CustomAccessibilityAction(presentation.increaseLabel) {
                        update(current.minutes + 1)
                        finish()
                        true
                    },
                    CustomAccessibilityAction(presentation.decreaseLabel) {
                        update(current.minutes - 1)
                        finish()
                        true
                    },
                    CustomAccessibilityAction(presentation.increaseFiveLabel) {
                        update(current.minutes + FIVE_MINUTES)
                        finish()
                        true
                    },
                    CustomAccessibilityAction(presentation.decreaseFiveLabel) {
                        update(current.minutes - FIVE_MINUTES)
                        finish()
                        true
                    },
                )
            }
            .onKeyEvent { event ->
                val next = event.dialTarget(current.minutes)
                if (next == null) {
                    false
                } else {
                    update(next)
                    finish()
                    true
                }
            }
            .focusable()
    }
}

private fun KeyEvent.dialTarget(minutes: Int): Int? = if (type != KeyEventType.KeyDown) {
    null
} else {
    when (key) {
        Key.DirectionUp, Key.DirectionRight -> minutes + 1
        Key.DirectionDown, Key.DirectionLeft -> minutes - 1
        Key.PageUp -> minutes + FIVE_MINUTES
        Key.PageDown -> minutes - FIVE_MINUTES
        Key.MoveHome -> 1
        Key.MoveEnd -> MINUTES_PER_TURN
        else -> null
    }
}

private const val FIVE_MINUTES = 5
