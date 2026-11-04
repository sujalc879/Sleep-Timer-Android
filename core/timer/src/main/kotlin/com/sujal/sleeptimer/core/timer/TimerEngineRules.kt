package com.sujal.sleeptimer.core.timer

import com.sujal.sleeptimer.core.model.AlarmKind
import com.sujal.sleeptimer.core.model.AlarmPlan
import com.sujal.sleeptimer.core.model.AlarmScheduler
import com.sujal.sleeptimer.core.model.AppSettings
import com.sujal.sleeptimer.core.model.Clock
import com.sujal.sleeptimer.core.model.CommandResult
import com.sujal.sleeptimer.core.model.Diagnostics
import com.sujal.sleeptimer.core.model.EffectGate
import com.sujal.sleeptimer.core.model.EffectToken
import com.sujal.sleeptimer.core.model.PlaybackController
import com.sujal.sleeptimer.core.model.SettingsStore
import com.sujal.sleeptimer.core.model.SnapshotStore
import com.sujal.sleeptimer.core.model.StartSource
import com.sujal.sleeptimer.core.model.StopResult
import com.sujal.sleeptimer.core.model.StopStatus
import com.sujal.sleeptimer.core.model.TimerCommand
import com.sujal.sleeptimer.core.model.TimerFeedback
import com.sujal.sleeptimer.core.model.TimerLimits
import com.sujal.sleeptimer.core.model.TimerPhase
import com.sujal.sleeptimer.core.model.TimerRun
import com.sujal.sleeptimer.core.model.TimerSnapshot
import com.sujal.sleeptimer.core.model.TimerUiState
import com.sujal.sleeptimer.core.model.VolumeRecord
import com.sujal.sleeptimer.core.model.WakeLease
import com.sujal.sleeptimer.core.model.WakeLocks
import com.sujal.sleeptimer.core.model.WarningMode
import com.sujal.sleeptimer.core.model.isActive
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal fun TimerCommand.deliveryId(): String? = when (this) {
    is TimerCommand.Start -> commandId
    is TimerCommand.StartTest -> commandId
    is TimerCommand.Stop -> commandId
    is TimerCommand.Extend -> commandId
    is TimerCommand.Reduce -> commandId
    else -> null
}

internal fun TimerSnapshot.remember(id: String): List<String> = (acceptedCommandIds + id).takeLast(COMMAND_HISTORY)
internal val TimerSnapshot.isCancelling: Boolean
    get() = phase == TimerPhase.STOPPING && lastResult?.status == StopStatus.CANCELLED && run != null

internal fun finalWindow(run: TimerRun, settings: AppSettings): Long {
    val warning = if (warningEnabled(run, settings)) TimerLimits.MINUTE else 0
    val fade = settings.fadeMillis.coerceAtMost(if (run.isTest) TEST_FADE_CAP else run.totalDurationMillis)
    return run.endElapsed - maxOf(warning, fade) - FINAL_SAFETY
}

internal fun warningEnabled(run: TimerRun, settings: AppSettings): Boolean =
    settings.warning != WarningMode.OFF && !run.warningDelivered && run.totalDurationMillis >= 2 * TimerLimits.MINUTE

internal const val COMMAND_HISTORY = 64
internal const val MAX_COMMAND_ID_LENGTH = 128
internal const val TEST_DURATION = 10 * TimerLimits.SECOND
internal const val TEST_FADE_CAP = 5 * TimerLimits.SECOND
internal const val FINAL_SAFETY = 5 * TimerLimits.SECOND
internal const val QUOTA_RISK_WINDOW = 15 * TimerLimits.MINUTE
internal const val REDUCE_FLOOR = 5 * TimerLimits.MINUTE
internal const val MIN_REMAINING = TimerLimits.SECOND
internal const val FINAL_GRACE = 2 * TimerLimits.MINUTE
internal const val SCHEDULER_STEP = 250L
internal const val DEFER_POLL = TimerLimits.SECOND

/** Short/restarted timers can encounter while-idle alarm quotas, so protect their whole run. */
internal fun EngineRuntime.needsFullRunWakeLock(remaining: Long, exact: Boolean): Boolean =
    executionAllowed && (!exact || remaining <= QUOTA_RISK_WINDOW || snapshot.isCancelling)
