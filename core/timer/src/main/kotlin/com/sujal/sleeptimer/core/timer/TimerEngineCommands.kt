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

@Suppress("ReturnCount") // Fail closed at each command validation boundary.
internal suspend fun EngineRuntime.reconcileRestoredSnapshot() {
    val run = snapshot.run ?: return
    if (recovery.wasForceStopped()) {
        alarms.cancel(run.id, snapshot.scheduleGeneration)
        commit(
            snapshot.copy(
                phase = TimerPhase.FINISHED,
                run = null,
                needsAlarmReconcile = false,
                lastResult = StopResult(
                    StopStatus.INTERRUPTED,
                    detail = "Timer cancelled when the app was force-stopped",
                    isTest = run.isTest,
                ),
            ),
            durable = false,
        )
        return
    }
    val currentBoot = clock.bootCount()
    val bootChanged = currentBoot < 0 || snapshot.bootCount < 0 || snapshot.bootCount != currentBoot
    if (bootChanged) {
        commit(
            snapshot.copy(
                phase = TimerPhase.FINISHED,
                run = null,
                needsAlarmReconcile = false,
                lastResult = StopResult(StopStatus.INTERRUPTED, detail = "Timer interrupted by restart"),
            ),
            durable = false,
        )
        return
    }
    if (!snapshot.isActive) return
    val late = clock.elapsedRealtime() - run.endElapsed
    val mustInterrupt = late > TimerLimits.LATE_STOP_WINDOW || feedback.isInteractive()
    if (!snapshot.isCancelling && late > 0 && mustInterrupt) {
        alarms.cancel(run.id, snapshot.scheduleGeneration)
        commit(
            snapshot.copy(
                phase = TimerPhase.FINISHED,
                run = null,
                needsAlarmReconcile = false,
                lastResult = StopResult(
                    StopStatus.INTERRUPTED,
                    detail = "Your timer was interrupted",
                    isTest = run.isTest,
                ),
            ),
            durable = false,
        )
    } else {
        commit(snapshot.copy(run = run.copy(hasWhileInUse = false), needsAlarmReconcile = true), durable = false)
    }
}

@Suppress("ReturnCount") // Fail closed at each command validation boundary.
internal suspend fun EngineRuntime.handleCommand(command: TimerCommand): CommandResult {
    commandRejection(command)?.let { return it }
    return when (command) {
        is TimerCommand.SelectDuration -> selectDuration(command)
        is TimerCommand.Start -> startCommand(command)
        is TimerCommand.UndoReplacement -> undoReplacement(command)
        is TimerCommand.StartTest -> startTest(command)
        is TimerCommand.Stop -> cancelTimer(command)
        is TimerCommand.Extend -> adjust(command.deltaMillis, command.commandId, command.expectedRunId, extend = true)
        is TimerCommand.Reduce -> adjust(command.deltaMillis, command.commandId, command.expectedRunId, extend = false)
        is TimerCommand.AlarmFired -> alarmFired(command)
        is TimerCommand.Reconcile -> reconcile(command)
        is TimerCommand.SettingsChanged -> changeSettings(command.settings)
    }
}

@Suppress("ReturnCount") // Fail closed at each command validation boundary.
internal suspend fun EngineRuntime.start(
    duration: Long,
    source: StartSource,
    hasWhileInUse: Boolean,
    commandId: String,
    test: Boolean = false,
): CommandResult {
    if (!test &&
        duration !in TimerLimits.MINUTE..TimerLimits.MAX_DURATION
    ) {
        return CommandResult(false, "Invalid duration")
    }
    if (source == StartSource.NIGHTLY) {
        if (snapshot.lastNightlyCommandId ==
            commandId
        ) {
            return CommandResult(false, "Nightly occurrence already handled")
        }
        if (snapshot.isActive) {
            commit(snapshot.copy(lastNightlyCommandId = commandId, acceptedCommandIds = snapshot.remember(commandId)))
            return CommandResult(false, "Timer already running")
        }
    }
    val old = snapshot
    val previous = deactivate()
    val now = clock.elapsedRealtime()
    val inheritedFade = old.run?.fade?.takeIf { it.restoreAllowed }
    val run = TimerRun(
        id = UUID.randomUUID().toString(),
        startedElapsed = now,
        endElapsed = now + duration,
        totalDurationMillis = duration,
        source = source,
        hasWhileInUse = hasWhileInUse,
        isTest = test,
        fade = inheritedFade,
    )
    commit(
        old.copy(
            phase = TimerPhase.RUNNING,
            run = run,
            lastResult = null,
            scheduleGeneration = old.scheduleGeneration + 1,
            selectedDurationMillis = if (test) old.selectedDurationMillis else duration,
            acceptedCommandIds = old.remember(commandId),
            lastNightlyCommandId = if (source == StartSource.NIGHTLY) commandId else old.lastNightlyCommandId,
            needsAlarmReconcile = true,
        ),
    )
    replacementUndo = old.run?.takeIf {
        source == StartSource.SHORTCUT && !it.isTest && old.phase in setOf(TimerPhase.RUNNING, TimerPhase.FADING)
    }?.let { ReplacementUndo(it, old.selectedDurationMillis, run.id, snapshot.scheduleGeneration, now) }
    old.run?.let { alarms.cancel(it.id, old.scheduleGeneration) }
    if (!test) rememberDuration(duration)
    activatePlan(previous, recoverFade = inheritedFade != null, allowPlayingRecovery = true)
    return CommandResult(true)
}

