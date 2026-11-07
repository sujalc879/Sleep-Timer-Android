package com.sujal.sleeptimer.surface.widget

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sujal.sleeptimer.core.model.AppSettings
import com.sujal.sleeptimer.core.model.ThemeMode
import com.sujal.sleeptimer.core.model.TimerLimits
import com.sujal.sleeptimer.core.model.TimerPhase
import com.sujal.sleeptimer.core.model.TimerSnapshot
import com.sujal.sleeptimer.core.model.isActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class WidgetPresentation(
    val active: Boolean = false,
    val runId: String? = null,
    val phase: TimerPhase = TimerPhase.IDLE,
    val endElapsed: Long = 0,
    val totalDuration: Long = TimerLimits.DEFAULT_DURATION,
    val selected: Long = TimerLimits.DEFAULT_DURATION,
    val renderElapsed: Long = 0,
    val boot: Int = -1,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamic: Boolean = true,
    val sessionEpoch: String = PROCESS_EPOCH,
) {
    val ringFraction: Float get() = if (active) {
        ((endElapsed - renderElapsed).toDouble() / totalDuration.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
    } else {
        (selected.toFloat() / TimerLimits.MINUTE / DIAL_MINUTES).coerceIn(0f, 1f)
    }
}

internal fun widgetPresentation(snapshot: TimerSnapshot, settings: AppSettings, now: Long, boot: Int) =
    WidgetPresentation(
        snapshot.isActive, snapshot.run?.id, snapshot.phase, snapshot.run?.endElapsed ?: 0,
        snapshot.run?.totalDurationMillis ?: snapshot.selectedDurationMillis, snapshot.selectedDurationMillis,
        now, boot, settings.theme, settings.dynamicColor,
    )

internal fun MutablePreferences.writePresentation(value: WidgetPresentation) {
    this[WIDGET_STATE] = widgetJson.encodeToString(value)
}

internal fun Preferences.readPresentation(initial: WidgetPresentation): WidgetPresentation {
    val value = this[WIDGET_STATE]?.takeIf { it.length <= MAX_STATE_LENGTH } ?: return initial
    return try {
        widgetJson.decodeFromString<WidgetPresentation>(value).takeIf {
            it.boot == initial.boot && it.sessionEpoch == initial.sessionEpoch &&
                it.renderElapsed >= initial.renderElapsed &&
                it.totalDuration > 0 &&
                it.selected in TimerLimits.MINUTE..TimerLimits.MAX_DURATION
        } ?: initial
    } catch (_: IllegalArgumentException) {
        initial
    }
}

private val WIDGET_STATE = stringPreferencesKey("presentation_v1")
private const val DIAL_MINUTES = 60

private val widgetJson = Json { encodeDefaults = true }
private val PROCESS_EPOCH = java.util.UUID.randomUUID().toString()
private const val MAX_STATE_LENGTH = 4_096
