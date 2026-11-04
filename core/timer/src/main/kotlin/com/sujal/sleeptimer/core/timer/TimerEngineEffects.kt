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

@Suppress("ReturnCount", "TooGenericExceptionCaught") // Fail closed; isolate unexpected effect failures.
internal suspend fun EngineRuntime.activatePlan(
    previous: Job?,
    recoverFade: Boolean = false,
    allowPlayingRecovery: Boolean = false,
    forceCritical: Boolean = false,
    scheduleAlarms: Boolean = true,
) {
    val run = snapshot.run ?: return
    if (!snapshot.isActive) return
    if (!snapshot.isCancelling && scheduleAlarms) {
        schedulePlan(run)
    }
    if (!executionAllowed && !forceCritical) {
        publish()
        return
    }
    val remaining = (run.endElapsed - clock.elapsedRealtime()).coerceAtLeast(0)
    val exact = alarms.canScheduleExactAlarms() && !snapshot.needsAlarmReconcile
    val mustStayAwake = needsFullRunWakeLock(remaining, exact)
    if (mustStayAwake) {
        fullRunLease = acquire("timer-runtime", remaining + FINAL_GRACE)
    }
    if (!mustStayAwake && !forceCritical && clock.elapsedRealtime() < finalWindow(run, settings)) return
    val currentToken = gate.next()
    token = currentToken
    val currentSettings = settings
    val cancelling = snapshot.isCancelling
    val criticalLease = if (fullRunLease == null) acquire("timer-final-window", remaining + FINAL_GRACE) else null
    effectJob = launchEffect(
        EffectExecution(
            run,
            currentSettings,
            currentToken,
            cancelling,
            previous,
            recoverFade,
            allowPlayingRecovery,
            criticalLease,
        ),
    )
}

internal suspend fun EngineRuntime.schedulePlan(run: TimerRun) {
    val scheduled = runCatching {
        alarms.schedule(AlarmPlan(run.id, snapshot.scheduleGeneration, finalWindow(run, settings), run.endElapsed))
    }.getOrElse {
        diagnostics.record("alarm scheduling failed: ${it.javaClass.simpleName}")
        false
    }
    commit(snapshot.copy(needsAlarmReconcile = !scheduled), durable = false)
}

internal suspend fun EngineRuntime.cancelRecovery(run: TimerRun, currentToken: EffectToken, allowPlaying: Boolean) {
    run.fade?.let { playback.recoverFade(it, currentToken, allowPlaying) }
    events.send(
        EngineEvent.Finished(
            run.id,
            currentToken,
            StopResult(
                StopStatus.CANCELLED,
                completedElapsed = clock.elapsedRealtime(),
                detail = "Timer cancelled",
                isTest = run.isTest,
            ),
        ),
    )
}