@Suppress("ReturnCount") // Fail closed at each command validation boundary.
internal suspend fun EngineRuntime.cancelTimer(command: TimerCommand.Stop): CommandResult {
    val run = snapshot.run
    if (command.expectedRunId != null && command.expectedRunId != run?.id) return CommandResult(false, "Stale run")
    if (!snapshot.isActive || snapshot.isCancelling) {
        commit(snapshot.copy(acceptedCommandIds = snapshot.remember(command.commandId)))
        return CommandResult(true)
    }
    val previous = deactivate()
    commit(
        snapshot.copy(
            phase = TimerPhase.STOPPING,
            lastResult = StopResult(StopStatus.CANCELLED, detail = "Timer cancelled", isTest = run?.isTest == true),
            acceptedCommandIds = snapshot.remember(command.commandId),
            needsAlarmReconcile = false,
        ),
    )
    run?.let { alarms.cancel(it.id, snapshot.scheduleGeneration) }
    activatePlan(previous, recoverFade = true, allowPlayingRecovery = true)
    return CommandResult(true)
}

@Suppress("ReturnCount") // Fail closed at each command validation boundary.
internal suspend fun EngineRuntime.adjust(
    delta: Long,
    commandId: String,
    expectedRunId: String?,
    extend: Boolean,
): CommandResult {
    val run = snapshot.run ?: return CommandResult(false, "No active timer")
    if (!snapshot.isActive || snapshot.isCancelling) return CommandResult(false, "No active timer")
    if (expectedRunId != null && expectedRunId != run.id) return CommandResult(false, "Stale run")
    if (delta <= 0) return CommandResult(false, "Invalid adjustment")
    val now = clock.elapsedRealtime()
    val remaining = (run.endElapsed - now).coerceAtLeast(0)
    val boundedDelta = delta.coerceAtMost(TimerLimits.MAX_DURATION)
    val newRemaining = if (extend) {
        (remaining + boundedDelta).coerceAtMost(TimerLimits.MAX_DURATION)
    } else {
        if (remaining <= REDUCE_FLOOR) return CommandResult(false, "Five minutes or less remain")
        (remaining - boundedDelta).coerceAtLeast(MIN_REMAINING)
    }
    val applied = newRemaining - remaining
    val total = if (applied > 0 && run.totalDurationMillis > Long.MAX_VALUE - applied) {
        Long.MAX_VALUE
    } else {
        (run.totalDurationMillis + applied).coerceAtLeast(newRemaining)
    }
    val oldGeneration = snapshot.scheduleGeneration
    val previous = deactivate()
    commit(
        snapshot.copy(
            phase = TimerPhase.RUNNING,
            run = run.copy(endElapsed = now + newRemaining, totalDurationMillis = total, deferredUntilElapsed = null),
            scheduleGeneration = oldGeneration + 1,
            acceptedCommandIds = snapshot.remember(commandId),
            needsAlarmReconcile = true,
        ),
    )
    alarms.cancel(run.id, oldGeneration)
    activatePlan(previous, recoverFade = run.fade != null, allowPlayingRecovery = extend)
    return CommandResult(true, if (extend && applied < boundedDelta) "Maximum remaining time is 12 hours" else "")
}

internal suspend fun EngineRuntime.reconcile(command: TimerCommand.Reconcile): CommandResult {
    executionAllowed = command.executionAllowed
    val previous = deactivate()
    val run = snapshot.run
    if (run != null && snapshot.isActive) {
        commit(
            snapshot.copy(
                run = if (command.serviceRestarted) run.copy(hasWhileInUse = false) else run,
                needsAlarmReconcile = !snapshot.isCancelling,
            ),
            durable = false,
        )
        activatePlan(previous, recoverFade = command.serviceRestarted)
    } else {
        publish()
    }
    return CommandResult(true)
}

@Suppress("ReturnCount") // Fail closed at each command validation boundary.
internal suspend fun EngineRuntime.changeSettings(updated: AppSettings): CommandResult {
    if (!updated.isValid()) return CommandResult(false, "Invalid settings")
    if (settings == updated) return CommandResult(true)
    settings = updated
    val oldGeneration = snapshot.scheduleGeneration
    val previous = deactivate()
    commit(
        snapshot.copy(
            scheduleGeneration = oldGeneration + 1,
            selectedDurationMillis = if (snapshot.isActive) {
                snapshot.selectedDurationMillis
            } else {
                updated.lastDurationMillis
            },
            needsAlarmReconcile = snapshot.isActive && !snapshot.isCancelling,
        ),
    )
    snapshot.run?.let { alarms.cancel(it.id, oldGeneration) }
    activatePlan(previous, recoverFade = snapshot.run?.fade != null)
    return CommandResult(true)
}

@Suppress("ReturnCount") // Fail closed at each command validation boundary.
internal suspend fun EngineRuntime.alarmFired(command: TimerCommand.AlarmFired): CommandResult {
    val run = snapshot.run ?: return CommandResult(false, "Stale alarm")
    val wrongIdentity = run.id != command.runId || command.generation != snapshot.scheduleGeneration
    if (!snapshot.isActive || snapshot.isCancelling || wrongIdentity) {
        return CommandResult(false, "Stale alarm")
    }
    val now = clock.elapsedRealtime()
    if (command.kind == AlarmKind.BACKUP && now < run.endElapsed) return CommandResult(false, "Early backup")
    if (command.kind == AlarmKind.FINAL_WINDOW && now < finalWindow(run, settings)) {
        return CommandResult(false, "Early final-window alarm")
    }
    if (effectJob?.isActive != true) activatePlan(null, forceCritical = true, scheduleAlarms = false)
    return CommandResult(true)
}
