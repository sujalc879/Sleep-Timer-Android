package com.sujal.sleeptimer.core.timer

import com.google.common.truth.Truth.assertThat
import com.sujal.sleeptimer.core.model.AlarmKind
import com.sujal.sleeptimer.core.model.AlarmPlan
import com.sujal.sleeptimer.core.model.AlarmScheduler
import com.sujal.sleeptimer.core.model.AppSettings
import com.sujal.sleeptimer.core.model.Clock
import com.sujal.sleeptimer.core.model.Diagnostics
import com.sujal.sleeptimer.core.model.EffectToken
import com.sujal.sleeptimer.core.model.FadeOutcome
import com.sujal.sleeptimer.core.model.Interruption
import com.sujal.sleeptimer.core.model.OutputRoute
import com.sujal.sleeptimer.core.model.PlaybackController
import com.sujal.sleeptimer.core.model.PlaybackObservation
import com.sujal.sleeptimer.core.model.RecoveryInspector
import com.sujal.sleeptimer.core.model.SettingsStore
import com.sujal.sleeptimer.core.model.SnapshotStore
import com.sujal.sleeptimer.core.model.StartSource
import com.sujal.sleeptimer.core.model.StopMethod
import com.sujal.sleeptimer.core.model.StopResult
import com.sujal.sleeptimer.core.model.StopStatus
import com.sujal.sleeptimer.core.model.TimerCommand
import com.sujal.sleeptimer.core.model.TimerFeedback
import com.sujal.sleeptimer.core.model.TimerLimits
import com.sujal.sleeptimer.core.model.TimerPhase
import com.sujal.sleeptimer.core.model.TimerRun
import com.sujal.sleeptimer.core.model.TimerSnapshot
import com.sujal.sleeptimer.core.model.VolumeRecord
import com.sujal.sleeptimer.core.model.WakeLease
import com.sujal.sleeptimer.core.model.WakeLocks
import com.sujal.sleeptimer.core.model.WarningMode
import com.sujal.sleeptimer.core.model.isActive
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimerEngineCommandTest {
    @Test
    fun testUsesRealDeadlineAndFiveSecondFadeWithoutChangingLastDuration() = runTest {
        val f = fixture()
        f.send(TimerCommand.StartTest(true, "test"))
        assertThat(f.snapshot.run?.isTest).isTrue()
        assertThat(f.snapshot.selectedDurationMillis).isEqualTo(TimerLimits.DEFAULT_DURATION)
        advanceTimeBy(TEST_SECONDS)
        runCurrent()
        assertThat(f.snapshot.phase).isEqualTo(TimerPhase.FINISHED)
        assertThat(f.playback.stopTimes).containsExactly(TEST_SECONDS)
        assertThat(f.playback.fadeStartedAt).containsExactly(TEST_FADE_START)
        assertThat(f.wakes.active).isEqualTo(0)
        f.engine.close()
    }

    @Test
    fun duplicateDeliveryDoesNotAddTimeButSeparateTapsDo() = runTest {
        val f = fixture()
        f.start()
        val initial = f.snapshot.run!!.endElapsed
        assertThat(f.send(TimerCommand.Extend(TimerLimits.MINUTE, "tap-1")).accepted).isTrue()
        assertThat(f.send(TimerCommand.Extend(TimerLimits.MINUTE, "tap-1")).accepted).isFalse()
        f.send(TimerCommand.Extend(TimerLimits.MINUTE, "tap-2"))
        assertThat(f.snapshot.run!!.endElapsed).isEqualTo(initial + 2 * TimerLimits.MINUTE)
        assertThat(f.store.value.acceptedCommandIds).containsAtLeast("tap-1", "tap-2")
        f.engine.close()
    }

    @Test
    fun distinctStartsReplaceRunAndStaleActionsCannotMutateReplacement() = runTest {
        val f = fixture()
        f.start(id = "first")
        val oldRun = f.snapshot.run!!.id
        val oldGeneration = f.snapshot.scheduleGeneration
        f.start(id = "second")
        val replacement = f.snapshot.run!!
        assertThat(replacement.id).isNotEqualTo(oldRun)
        assertThat(f.send(TimerCommand.Stop("old-stop", oldRun)).accepted).isFalse()
        assertThat(f.send(TimerCommand.Extend(TimerLimits.MINUTE, "old-extend", oldRun)).accepted).isFalse()
        assertThat(f.send(TimerCommand.AlarmFired(oldRun, oldGeneration, AlarmKind.BACKUP)).accepted).isFalse()
        assertThat(f.snapshot.run).isEqualTo(replacement)
        f.engine.close()
    }

    @Test
    fun adjustmentBoundsRejectInvalidValuesAndAvoidOverflow() = runTest {
        val f = fixture()
        assertThat(f.send(TimerCommand.Start(0, StartSource.ACTIVITY, true, "bad")).accepted).isFalse()
        f.start()
        assertThat(f.send(TimerCommand.Extend(Long.MIN_VALUE, "negative")).accepted).isFalse()
        f.send(TimerCommand.Extend(Long.MAX_VALUE, "huge"))
        assertThat(f.snapshot.run!!.endElapsed - f.clock.elapsedRealtime()).isEqualTo(TimerLimits.MAX_DURATION)
        assertThat(f.snapshot.run!!.totalDurationMillis).isGreaterThan(0)
        assertThat(f.send(TimerCommand.Reduce(Long.MAX_VALUE, "reduce")).accepted).isTrue()
        assertThat(f.snapshot.run!!.endElapsed - f.clock.elapsedRealtime()).isEqualTo(TimerLimits.SECOND)
        assertThat(f.send(TimerCommand.Reduce(TimerLimits.MINUTE, "again")).accepted).isFalse()
        f.engine.close()
    }

    @Test
    fun stopCancelsDuringFadeWithoutCallingPlaybackStop() = runTest {
        val f = fixture()
        f.playback.fadeBlock = CompletableDeferred()
        f.send(TimerCommand.StartTest(true, "test"))
        advanceTimeBy(TEST_FADE_START)
        runCurrent()
        assertThat(f.snapshot.phase).isEqualTo(TimerPhase.FADING)
        assertThat(f.send(TimerCommand.Stop("cancel")).accepted).isTrue()
        runCurrent()
        assertThat(f.snapshot.phase).isEqualTo(TimerPhase.IDLE)
        assertThat(f.snapshot.lastResult?.status).isEqualTo(StopStatus.CANCELLED)
        assertThat(f.playback.recoverAllowPlaying).containsExactly(true)
        assertThat(f.playback.stopTimes).isEmpty()
        assertThat(f.wakes.active).isEqualTo(0)
        f.engine.close()
    }

    @Test
    fun extendIsProcessedWhileFadeIsSuspendedAndOldDeadlineCannotStopPlayback() = runTest {
        val f = fixture()
        f.playback.fadeBlock = CompletableDeferred()
        f.send(TimerCommand.StartTest(true, "test"))
        advanceTimeBy(TEST_FADE_START)
        runCurrent()
        f.send(TimerCommand.Extend(EXTENSION, "extend"))
        assertThat(f.snapshot.run!!.endElapsed).isEqualTo(TEST_SECONDS + EXTENSION)
        assertThat(f.playback.recoverAllowPlaying).containsExactly(true)
        advanceTimeBy(TEST_SECONDS)
        runCurrent()
        assertThat(f.playback.stopTimes).isEmpty()
        f.engine.close()
    }

    @Test
    fun warningIsPersistedAndNeverRepeatedAfterExtension() = runTest {
        val f = fixture()
        f.start(duration = 2 * TimerLimits.MINUTE)
        advanceTimeBy(TimerLimits.MINUTE)
        runCurrent()
        assertThat(f.feedback.warnings).containsExactly(WarningMode.SOFT)
        assertThat(f.store.value.run!!.warningDelivered).isTrue()
        f.send(TimerCommand.Extend(EXTENSION, "extend"))
        advanceTimeBy(EXTENSION)
        runCurrent()
        assertThat(f.feedback.warnings).hasSize(1)
        f.engine.close()
    }

    @Test
    fun startTestNeverWarnsAndNightlyDoesNotReplaceAnActiveTimer() = runTest {
        val f = fixture()
        f.send(TimerCommand.StartTest(true, "test"))
        val id = f.snapshot.run!!.id
        val result = f.send(TimerCommand.Start(TimerLimits.MINUTE, StartSource.NIGHTLY, false, "nightly"))
        assertThat(result.accepted).isFalse()
        assertThat(f.snapshot.run!!.id).isEqualTo(id)
        advanceTimeBy(TEST_SECONDS)
        runCurrent()
        assertThat(f.feedback.warnings).isEmpty()
        f.engine.close()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TimerEngineRecoveryTest {
    @Test
    fun restoredRunLosesCapabilityAndDuplicateIdsSurviveProcessRecreation() = runTest {
        val saved = savedRun().copy(acceptedCommandIds = listOf("already-applied"))
        val f = fixture(saved = saved)
        assertThat(f.snapshot.run?.hasWhileInUse).isFalse()
        val before = f.snapshot.run!!.endElapsed
        assertThat(f.send(TimerCommand.Extend(TimerLimits.MINUTE, "already-applied")).accepted).isFalse()
        assertThat(f.snapshot.run!!.endElapsed).isEqualTo(before)
        assertThat(f.alarms.plans).isNotEmpty()
        f.engine.close()
    }

    @Test
    fun rebootAndUnknownBootIdentityDiscardPersistedElapsedDeadline() = runTest {
        for ((storedBoot, currentBoot) in listOf(7 to 8, -1 to -1)) {
            val f = fixture(saved = savedRun().copy(bootCount = storedBoot), boot = currentBoot)
            assertThat(f.snapshot.run).isNull()
            assertThat(f.snapshot.lastResult?.status).isEqualTo(StopStatus.INTERRUPTED)
            assertThat(f.playback.stopTimes).isEmpty()
            assertThat(f.wakes.active).isEqualTo(0)
            f.engine.close()
        }
    }

    @Test
    fun forceStoppedRunDoesNotResumeOrStopPlaybackWhenAppIsReopened() = runTest {
        val f = fixture(saved = savedRun(), forceStopped = true)
        assertThat(f.snapshot.isActive).isFalse()
        assertThat(f.snapshot.lastResult?.status).isEqualTo(StopStatus.INTERRUPTED)
        assertThat(f.snapshot.lastResult?.detail).contains("force-stopped")
        assertThat(f.alarms.plans).isEmpty()
        assertThat(f.playback.stopTimes).isEmpty()
        assertThat(f.wakes.active).isEqualTo(0)
        f.engine.close()
    }

    @Test
    fun lateRestoreStopsOnlyWithinThirtyMinutesAndWhileScreenIsOff() = runTest {
        val safe =
            fixture(
                saved = savedRun(end = TimerLimits.MINUTE),
                initialElapsed = 2 * TimerLimits.MINUTE,
                interactive = false,
            )
        runCurrent()
        assertThat(safe.snapshot.lastResult?.status).isEqualTo(StopStatus.STOPPED)
        safe.engine.close()
        for ((elapsed, screenOn) in listOf(2 * TimerLimits.MINUTE to true, 32 * TimerLimits.MINUTE to false)) {
            val f =
                fixture(saved = savedRun(end = TimerLimits.MINUTE), initialElapsed = elapsed, interactive = screenOn)
            assertThat(f.snapshot.lastResult?.status).isEqualTo(StopStatus.INTERRUPTED)
            assertThat(f.playback.stopTimes).isEmpty()
            f.engine.close()
        }
    }

    @Test
    fun interruptedFadeRecoveryDoesNotAuthorizeRaisingActivePlayback() = runTest {
        val f = fixture(saved = savedRun().let { it.copy(run = it.run!!.copy(fade = volumeRecord())) })
        runCurrent()
        assertThat(f.playback.recoverAllowPlaying).containsExactly(false)
        assertThat(f.snapshot.run?.fade).isNotNull()
        f.engine.close()
    }

    @Test
    fun persistedCancellationIntentNeverReplaysExpiryStop() = runTest {
        val saved = savedRun().let {
            it.copy(
                phase = TimerPhase.STOPPING,
                lastResult = StopResult(StopStatus.CANCELLED),
                run = it.run!!.copy(fade = volumeRecord()),
            )
        }
        val f = fixture(saved = saved)
        runCurrent()
        assertThat(f.snapshot.phase).isEqualTo(TimerPhase.IDLE)
        assertThat(f.playback.stopTimes).isEmpty()
        assertThat(f.playback.recoverAllowPlaying).containsExactly(false)
        f.engine.close()
    }

    @Test
    fun initializationReadFailureIsVisibleAndDoesNotHangSubsequentCommands() = runTest {
        val f = fixture(loadFailure = true)
        assertThat(f.engine.state.value.initialized).isTrue()
        assertThat(f.engine.state.value.degradedReason).contains("recovery")
        assertThat(f.send(TimerCommand.StartTest(true, "test")).accepted).isTrue()
        f.engine.close()
        assertThat(f.wakes.active).isEqualTo(0)
    }

    @Test
    fun saveFailureRejectsStartWithoutPublishingOrLeakingALock() = runTest {
        val f = fixture()
        f.store.failSave = true
        assertThat(f.send(TimerCommand.StartTest(true, "test")).accepted).isFalse()
        assertThat(f.snapshot.isActive).isFalse()
        assertThat(f.wakes.active).isEqualTo(0)
        assertThat(f.engine.state.value.degradedReason).contains("saved")
        f.store.failSave = false
        assertThat(f.send(TimerCommand.StartTest(true, "test")).accepted).isTrue()
        f.engine.close()
    }

    @Test
    fun storageFailureDoesNotPreventAlreadyAcceptedRunFromPausingAtDeadline() = runTest {
        val f = fixture()
        f.send(TimerCommand.StartTest(true, "test"))
        f.store.failSave = true
        advanceTimeBy(TEST_SECONDS)
        runCurrent()
        assertThat(f.playback.volumeWrites).isEqualTo(0)
        assertThat(f.playback.stopTimes).containsExactly(TEST_SECONDS)
        assertThat(f.snapshot.phase).isEqualTo(TimerPhase.FINISHED)
        assertThat(f.wakes.active).isEqualTo(0)
        f.engine.close()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TimerEngineSchedulingTest {
    @Test
    fun longExactRunLetsCpuSleepUntilAlarmHandsOffToBoundedFinalWindow() = runTest {
        val f = fixture()
        f.start(duration = TimerLimits.DEFAULT_DURATION)
        assertThat(f.wakes.active).isEqualTo(0)
        val plan = f.alarms.plans.last()
        f.clock.offset = plan.finalWindowElapsed
        f.send(TimerCommand.AlarmFired(plan.runId, plan.generation, AlarmKind.FINAL_WINDOW))
        assertThat(f.wakes.active).isGreaterThan(0)
        advanceTimeBy(plan.endElapsed - plan.finalWindowElapsed)
        runCurrent()
        assertThat(f.playback.stopTimes).containsExactly(plan.endElapsed)
        assertThat(f.wakes.active).isEqualTo(0)
        f.engine.close()
    }

    @Test
    fun noExactPermissionHoldsBoundedFallbackAndSettingsRescheduleWithoutWarningWindow() = runTest {
        val f = fixture(exact = false)
        f.start(duration = TimerLimits.DEFAULT_DURATION)
        assertThat(f.wakes.active).isGreaterThan(0)
        assertThat(f.engine.state.value.degradedReason).isEqualTo("Exact alarms off")
        val oldPlan = f.alarms.plans.last()
        f.send(TimerCommand.SettingsChanged(AppSettings(warning = WarningMode.OFF)))
        val newPlan = f.alarms.plans.last()
        assertThat(newPlan.generation).isGreaterThan(oldPlan.generation)
        assertThat(newPlan.finalWindowElapsed).isEqualTo(newPlan.endElapsed - NO_WARNING_WINDOW)
        f.engine.close()
        assertThat(f.wakes.active).isEqualTo(0)
    }

    @Test
    fun unavailableForegroundServiceKeepsOnlyAlarmsUntilFinalWindowArrives() = runTest {
        val f = fixture(executionAllowed = false)
        f.send(TimerCommand.StartTest(false, "test"))
        assertThat(f.wakes.active).isEqualTo(0)
        assertThat(f.engine.state.value.degradedReason).contains("Open app")
        val plan = f.alarms.plans.last()
        f.send(TimerCommand.AlarmFired(plan.runId, plan.generation, AlarmKind.FINAL_WINDOW))
        assertThat(f.wakes.active).isGreaterThan(0)
        advanceTimeBy(TEST_SECONDS)
        runCurrent()
        assertThat(f.playback.stopTimes).containsExactly(TEST_SECONDS)
        assertThat(f.wakes.active).isEqualTo(0)
        f.engine.close()
    }

    @Test
    fun serviceLossReleasesRuntimeLocksAndResumingServiceRestoresProtection() = runTest {
        val f = fixture(exact = false)
        f.start()
        assertThat(f.wakes.active).isGreaterThan(0)
        f.send(TimerCommand.Reconcile("lost", executionAllowed = false))
        assertThat(f.wakes.active).isEqualTo(0)
        assertThat(f.snapshot.isActive).isTrue()
        f.send(TimerCommand.Reconcile("restored", serviceRestarted = true, executionAllowed = true))
        assertThat(f.wakes.active).isGreaterThan(0)
        assertThat(f.snapshot.run?.hasWhileInUse).isFalse()
        f.engine.close()
    }

    @Test
    fun callDefersAndReconcileDoesNotResetTwoHourDeadline() = runTest {
        val f = fixture()
        f.playback.interruption = Interruption.CALL
        f.send(TimerCommand.StartTest(true, "test"))
        advanceTimeBy(TEST_SECONDS)
        runCurrent()
        assertThat(f.snapshot.phase).isEqualTo(TimerPhase.DEFERRED)
        val until = f.snapshot.run!!.deferredUntilElapsed!!
        advanceTimeBy(TimerLimits.MINUTE)
        f.send(TimerCommand.Reconcile("mode callback"))
        runCurrent()
        assertThat(f.snapshot.run!!.deferredUntilElapsed).isEqualTo(until)
        advanceTimeBy(until - f.clock.elapsedRealtime())
        runCurrent()
        assertThat(f.snapshot.lastResult?.status).isEqualTo(StopStatus.INTERRUPTED)
        assertThat(f.wakes.active).isEqualTo(0)
        f.engine.close()
    }

    @Test
    fun clearingCallRetriesStopAndReleasesEveryLease() = runTest {
        val f = fixture()
        f.playback.interruption = Interruption.ALARM
        f.send(TimerCommand.StartTest(true, "test"))
        advanceTimeBy(TEST_SECONDS)
        runCurrent()
        assertThat(f.snapshot.phase).isEqualTo(TimerPhase.DEFERRED)
        f.playback.interruption = Interruption.NONE
        advanceTimeBy(TimerLimits.SECOND)
        runCurrent()
        assertThat(f.snapshot.lastResult?.status).isEqualTo(StopStatus.STOPPED)
        assertThat(f.wakes.active).isEqualTo(0)
        f.engine.close()
    }

    @Test
    fun wallClockChangeDoesNotChangeElapsedDeadlineAndCloseInvalidatesEffects() = runTest {
        val f = fixture()
        f.send(TimerCommand.StartTest(true, "test"))
        val deadline = f.snapshot.run!!.endElapsed
        f.clock.wallOffset = Long.MAX_VALUE / 2
        f.send(TimerCommand.Reconcile("time changed"))
        assertThat(f.snapshot.run!!.endElapsed).isEqualTo(deadline)
        f.engine.close()
        assertThat(f.wakes.active).isEqualTo(0)
        advanceTimeBy(TEST_SECONDS)
        runCurrent()
        assertThat(f.playback.stopTimes).isEmpty()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun TestScope.fixture(
    saved: TimerSnapshot = TimerSnapshot(),
    initialElapsed: Long = 0,
    boot: Int = 7,
    interactive: Boolean = true,
    exact: Boolean = true,
    executionAllowed: Boolean = true,
    loadFailure: Boolean = false,
    forceStopped: Boolean = false,
): Fixture {
    val clock = FakeClock(testScheduler, boot, initialElapsed)
    val store = MemorySnapshots(saved, loadFailure)
    val alarms = FakeAlarms(exact)
    val wakes = FakeWakes()
    val feedback = FakeFeedback(interactive)
    val playback = FakePlayback(clock, store)
    val engine = TimerEngine(
        TimerDependencies(
            clock, store, MemorySettings(), alarms, wakes, feedback, playback,
            Diagnostics {
            },
            RecoveryInspector { forceStopped },
        ),
        backgroundScope,
        initialExecutionAllowed = executionAllowed,
    )
    runCurrent()
    return Fixture(this, engine, clock, store, alarms, wakes, feedback, playback)
}

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LongParameterList") // Named fixture ports make state/side-effect assertions explicit.
private class Fixture(
    val scope: TestScope,
    val engine: TimerEngine,
    val clock: FakeClock,
    val store: MemorySnapshots,
    val alarms: FakeAlarms,
    val wakes: FakeWakes,
    val feedback: FakeFeedback,
    val playback: FakePlayback,
) {
    val snapshot: TimerSnapshot get() = engine.state.value.snapshot
    suspend fun send(command: TimerCommand): com.sujal.sleeptimer.core.model.CommandResult {
        val result = engine.send(command)
        scope.runCurrent()
        return result
    }
    suspend fun start(duration: Long = 2 * TimerLimits.MINUTE, id: String = "start") {
        assertThat(send(TimerCommand.Start(duration, StartSource.ACTIVITY, true, id)).accepted).isTrue()
    }
}

private class FakeClock(private val scheduler: TestCoroutineScheduler, private val boot: Int, var offset: Long) :
    Clock {
    var wallOffset = 0L
    override fun elapsedRealtime(): Long = scheduler.currentTime + offset
    override fun wallTimeMillis(): Long = elapsedRealtime() + wallOffset
    override fun bootCount(): Int = boot
}

private class MemorySnapshots(var value: TimerSnapshot, private val failLoad: Boolean) : SnapshotStore {
    var failSave = false
    override suspend fun load(): TimerSnapshot {
        if (failLoad) throw IOException("Unreadable snapshot")
        return value
    }
    override suspend fun save(snapshot: TimerSnapshot) {
        if (failSave) throw IOException("Read-only snapshot")
        value = snapshot
    }
}

private class MemorySettings : SettingsStore {
    override val data = MutableStateFlow(AppSettings())
    override suspend fun update(transform: (AppSettings) -> AppSettings): AppSettings {
        data.value = transform(data.value)
        return data.value
    }
}

private class FakeAlarms(private val exact: Boolean) : AlarmScheduler {
    val plans = mutableListOf<AlarmPlan>()
    val cancelled = mutableListOf<Pair<String, Long>>()
    override fun canScheduleExactAlarms(): Boolean = exact
    override fun schedule(plan: AlarmPlan): Boolean {
        plans.add(plan)
        return exact
    }
    override fun cancel(runId: String, generation: Long) {
        cancelled.add(runId to generation)
    }
}

private class FakeWakes : WakeLocks {
    var active = 0
    val timeouts = mutableListOf<Long>()
    override fun acquire(reason: String, timeoutMillis: Long): WakeLease {
        assertThat(timeoutMillis).isGreaterThan(0)
        active += 1
        timeouts.add(timeoutMillis)
        var released = false
        return WakeLease {
            check(!released) { "Lease released twice" }
            released = true
            active -= 1
        }
    }
}

private class FakeFeedback(private val interactive: Boolean) : TimerFeedback {
    val warnings = mutableListOf<WarningMode>()
    override fun warning(mode: WarningMode) {
        warnings.add(mode)
    }
    override fun isInteractive(): Boolean = interactive
}

private class FakePlayback(private val clock: Clock, private val store: MemorySnapshots) : PlaybackController {
    var interruption = Interruption.NONE
    var fadeBlock: CompletableDeferred<Unit>? = null
    val fadeStartedAt = mutableListOf<Long>()
    val stopTimes = mutableListOf<Long>()
    val recoverAllowPlaying = mutableListOf<Boolean>()
    var volumeWrites = 0
    override suspend fun observe(): PlaybackObservation = PlaybackObservation(true, interruption = interruption)
    override suspend fun fade(
        run: TimerRun,
        settings: AppSettings,
        token: EffectToken,
        onRecord: suspend (VolumeRecord?) -> Unit,
    ): FadeOutcome {
        if (run.fade != null) return FadeOutcome(run.fade)
        fadeStartedAt.add(clock.elapsedRealtime())
        val record = volumeRecord().copy(expiresElapsed = run.endElapsed + TimerLimits.RESTORE_WINDOW)
        onRecord(record)
        assertThat(store.value.run?.fade).isEqualTo(record)
        token.guarded { volumeWrites += 1 }
        fadeBlock?.await()
        return FadeOutcome(record)
    }
    override suspend fun stop(
        run: TimerRun,
        settings: AppSettings,
        token: EffectToken,
        onRecord: suspend (VolumeRecord?) -> Unit,
    ): StopResult {
        if (interruption == Interruption.CALL ||
            interruption == Interruption.ALARM
        ) {
            return StopResult(StopStatus.DEFERRED)
        }
        stopTimes.add(clock.elapsedRealtime())
        return StopResult(StopStatus.STOPPED, StopMethod.MEDIA_KEY, clock.elapsedRealtime(), isTest = run.isTest)
    }
    override suspend fun recoverFade(record: VolumeRecord, token: EffectToken, allowWhilePlaying: Boolean): Boolean {
        recoverAllowPlaying.add(allowWhilePlaying)
        return allowWhilePlaying
    }
    override fun abandonFocus(ownerId: String) = Unit
}

private fun savedRun(end: Long = 2 * TimerLimits.MINUTE): TimerSnapshot = TimerSnapshot(
    bootCount = 7,
    phase = TimerPhase.RUNNING,
    scheduleGeneration = 1,
    run = TimerRun("restored", 0, end, end, StartSource.ACTIVITY, true),
)

private fun volumeRecord(): VolumeRecord = VolumeRecord(
    originalIndex = 8,
    minimumIndex = 0,
    lastWrittenIndex = 2,
    route = OutputRoute("test-route"),
    capturedElapsed = 0,
    expiresElapsed = TimerLimits.RESTORE_WINDOW,
)

private const val TEST_SECONDS = 10 * TimerLimits.SECOND
private const val TEST_FADE_START = 5 * TimerLimits.SECOND
private const val EXTENSION = 10 * TimerLimits.MINUTE
private const val NO_WARNING_WINDOW = 20 * TimerLimits.SECOND
