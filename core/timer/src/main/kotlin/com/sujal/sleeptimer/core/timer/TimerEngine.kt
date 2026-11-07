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
import com.sujal.sleeptimer.core.model.RecoveryInspector
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

/** The sole timer writer. Long-running effects report back through the same serial command queue. */
data class TimerDependencies(
    val clock: Clock,
    val snapshotStore: SnapshotStore,
    val settingsStore: SettingsStore,
    val alarms: AlarmScheduler,
    val wakeLocks: WakeLocks,
    val feedback: TimerFeedback,
    val playback: PlaybackController,
    val diagnostics: Diagnostics,
    val recovery: RecoveryInspector = RecoveryInspector { false },
    val screenLocker: com.sujal.sleeptimer.core.model.ScreenLocker = com.sujal.sleeptimer.core.model.ScreenLocker {},
)

class TimerEngine(dependencies: TimerDependencies, scope: CoroutineScope, initialExecutionAllowed: Boolean = true) {
    private val job = SupervisorJob(scope.coroutineContext[Job])
    private val engineScope = CoroutineScope(scope.coroutineContext + job)
    private val events = Channel<EngineEvent>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow(TimerUiState())
    private val runtime = EngineRuntime(
        dependencies,
        engineScope,
        events,
        mutableState,
        initialExecutionAllowed,
    )
    private val closed = AtomicBoolean(false)
    val state: StateFlow<TimerUiState> = mutableState.asStateFlow()

    init {
        engineScope.launch {
            runtime.initialize()
            for (event in events) runtime.process(event)
        }
    }

    suspend fun send(command: TimerCommand): CommandResult {
        if (closed.get()) return CommandResult(false, "Timer engine is closed")
        val reply = CompletableDeferred<CommandResult>()
        events.send(EngineEvent.Command(command, reply))
        return reply.await()
    }

    fun close() {
        if (closed.compareAndSet(false, true)) {
            runtime.close()
            events.close()
            engineScope.cancel()
        }
    }
}

internal sealed interface EngineEvent {
    data class Command(val command: TimerCommand, val reply: CompletableDeferred<CommandResult>) : EngineEvent
    data class Record(
        val runId: String,
        val token: EffectToken,
        val record: VolumeRecord?,
        val reply: CompletableDeferred<Unit>,
    ) : EngineEvent
    data class Phase(
        val runId: String,
        val token: EffectToken,
        val phase: TimerPhase,
        val deferredUntil: Long? = null,
        val reply: CompletableDeferred<Unit>,
    ) : EngineEvent
    data class Warning(val runId: String, val token: EffectToken, val reply: CompletableDeferred<Unit>) : EngineEvent
    data class Finished(val runId: String, val token: EffectToken, val result: StopResult) : EngineEvent
}

