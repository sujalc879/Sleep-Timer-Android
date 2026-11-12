package com.sujal.sleeptimer.feature.home

import com.sujal.sleeptimer.core.model.AppSettings
import com.sujal.sleeptimer.core.model.StopResult
import com.sujal.sleeptimer.core.model.StopStatus
import com.sujal.sleeptimer.core.model.TimerLimits
import com.sujal.sleeptimer.core.model.TimerPhase
import java.text.NumberFormat
import java.util.Locale

data class HomeUiState(
    val initialized: Boolean = false,
    val phase: TimerPhase = TimerPhase.IDLE,
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

internal fun countdown(millis: Long, locale: Locale): String {
    val seconds = ((millis.coerceAtLeast(0) + TimerLimits.SECOND - 1) / TimerLimits.SECOND)
    val number = NumberFormat.getIntegerInstance(locale).apply {
        isGroupingUsed = false
        minimumIntegerDigits = 2
    }
    val second = number.format(seconds % SECONDS_PER_MINUTE)
    return if (seconds >= SECONDS_PER_HOUR) {
        val hour = NumberFormat.getIntegerInstance(locale).apply {
            isGroupingUsed = false
        }.format(seconds / SECONDS_PER_HOUR)
        "$hour:${number.format(seconds / SECONDS_PER_MINUTE % SECONDS_PER_MINUTE)}:$second"
    } else {
        "${number.format(seconds / SECONDS_PER_MINUTE)}:$second"
    }
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
private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3_600

@Suppress("MagicNumber") // Product's connected preset choices, expressed directly.
internal val PRESET_MINUTES = listOf(15, 30, 45, 60)
internal const val REDUCE_MILLIS = 5 * TimerLimits.MINUTE
