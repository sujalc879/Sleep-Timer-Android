package com.sujal.sleeptimer.feature.home

import com.sujal.sleeptimer.core.model.AppSettings
import com.sujal.sleeptimer.core.model.StopResult
import com.sujal.sleeptimer.core.model.StopStatus
import com.sujal.sleeptimer.core.model.TimerLimits
import com.sujal.sleeptimer.core.model.TimerPhase

data class HomeUiState(
    val initialized: Boolean = false,
    val phase: TimerPhase = TimerPhase.IDLE,
    val cancelling: Boolean = false,
    val runId: String? = null,
    val durationMillis: Long = TimerLimits.DEFAULT_DURATION,
    val remainingMillis: Long = 0,
    val endWallMillis: Long = 0,
    val progress: Float = 0f,
    val settings: AppSettings = AppSettings(),
    val finished: StopResult? = null,
    val lastTest: StopResult? = null,
    val currentResult: StopResult? = null,
    val exactAlarmsAllowed: Boolean = false,
    val notificationsAllowed: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val notificationAccess: Boolean = false,
    val shakeMonitoringActive: Boolean = false,
    val screenLockAvailable: Boolean = false,
    val degradedReason: String? = null,
) {
    val running: Boolean get() = runId != null
    val interrupted: Boolean get() = !running && currentResult?.status == StopStatus.INTERRUPTED
    val displayMillis: Long get() = if (running) remainingMillis else durationMillis
    val criticalCount: Int get() = listOf(
        !exactAlarmsAllowed,
        degradedReason != null && exactAlarmsAllowed,
        settings.screenOffAdminRevoked,
        interrupted,
    ).count { it }
    val needsAttention: Boolean get() = criticalCount > 0
}

@Suppress("ReturnCount") // Invalid typed fields fail closed independently.
internal fun customDuration(hours: String, minutes: String): Long? {
    val hour = hours.toIntOrNull() ?: return null
    val minute = minutes.toIntOrNull() ?: return null
    if (hour !in 0..MAX_HOURS || minute !in 0 until MINUTES_PER_HOUR) return null
    return ((hour * MINUTES_PER_HOUR + minute) * TimerLimits.MINUTE).takeIf {
        it in
            TimerLimits.MINUTE..TimerLimits.MAX_DURATION
    }
}

private const val MAX_HOURS = 12
private const val MINUTES_PER_HOUR = 60

@Suppress("MagicNumber") // Product's connected preset choices, expressed directly.
internal val PRESET_MINUTES = listOf(15, 30, 45, 60)
internal const val REDUCE_MILLIS = 5 * TimerLimits.MINUTE

/** Clock-only fields are read by the dial; they must not invalidate the screen layout. */
internal fun HomeUiState.forLayout(): HomeUiState = copy(
    remainingMillis = 0,
    endWallMillis = 0,
    progress = 0f,
    finished = null,
)