internal class EngineRuntime(
    ports: TimerDependencies,
    val scope: CoroutineScope,
    val events: Channel<EngineEvent>,
    val state: MutableStateFlow<TimerUiState>,
    var executionAllowed: Boolean,
) {
    var replacementUndo: ReplacementUndo? = null
    val clock = ports.clock
    val store = ports.snapshotStore
    val settingsStore = ports.settingsStore
    val alarms = ports.alarms
    val wakeLocks = ports.wakeLocks
    val feedback = ports.feedback
    val screenLocker = ports.screenLocker
    val playback = ports.playback
    val diagnostics = ports.diagnostics
    val recovery = ports.recovery
    var snapshot = TimerSnapshot()
    var settings = AppSettings()
    var failure: String? = null
    val gate = EffectGate()
    var token: EffectToken? = null
    var effectJob: Job? = null
    var fullRunLease: WakeLease? = null
    val leases = mutableSetOf<TrackedLease>()

    // Keep storage failures inside the actor; cancellation is propagated separately.
    @Suppress("TooGenericExceptionCaught")
    suspend fun initialize() {
        try {
            settings = settingsStore.data.first().takeIf { it.isValid() } ?: AppSettings()
            val saved = store.load()
            snapshot = saved.copy(
                lastTestResult = saved.lastTestResult ?: saved.lastResult?.takeIf {
                    it.isTest && it.status != StopStatus.CANCELLED
                },
            )
            reconcileRestoredSnapshot()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            failure = "Timer recovery unavailable"
            diagnostics.record("timer recovery failed: ${error.javaClass.simpleName}")
            snapshot = TimerSnapshot(bootCount = clock.bootCount())
        }
        publish()
        if (snapshot.isActive) activatePlan(null, recoverFade = true)
        scope.launch {
            settingsStore.data.collect { updated ->
                if (updated != settings) {
                    events.send(EngineEvent.Command(TimerCommand.SettingsChanged(updated), CompletableDeferred()))
                }
            }
        }
    }

    // Keep storage failures inside the actor; cancellation is propagated separately.
    @Suppress("TooGenericExceptionCaught")
    suspend fun process(event: EngineEvent) {
        try {
            when (event) {
                is EngineEvent.Command -> event.reply.complete(handleCommand(event.command))
                is EngineEvent.Record -> handleRecord(event)
                is EngineEvent.Phase -> handlePhase(event)
                is EngineEvent.Warning -> handleWarning(event)
                is EngineEvent.Finished -> handleFinished(event)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            rejectEvent(event, error)
        }
    }

    private suspend fun rejectEvent(event: EngineEvent, error: Exception) {
        failure = "Timer state could not be saved"
        diagnostics.record("timer command failed: ${error.javaClass.simpleName}")
        publish()
        when (event) {
            is EngineEvent.Command -> {
                event.reply.complete(CommandResult(false, failure.orEmpty()))
                if (snapshot.isActive && effectJob?.isActive != true) activatePlan(null)
            }

            is EngineEvent.Record -> event.reply.completeExceptionally(error)

            is EngineEvent.Phase -> event.reply.completeExceptionally(error)

            is EngineEvent.Warning -> event.reply.completeExceptionally(error)

            is EngineEvent.Finished -> Unit
        }
    }

    // Keep storage failures inside the actor; cancellation is propagated separately.
    @Suppress("TooGenericExceptionCaught")
    suspend fun commit(next: TimerSnapshot, durable: Boolean = true) {
        val updated = next.copy(bootCount = clock.bootCount(), revision = snapshot.revision + 1)
        try {
            store.save(updated)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (durable) throw error
            failure = "Timer state could not be saved"
            diagnostics.record("timer safety transition not persisted: ${error.javaClass.simpleName}")
        }
        snapshot = updated
        publish()
    }

    fun publish() {
        val exact = runCatching { alarms.canScheduleExactAlarms() }.getOrDefault(false)
        state.value = TimerUiState(
            initialized = true,
            snapshot = snapshot,
            exactAlarmsAllowed = exact,
            degradedReason = when {
                !executionAllowed && snapshot.isActive -> "Open app to keep timer running"
                failure != null -> failure
                !exact -> "Exact alarms off"
                else -> null
            },
        )
    }

    fun deactivate(): Job? {
        gate.invalidate()
        val previous = effectJob
        previous?.cancel()
        effectJob = null
        token = null
        fullRunLease?.release()
        fullRunLease = null
        snapshot.run?.let { playback.abandonFocus(it.id) }
        return previous
    }

    fun matches(runId: String, expectedToken: EffectToken): Boolean =
        snapshot.isActive && snapshot.run?.id == runId && token === expectedToken && expectedToken.isValid()

    fun acquire(reason: String, timeout: Long): WakeLease {
        val lease = TrackedLease(wakeLocks.acquire(reason, timeout.coerceAtLeast(1)), leases)
        synchronized(leases) { leases.add(lease) }
        return lease
    }

    fun close() {
        deactivate()
        synchronized(leases) { leases.toList() }.forEach { it.release() }
    }
}

internal class TrackedLease(private val delegate: WakeLease, private val owners: MutableSet<TrackedLease>) : WakeLease {
    private val released = AtomicBoolean(false)
    override fun release() {
        if (released.compareAndSet(false, true)) {
            try {
                delegate.release()
            } finally {
                synchronized(owners) { owners.remove(this) }
            }
        }
    }
}

internal suspend fun EngineRuntime.handleRecord(event: EngineEvent.Record) {
    if (!matches(event.runId, event.token)) {
        event.reply.completeExceptionally(CancellationException("Superseded run"))
        return
    }
    val phase = when {
        snapshot.phase == TimerPhase.STOPPING || snapshot.phase == TimerPhase.DEFERRED -> snapshot.phase
        event.record?.restoreAllowed == true -> TimerPhase.FADING
        else -> TimerPhase.RUNNING
    }
    commit(snapshot.copy(phase = phase, run = snapshot.run?.copy(fade = event.record)))
    event.reply.complete(Unit)
}

internal suspend fun EngineRuntime.handlePhase(event: EngineEvent.Phase) {
    if (matches(event.runId, event.token)) {
        commit(
            snapshot.copy(phase = event.phase, run = snapshot.run?.copy(deferredUntilElapsed = event.deferredUntil)),
            durable = false,
        )
        event.reply.complete(Unit)
    } else {
        event.reply.completeExceptionally(CancellationException("Superseded run"))
    }
}

internal suspend fun EngineRuntime.handleWarning(event: EngineEvent.Warning) {
    if (matches(event.runId, event.token) && snapshot.run?.warningDelivered == false) {
        commit(snapshot.copy(run = snapshot.run?.copy(warningDelivered = true)), durable = false)
        event.reply.complete(Unit)
    } else {
        event.reply.completeExceptionally(CancellationException("Warning already handled"))
    }
}

internal suspend fun EngineRuntime.handleFinished(event: EngineEvent.Finished) {
    if (!matches(event.runId, event.token)) return
    val run = snapshot.run ?: return
    val oldGeneration = snapshot.scheduleGeneration
    deactivate()
    alarms.cancel(run.id, oldGeneration)
    commit(
        snapshot.copy(
            phase = if (event.result.status == StopStatus.CANCELLED) TimerPhase.IDLE else TimerPhase.FINISHED,
            run = null,
            lastResult = event.result,
            lastTestResult = if (event.result.isTest && event.result.status != StopStatus.CANCELLED) {
                event.result
            } else {
                snapshot.lastTestResult
            },
            needsAlarmReconcile = false,
        ),
        durable = false,
    )
}
