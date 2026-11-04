package com.sujal.sleeptimer.core.model

import kotlinx.serialization.Serializable

object TimerLimits {
    const val SECOND = 1_000L
    const val MINUTE = 60_000L
    const val MAX_DURATION = 12 * 60 * MINUTE
    const val DEFAULT_DURATION = 30 * MINUTE
    const val RESTORE_WINDOW = 2 * MINUTE
    const val LATE_STOP_WINDOW = 30 * MINUTE
    const val DEFER_WINDOW = 2 * 60 * MINUTE
}

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
enum class WarningMode { OFF, SOFT, STRONG }

@Serializable
data class AppSettings(
    val schemaVersion: Int = 1,
    val lastDurationMillis: Long = TimerLimits.DEFAULT_DURATION,
    val fadeMillis: Long = DEFAULT_FADE,
    val screenOff: Boolean = false,
    val restoreVolume: Boolean = true,
    val shakeToExtend: Boolean = true,
    val extendMillis: Long = DEFAULT_EXTENSION,
    val warning: WarningMode = WarningMode.SOFT,
    val nightlyEnabled: Boolean = false,
    val nightlyHour: Int = DEFAULT_NIGHTLY_HOUR,
    val nightlyMinute: Int = 0,
    val nightlyDays: Set<Int> = WEEK_DAYS,
    val nightlyDurationMillis: Long = TimerLimits.DEFAULT_DURATION,
    val dynamicColor: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dimWhileRunning: Boolean = true,
) {
    fun isValid(): Boolean = schemaVersion == 1 && lastDurationMillis in TimerLimits.MINUTE..TimerLimits.MAX_DURATION &&
        fadeMillis in 0..TimerLimits.MINUTE && fadeMillis % FADE_STEP == 0L &&
        extendMillis in EXTENSION_OPTIONS &&
        nightlyHour in 0..LAST_HOUR && nightlyMinute in 0..LAST_MINUTE && nightlyDays.all { it in WEEK_DAYS } &&
        nightlyDurationMillis in TimerLimits.MINUTE..TimerLimits.MAX_DURATION
}

@Serializable
enum class TimerPhase { IDLE, RUNNING, FADING, STOPPING, DEFERRED, FINISHED }

@Serializable
enum class StartSource { ACTIVITY, NOTIFICATION, WIDGET, TILE, SHORTCUT, NIGHTLY, RESTART, TEST }

@Serializable
enum class AlarmKind { FINAL_WINDOW, BACKUP }

@Serializable
data class OutputRoute(val identity: String)

@Serializable
data class VolumeRecord(
    val originalIndex: Int,
    val minimumIndex: Int,
    val lastWrittenIndex: Int,
    val route: OutputRoute,
    val capturedElapsed: Long,
    val expiresElapsed: Long,
    val restoreAllowed: Boolean = true,
)

@Serializable
data class TimerRun(
    val id: String,
    val startedElapsed: Long,
    val endElapsed: Long,
    val totalDurationMillis: Long,
    val source: StartSource,
    val hasWhileInUse: Boolean,
    val isTest: Boolean = false,
    val warningDelivered: Boolean = false,
    val fade: VolumeRecord? = null,
    val deferredUntilElapsed: Long? = null,
)

@Serializable
enum class StopStatus { STOPPED, NOTHING_PLAYING, FAILED, DEFERRED, INTERRUPTED, CANCELLED }

@Serializable
enum class StopMethod { NONE, MEDIA_SESSION, AUDIO_FOCUS, MEDIA_KEY }

@Serializable
data class StopResult(
    val status: StopStatus,
    val method: StopMethod = StopMethod.NONE,
    val completedElapsed: Long = 0,
    val firstAttemptElapsed: Long? = null,
    val detail: String = "",
    val isTest: Boolean = false,
    val localOnly: Boolean = false,
)

@Serializable
data class TimerSnapshot(
    val schemaVersion: Int = 1,
    val bootCount: Int = -1,
    val revision: Long = 0,
    val scheduleGeneration: Long = 0,
    val phase: TimerPhase = TimerPhase.IDLE,
    val selectedDurationMillis: Long = TimerLimits.DEFAULT_DURATION,
    val run: TimerRun? = null,
    val lastResult: StopResult? = null,
    val acceptedCommandIds: List<String> = emptyList(),
    val needsAlarmReconcile: Boolean = false,
)

val TimerSnapshot.isActive: Boolean
    get() = run != null && phase in setOf(
        TimerPhase.RUNNING,
        TimerPhase.FADING,
        TimerPhase.STOPPING,
        TimerPhase.DEFERRED,
    )

data class TimerUiState(
    val initialized: Boolean = false,
    val snapshot: TimerSnapshot = TimerSnapshot(),
    val exactAlarmsAllowed: Boolean = false,
    val degradedReason: String? = null,
)

sealed interface TimerCommand {
    data class Start(
        val durationMillis: Long,
        val source: StartSource,
        val hasWhileInUse: Boolean,
        val commandId: String,
    ) : TimerCommand
    data class Stop(val commandId: String, val expectedRunId: String? = null) : TimerCommand
    data class Extend(val deltaMillis: Long, val commandId: String, val expectedRunId: String? = null) : TimerCommand
    data class Reduce(val deltaMillis: Long, val commandId: String, val expectedRunId: String? = null) : TimerCommand
    data class StartTest(val hasWhileInUse: Boolean, val commandId: String) : TimerCommand
    data class AlarmFired(val runId: String, val generation: Long, val kind: AlarmKind) : TimerCommand
    data class Reconcile(
        val reason: String,
        val serviceRestarted: Boolean = false,
        val executionAllowed: Boolean = true,
    ) : TimerCommand
    data class SettingsChanged(val settings: AppSettings) : TimerCommand
}

data class CommandResult(val accepted: Boolean, val detail: String = "")

private const val DEFAULT_FADE = 15 * TimerLimits.SECOND
private const val DEFAULT_EXTENSION = 10 * TimerLimits.MINUTE
private const val FADE_STEP = 5 * TimerLimits.SECOND
private const val DEFAULT_NIGHTLY_HOUR = 23
private const val LAST_HOUR = 23
private const val LAST_MINUTE = 59
private const val DAYS_PER_WEEK = 7
private val WEEK_DAYS = (1..DAYS_PER_WEEK).toSet()

@Suppress("MagicNumber") // Product's discrete minute choices, expressed directly as a set.
private val EXTENSION_OPTIONS = setOf(5L, 10L, 15L, 20L, 30L).map { it * TimerLimits.MINUTE }.toSet()
