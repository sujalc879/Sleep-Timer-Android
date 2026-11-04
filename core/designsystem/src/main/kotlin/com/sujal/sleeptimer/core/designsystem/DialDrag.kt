package com.sujal.sleeptimer.core.designsystem

import kotlin.math.roundToInt

/** Continuous bounded angle: crossing twelve cannot wrap a maximum into a minimum. */
class DialDrag(initialMinutes: Int, pointerAngle: Float) {
    private var angle = initialMinutes.coerceIn(1, MINUTES_PER_TURN) * DEGREES_PER_MINUTE
    private var previous = pointerAngle

    fun move(pointerAngle: Float): Int {
        var delta = pointerAngle - previous
        if (delta > HALF_TURN) delta -= FULL_TURN
        if (delta < -HALF_TURN) delta += FULL_TURN
        angle = (angle + delta).coerceIn(DEGREES_PER_MINUTE, FULL_TURN)
        previous = pointerAngle
        return (angle / DEGREES_PER_MINUTE).roundToInt().coerceIn(1, MINUTES_PER_TURN)
    }
}

internal const val MINUTES_PER_TURN = 60
internal const val DEGREES_PER_MINUTE = 6f
internal const val FULL_TURN = 360f
internal const val HALF_TURN = 180f
