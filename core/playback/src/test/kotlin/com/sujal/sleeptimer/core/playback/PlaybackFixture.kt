package com.sujal.sleeptimer.core.playback

import com.sujal.sleeptimer.core.model.Clock
import com.sujal.sleeptimer.core.model.Diagnostics
import com.sujal.sleeptimer.core.model.EffectGate
import com.sujal.sleeptimer.core.model.FocusResult
import com.sujal.sleeptimer.core.model.Interruption
import com.sujal.sleeptimer.core.model.OutputRoute
import com.sujal.sleeptimer.core.model.PlaybackBackend
import com.sujal.sleeptimer.core.model.PlaybackObservation
import com.sujal.sleeptimer.core.model.StartSource
import com.sujal.sleeptimer.core.model.TimerLimits
import com.sujal.sleeptimer.core.model.TimerRun
import com.sujal.sleeptimer.core.model.VolumeRecord
import com.sujal.sleeptimer.core.model.VolumeState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler

@OptIn(ExperimentalCoroutinesApi::class)
internal class PlaybackFixture(val scheduler: TestCoroutineScheduler) {
    val clock = object : Clock {
        override fun elapsedRealtime(): Long = BASE_ELAPSED + scheduler.currentTime
        override fun wallTimeMillis(): Long = elapsedRealtime()
        override fun bootCount(): Int = 1
    }
    val gate = EffectGate()
    val token = gate.next()
    val diagnostics = mutableListOf<String>()
    val backend = FakePlaybackBackend(clock)
    val coordinator = PlaybackCoordinator(
        backend,
        clock,
        Diagnostics { diagnostics += it },
        StandardTestDispatcher(scheduler),
    )

    fun run(duration: Long = TimerLimits.MINUTE, fade: VolumeRecord? = null, isTest: Boolean = false): TimerRun =
        TimerRun(
            id = "run-one",
            startedElapsed = clock.elapsedRealtime(),
            endElapsed = clock.elapsedRealtime() + duration,
            totalDurationMillis = duration,
            source = StartSource.ACTIVITY,
            hasWhileInUse = true,
            isTest = isTest,
            fade = fade,
        )

    fun record(): VolumeRecord = VolumeRecord(
        originalIndex = ORIGINAL_VOLUME,
        minimumIndex = MINIMUM_VOLUME,
        lastWrittenIndex = FADED_VOLUME,
        route = ROUTE,
        capturedElapsed = clock.elapsedRealtime(),
        expiresElapsed = clock.elapsedRealtime() + TimerLimits.RESTORE_WINDOW,
    )

    companion object {
        const val BASE_ELAPSED = 100_000L
        const val ORIGINAL_VOLUME = 6
        const val FADED_VOLUME = 2
        const val MINIMUM_VOLUME = 1
        val ROUTE = OutputRoute("verified-speaker")
    }
}

internal class FakePlaybackBackend(private val clock: Clock) : PlaybackBackend {
    var playing = true
    var remotePlaying = false
    var remoteObservable = true
    var interruption = Interruption.NONE
    var volumeIndex = PlaybackFixture.ORIGINAL_VOLUME
    var route: OutputRoute? = PlaybackFixture.ROUTE
    var fixed = false
    var canControl = true
    var interactive = true
    var focusCalls = 0
    var keyCalls = 0
    var sessionCalls = 0
    var focusState = FocusResult.FAILED
    var observeHook: (FakePlaybackBackend) -> Unit = {}
    var sessionAction: (FakePlaybackBackend) -> Boolean = { false }
    var focusAction: (FakePlaybackBackend) -> FocusResult = { FocusResult.FAILED }
    var keyAction: (FakePlaybackBackend) -> Unit = { it.playing = false }
    var volumeAction: (FakePlaybackBackend, Int) -> Unit = { backend, index -> backend.volumeIndex = index }
    val writes = mutableListOf<VolumeWrite>()
    val abandonedAt = mutableListOf<Long>()

    override fun observe(): PlaybackObservation {
        observeHook(this)
        return PlaybackObservation(
            localPlaying = playing,
            remotePlaying = remotePlaying,
            interruption = interruption,
            volume = VolumeState(volumeIndex, PlaybackFixture.MINIMUM_VOLUME, MAXIMUM_VOLUME, fixed, route),
            remoteObservable = remoteObservable,
        )
    }

    override fun pauseSessions(): Boolean {
        sessionCalls += 1
        return sessionAction(this)
    }

    override fun requestFocus(ownerId: String): FocusResult {
        focusCalls += 1
        focusState = focusAction(this)
        return focusState
    }

    override fun focusResult(ownerId: String): FocusResult = focusState

    override fun abandonFocus(ownerId: String) {
        abandonedAt += clock.elapsedRealtime()
    }

    override fun dispatchPause() {
        keyCalls += 1
        keyAction(this)
    }

    override fun setVolume(index: Int) {
        writes += VolumeWrite(index, playing || remotePlaying, clock.elapsedRealtime())
        volumeAction(this, index)
    }

    override fun canControlAudio(run: TimerRun): Boolean = canControl
    override fun isInteractive(): Boolean = interactive

    companion object {
        const val MAXIMUM_VOLUME = 15
    }
}

internal data class VolumeWrite(val index: Int, val playing: Boolean, val elapsed: Long)
