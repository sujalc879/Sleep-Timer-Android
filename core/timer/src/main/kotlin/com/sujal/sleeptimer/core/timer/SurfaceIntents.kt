package com.sujal.sleeptimer.core.timer

import com.sujal.sleeptimer.core.model.StartSource
import com.sujal.sleeptimer.core.model.TimerLimits

sealed interface SurfaceRequest {
    data object Custom : SurfaceRequest
    data class Start(
        val duration: Long?,
        val source: StartSource,
        val onlyIfIdle: Boolean = false,
        val shortcutId: String? = null,
    ) : SurfaceRequest
}

object SurfaceIntents {
    const val START_30 = "com.sujal.sleeptimer.action.START_30"
    const val START_60 = "com.sujal.sleeptimer.action.START_60"
    const val CUSTOM = "com.sujal.sleeptimer.action.CUSTOM"
    const val WIDGET_LAST = "com.sujal.sleeptimer.action.WIDGET_START_LAST"
    const val WIDGET_PRESET = "com.sujal.sleeptimer.action.WIDGET_PRESET"
    const val MINUTES = "preset_minutes"

    @Suppress("MagicNumber") // Fixed product presets and shortcut durations.
    fun parse(action: String?, minutes: Int = -1): SurfaceRequest? = when (action) {
        START_30 -> SurfaceRequest.Start(30 * TimerLimits.MINUTE, StartSource.SHORTCUT, shortcutId = "start_30")

        START_60 -> SurfaceRequest.Start(60 * TimerLimits.MINUTE, StartSource.SHORTCUT, shortcutId = "start_60")

        CUSTOM -> SurfaceRequest.Custom

        WIDGET_LAST -> SurfaceRequest.Start(null, StartSource.WIDGET, onlyIfIdle = true)

        WIDGET_PRESET -> if (minutes in
            setOf(15, 30, 45, 60)
        ) {
            SurfaceRequest.Start(minutes * TimerLimits.MINUTE, StartSource.WIDGET)
        } else {
            null
        }

        else -> null
    }
}