@Suppress("TooGenericExceptionCaught") // A rejected fade must still reach the deadline stop.
internal suspend fun EngineRuntime.executePipeline(
    initialRun: TimerRun,
    activeSettings: AppSettings,
    currentToken: EffectToken,
    recover: Boolean,
    allowPlayingRecovery: Boolean,
) = coroutineScope {
    val savedFade = initialRun.fade
    if (recover && savedFade != null) {
        val restored = playback.recoverFade(savedFade, currentToken, allowPlayingRecovery)
        if (restored || allowPlayingRecovery) record(initialRun.id, currentToken, null)
    }
    awaitElapsed(finalWindow(initialRun, activeSettings), currentToken)
    val warningJob = if (warningEnabled(initialRun, activeSettings)) {
        launch { deliverWarning(initialRun, activeSettings, currentToken) }
    } else {
        null
    }
    val fadeStart = initialRun.endElapsed - activeSettings.fadeMillis.coerceAtMost(
        if (initialRun.isTest) TEST_FADE_CAP else initialRun.totalDurationMillis,
    )
    awaitElapsed(fadeStart, currentToken)
    if (activeSettings.fadeMillis > 0 && currentToken.isValid()) {
        try {
            val current = currentRun(initialRun.id)
            playback.fade(current, activeSettings, currentToken) { record(initialRun.id, currentToken, it) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            diagnostics.record("fade skipped safely: ${error.javaClass.simpleName}")
        }
    }
    awaitElapsed(initialRun.endElapsed, currentToken)
    warningJob?.join()
    executeStop(initialRun.id, activeSettings, currentToken)
}

internal suspend fun EngineRuntime.deliverWarning(
    run: TimerRun,
    activeSettings: AppSettings,
    currentToken: EffectToken,
) {
    awaitElapsed(run.endElapsed - TimerLimits.MINUTE, currentToken)
    if (playback.observe().mustDefer || !currentToken.isValid()) return
    val reply = CompletableDeferred<Unit>()
    events.send(EngineEvent.Warning(run.id, currentToken, reply))
    reply.await()
    currentToken.guarded { feedback.warning(activeSettings.warning) }
}

internal suspend fun EngineRuntime.executeStop(runId: String, activeSettings: AppSettings, currentToken: EffectToken) {
    var deferralLease: WakeLease? = null
    var deferredDeadline: Long? = currentRun(runId).deferredUntilElapsed
    var stopLease: WakeLease? = null
    try {
        while (currentToken.isValid()) {
            phase(runId, currentToken, TimerPhase.STOPPING, deferredDeadline)
            val result = playback.stop(currentRun(runId), activeSettings, currentToken) {
                record(runId, currentToken, it)
            }
            if (result.status != StopStatus.DEFERRED) {
                events.send(EngineEvent.Finished(runId, currentToken, result))
                return
            }
            val until = deferredDeadline ?: (clock.elapsedRealtime() + TimerLimits.DEFER_WINDOW)
            deferredDeadline = until
            phase(runId, currentToken, TimerPhase.DEFERRED, until)
            deferralLease?.release()
            deferralLease = acquire("timer-call-deferral", (until - clock.elapsedRealtime()).coerceAtLeast(1))
            while (currentToken.isValid() && clock.elapsedRealtime() < until && playback.observe().mustDefer) {
                delay(DEFER_POLL.coerceAtMost(until - clock.elapsedRealtime()).coerceAtLeast(1))
            }
            if (clock.elapsedRealtime() >= until) {
                events.send(
                    EngineEvent.Finished(
                        runId,
                        currentToken,
                        StopResult(
                            StopStatus.INTERRUPTED,
                            completedElapsed = clock.elapsedRealtime(),
                            detail = "Call or alarm continued beyond the timer's retry window",
                            isTest = currentRun(runId).isTest,
                        ),
                    ),
                )
                return
            }
            stopLease?.release()
            stopLease = acquire("timer-after-deferral", FINAL_GRACE)
        }
    } finally {
        stopLease?.release()
        deferralLease?.release()
    }
}

internal fun EngineRuntime.currentRun(runId: String): TimerRun =
    state.value.snapshot.run?.takeIf { it.id == runId } ?: throw CancellationException("Superseded run")

internal suspend fun EngineRuntime.record(runId: String, currentToken: EffectToken, record: VolumeRecord?) {
    val reply = CompletableDeferred<Unit>()
    events.send(EngineEvent.Record(runId, currentToken, record, reply))
    reply.await()
}

internal suspend fun EngineRuntime.phase(
    runId: String,
    currentToken: EffectToken,
    phase: TimerPhase,
    deferredUntil: Long?,
) {
    val reply = CompletableDeferred<Unit>()
    events.send(EngineEvent.Phase(runId, currentToken, phase, deferredUntil, reply))
    reply.await()
}

internal suspend fun EngineRuntime.awaitElapsed(deadline: Long, currentToken: EffectToken) {
    while (currentToken.isValid()) {
        val remaining = deadline - clock.elapsedRealtime()
        if (remaining <= 0) return
        delay(remaining.coerceAtMost(SCHEDULER_STEP))
    }
    throw CancellationException("Superseded run")
}
