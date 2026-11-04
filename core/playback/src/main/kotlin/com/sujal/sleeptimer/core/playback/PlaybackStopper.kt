package com.sujal.sleeptimer.core.playback

import com.sujal.sleeptimer.core.model.Clock
import com.sujal.sleeptimer.core.model.Diagnostics
import com.sujal.sleeptimer.core.model.EffectToken
import com.sujal.sleeptimer.core.model.FocusResult
import com.sujal.sleeptimer.core.model.Interruption
import com.sujal.sleeptimer.core.model.PlaybackBackend
import com.sujal.sleeptimer.core.model.PlaybackObservation
import com.sujal.sleeptimer.core.model.StopMethod
import com.sujal.sleeptimer.core.model.StopResult
import com.sujal.sleeptimer.core.model.StopStatus
import com.sujal.sleeptimer.core.model.TimerLimits
import com.sujal.sleeptimer.core.model.TimerRun
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

@Suppress("ReturnCount") // Stop escalating immediately at every verified/deferred/cancelled boundary.
internal class PlaybackStopper(
    private val backend: PlaybackBackend,
    private val clock: Clock,
    private val diagnostics: Diagnostics,
) {
    suspend fun stop(run: TimerRun, token: EffectToken): StopResult {
        val probe = Probe(backend)
        val initial = probe.read()
        if (!token.isValid()) return result(StopStatus.CANCELLED)
        if (initial.mustDefer) return result(StopStatus.DEFERRED, detail = "Call or alarm in progress")
        if (probe.inactive(initial)) {
            val detail = if (initial.remoteObservable) {
                "Nothing playing"
            } else {
                "No local playback; remote playback unverified"
            }
            return result(StopStatus.NOTHING_PLAYING, detail = detail)
        }
        val firstAttempt = clock.elapsedRealtime()
        val stopped = attemptPipeline(run, token, probe, firstAttempt)
        if (stopped.status != StopStatus.STOPPED || backend.isInteractive()) return stopped
        return watchdog(token, probe, stopped)
    }

    private suspend fun attemptPipeline(
        run: TimerRun,
        token: EffectToken,
        probe: Probe,
        firstAttempt: Long,
    ): StopResult {
        val paused = token.guarded { if (!probe.read().mustDefer) backend.pauseSessions() else false } == true
        if (paused) {
            verificationResult(waitForInactive(token, probe), StopMethod.MEDIA_SESSION, firstAttempt)?.let { return it }
        }
        tryFocus(run, token, probe, firstAttempt)?.let { return it }
        return tryKeys(token, probe, firstAttempt)
    }

    private suspend fun tryFocus(run: TimerRun, token: EffectToken, probe: Probe, firstAttempt: Long): StopResult? {
        val beforeFocus = probe.read()
        if (beforeFocus.mustDefer) return result(StopStatus.DEFERRED, firstAttempt = firstAttempt)
        if (probe.inactive(beforeFocus)) return result(StopStatus.STOPPED, firstAttempt = firstAttempt)
        if (canRequestFocus(beforeFocus, run)) {
            val focus = token.guarded {
                val current = probe.read()
                if (canRequestFocus(current, run) && !probe.inactive(current)) {
                    backend.requestFocus(run.id)
                } else {
                    FocusResult.FAILED
                }
            } ?: return result(StopStatus.CANCELLED)
            diagnostics.record("audio_focus_${focus.name.lowercase()}")
            if (focus != FocusResult.FAILED) {
                verificationResult(waitForInactive(token, probe, run.id), StopMethod.AUDIO_FOCUS, firstAttempt)
                    ?.let { return it }
            }
        }
        return null
    }

    private fun canRequestFocus(observation: PlaybackObservation, run: TimerRun): Boolean {
        val observable = observation.localPlaying || observation.remoteObservable
        return observation.interruption == Interruption.NONE && observable && backend.canControlAudio(run)
    }

    private suspend fun tryKeys(token: EffectToken, probe: Probe, firstAttempt: Long): StopResult {
        repeat(KEY_ATTEMPTS) {
            val beforeKey = probe.read()
            if (!token.isValid()) return result(StopStatus.CANCELLED)
            if (beforeKey.mustDefer) return result(StopStatus.DEFERRED, firstAttempt = firstAttempt)
            if (probe.inactive(beforeKey)) return result(StopStatus.STOPPED, firstAttempt = firstAttempt)
            token.guarded { if (!probe.read().mustDefer) backend.dispatchPause() }
            verificationResult(waitForInactive(token, probe), StopMethod.MEDIA_KEY, firstAttempt)?.let { return it }
        }
        return result(
            StopStatus.FAILED,
            detail = "Playback could not be confirmed stopped",
            firstAttempt = firstAttempt,
        )
    }

    private suspend fun waitForInactive(token: EffectToken, probe: Probe, focusOwner: String? = null): Verification {
        val deadline = clock.elapsedRealtime() + VERIFICATION_WINDOW
        while (token.isValid()) {
            currentCoroutineContext().ensureActive()
            val observation = probe.read()
            if (observation.mustDefer) return Verification.DEFERRED
            if (probe.inactive(observation)) return Verification.INACTIVE
            if (focusOwner != null && backend.focusResult(focusOwner) == FocusResult.FAILED) return Verification.ACTIVE
            val remaining = deadline - clock.elapsedRealtime()
            if (remaining <= 0) return Verification.ACTIVE
            delay(minOf(POLL_INTERVAL, remaining))
        }
        return Verification.CANCELLED
    }

    private suspend fun watchdog(token: EffectToken, probe: Probe, stopped: StopResult): StopResult {
        val deadline = clock.elapsedRealtime() + WATCHDOG_WINDOW
        while (token.isValid() && !backend.isInteractive() && clock.elapsedRealtime() < deadline) {
            currentCoroutineContext().ensureActive()
            val observation = probe.read()
            if (observation.mustDefer) return result(StopStatus.DEFERRED, firstAttempt = stopped.firstAttemptElapsed)
            if (!probe.inactive(observation)) {
                token.guarded {
                    if (!backend.isInteractive() && !probe.read().mustDefer) backend.pauseSessions()
                }
                token.guarded {
                    val current = probe.read()
                    if (!backend.isInteractive() && !current.mustDefer &&
                        !probe.inactive(current)
                    ) {
                        backend.dispatchPause()
                    }
                }
            }
            delay(minOf(POLL_INTERVAL, deadline - clock.elapsedRealtime()).coerceAtLeast(1))
        }
        if (!token.isValid()) return result(StopStatus.CANCELLED)
        val final = probe.read()
        if (final.mustDefer) return result(StopStatus.DEFERRED, firstAttempt = stopped.firstAttemptElapsed)
        return if (probe.inactive(final)) {
            stopped
        } else {
            result(
                StopStatus.FAILED,
                detail = "Playback resumed or remote state became unavailable",
                firstAttempt = stopped.firstAttemptElapsed,
            )
        }
    }

    private fun verificationResult(verification: Verification, method: StopMethod, firstAttempt: Long): StopResult? =
        when (verification) {
            Verification.INACTIVE -> result(StopStatus.STOPPED, method, firstAttempt = firstAttempt)
            Verification.DEFERRED -> result(StopStatus.DEFERRED, firstAttempt = firstAttempt)
            Verification.CANCELLED -> result(StopStatus.CANCELLED)
            Verification.ACTIVE -> null
        }

    private fun result(
        status: StopStatus,
        method: StopMethod = StopMethod.NONE,
        detail: String = "",
        firstAttempt: Long? = null,
    ): StopResult = StopResult(
        status = status,
        method = method,
        completedElapsed = clock.elapsedRealtime(),
        firstAttemptElapsed = firstAttempt,
        detail = detail,
    )

    private class Probe(private val backend: PlaybackBackend) {
        private var observedRemote = false

        fun read(): PlaybackObservation = backend.observe().also { observedRemote = observedRemote || it.remotePlaying }

        fun inactive(observation: PlaybackObservation): Boolean =
            !observation.playing && (!observedRemote || observation.remoteObservable)
    }

    private enum class Verification { INACTIVE, ACTIVE, DEFERRED, CANCELLED }

    private companion object {
        const val POLL_INTERVAL = 250L
        const val VERIFICATION_WINDOW = 3 * TimerLimits.SECOND
        const val WATCHDOG_WINDOW = 30 * TimerLimits.SECOND
        const val KEY_ATTEMPTS = 2
    }
}
