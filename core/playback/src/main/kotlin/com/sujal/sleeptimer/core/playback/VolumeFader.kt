package com.sujal.sleeptimer.core.playback

import com.sujal.sleeptimer.core.model.AppSettings
import com.sujal.sleeptimer.core.model.Clock
import com.sujal.sleeptimer.core.model.Diagnostics
import com.sujal.sleeptimer.core.model.EffectToken
import com.sujal.sleeptimer.core.model.FadeOutcome
import com.sujal.sleeptimer.core.model.Interruption
import com.sujal.sleeptimer.core.model.PlaybackBackend
import com.sujal.sleeptimer.core.model.PlaybackObservation
import com.sujal.sleeptimer.core.model.TimerLimits
import com.sujal.sleeptimer.core.model.TimerRun
import com.sujal.sleeptimer.core.model.VolumeRecord
import com.sujal.sleeptimer.core.model.VolumeState
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

@Suppress("ReturnCount") // Safety guards abort volume writes immediately.
internal class VolumeFader(
    private val backend: PlaybackBackend,
    private val clock: Clock,
    private val diagnostics: Diagnostics,
) {
    suspend fun fade(
        run: TimerRun,
        settings: AppSettings,
        token: EffectToken,
        onRecord: suspend (VolumeRecord?) -> Unit,
    ): FadeOutcome {
        run.fade?.let { return FadeOutcome(it, aborted = !it.restoreAllowed) }
        val observation = backend.observe()
        val initial = observation.volume
        val duration = minOf(
            settings.fadeMillis,
            run.endElapsed - clock.elapsedRealtime(),
            if (run.isTest) TEST_FADE_CAP else TimerLimits.MINUTE,
        )
        val capable = token.isValid() && backend.canControlAudio(run) && duration > 0
        if (!capable || !canFade(observation, initial)) {
            diagnostics.record("fade_skipped_unsafe_or_unavailable")
            return FadeOutcome(null)
        }
        checkNotNull(initial)
        val started = clock.elapsedRealtime()
        val deadline = started + duration
        var record = VolumeRecord(
            originalIndex = initial.index,
            minimumIndex = initial.minimum,
            lastWrittenIndex = initial.index,
            route = checkNotNull(initial.route),
            capturedElapsed = started,
            expiresElapsed = run.endElapsed + TimerLimits.RESTORE_WINDOW,
        )
        onRecord(record)
        while (token.isValid()) {
            currentCoroutineContext().ensureActive()
            val now = clock.elapsedRealtime()
            val progress = ((now - started).toDouble() / duration).coerceIn(0.0, 1.0)
            val remaining = 1.0 - progress
            val next = initial.minimum + ((initial.index - initial.minimum) * remaining * remaining).roundToInt()
            if (next != record.lastWrittenIndex) {
                val advanced = advanceFade(record, next, token, onRecord)
                if (advanced.aborted) return advanced
                record = checkNotNull(advanced.record)
            }
            if (now >= deadline) return FadeOutcome(record)
            delay(minOf(POLL_INTERVAL, deadline - now))
        }
        return FadeOutcome(record, aborted = true)
    }

    private suspend fun advanceFade(
        record: VolumeRecord,
        next: Int,
        token: EffectToken,
        onRecord: suspend (VolumeRecord?) -> Unit,
    ): FadeOutcome {
        val before = backend.observe()
        if (!matches(record, before) || before.interruption != Interruption.NONE || !before.playing) {
            val invalidated = record.copy(restoreAllowed = matches(record, before))
            onRecord(invalidated)
            diagnostics.record("fade_aborted_observation_changed")
            return FadeOutcome(invalidated, aborted = true)
        }
        val prepared = record.copy(lastWrittenIndex = next)
        // Persist original route/index and the intended write before touching the shared stream.
        onRecord(prepared)
        val changed = writeChecked(record, next, token, allowPlaying = true)
        if (!changed) {
            val aborted = prepared.copy(restoreAllowed = false)
            if (token.isValid()) onRecord(aborted)
            diagnostics.record("fade_aborted_write_or_readback")
            return FadeOutcome(aborted, aborted = true)
        }
        return FadeOutcome(prepared)
    }

    suspend fun restoreWhenQuiet(record: VolumeRecord, token: EffectToken): Boolean {
        if (!validRecord(record)) return false
        var quietSince: Long? = null
        val settlingMillis = QUIET_CONFIRMATION + PLAYER_SETTLING
        while (token.isValid() && clock.elapsedRealtime() < record.expiresElapsed) {
            currentCoroutineContext().ensureActive()
            val observation = backend.observe()
            if (!matches(record, observation)) {
                diagnostics.record("restore_dropped_route_or_user_volume_changed")
                return false
            }
            if (observation.playing || observation.interruption != Interruption.NONE) {
                quietSince = null
            } else {
                val now = clock.elapsedRealtime()
                if (quietSince == null) quietSince = now
                if (now - quietSince >= settlingMillis) {
                    val restored = writeChecked(record, record.originalIndex, token, allowPlaying = false)
                    diagnostics.record(if (restored) "volume_restored_safely" else "restore_rejected")
                    return restored
                }
            }
            delay(minOf(POLL_INTERVAL, record.expiresElapsed - clock.elapsedRealtime()).coerceAtLeast(1))
        }
        diagnostics.record("restore_expired_or_cancelled")
        return false
    }

    suspend fun rampCancellation(record: VolumeRecord, token: EffectToken): Boolean {
        if (!validRecord(record)) return false
        val started = clock.elapsedRealtime()
        var current = record
        while (token.isValid() && clock.elapsedRealtime() < record.expiresElapsed) {
            currentCoroutineContext().ensureActive()
            val progress = ((clock.elapsedRealtime() - started).toDouble() / CANCELLATION_RAMP).coerceIn(0.0, 1.0)
            val next = record.lastWrittenIndex + ((record.originalIndex - record.lastWrittenIndex) * progress).toInt()
            if (next != current.lastWrittenIndex) {
                if (!writeChecked(current, next, token, allowPlaying = true)) return false
                current = current.copy(lastWrittenIndex = next)
            }
            if (progress >= 1.0) return true
            delay(RAMP_INTERVAL)
        }
        return false
    }

    private fun canFade(observation: PlaybackObservation, volume: VolumeState?): Boolean =
        observation.localPlaying && !observation.remotePlaying && observation.remoteObservable &&
            observation.interruption == Interruption.NONE &&
            volume != null && !volume.fixed && volume.route != null && volume.index > volume.minimum &&
            volume.minimum >= 0 && volume.index <= volume.maximum

    private fun validRecord(record: VolumeRecord): Boolean =
        record.restoreAllowed && record.minimumIndex >= 0 && record.lastWrittenIndex >= record.minimumIndex &&
            record.originalIndex >= record.lastWrittenIndex && clock.elapsedRealtime() < record.expiresElapsed

    private fun matches(record: VolumeRecord, observation: PlaybackObservation): Boolean =
        observation.remoteObservable && observation.volume?.let { volume ->
            !volume.fixed && volume.route == record.route && volume.index == record.lastWrittenIndex &&
                record.originalIndex <= volume.maximum
        } == true

    @Suppress("TooGenericExceptionCaught") // OEM binder rejection is a failed write, never an upward correction.
    private fun writeChecked(previous: VolumeRecord, target: Int, token: EffectToken, allowPlaying: Boolean): Boolean =
        token.guarded {
            val observation = backend.observe()
            val targetAllowed =
                target in previous.minimumIndex..previous.originalIndex && (allowPlaying || !observation.playing)
            val safe = matches(previous, observation) && observation.interruption == Interruption.NONE && targetAllowed
            if (!safe) {
                false
            } else {
                try {
                    backend.setVolume(target)
                    val readback = backend.observe()
                    readback.volume?.let { it.route == previous.route && it.index == target } == true
                } catch (error: CancellationException) {
                    throw error
                } catch (error: RuntimeException) {
                    diagnostics.record("volume_write_rejected_${error.javaClass.simpleName}")
                    false
                }
            }
        } == true

    private companion object {
        const val POLL_INTERVAL = 250L
        const val RAMP_INTERVAL = 50L
        const val TEST_FADE_CAP = 5 * TimerLimits.SECOND
        const val QUIET_CONFIRMATION = TimerLimits.SECOND
        const val PLAYER_SETTLING = 1_500L
        const val CANCELLATION_RAMP = TimerLimits.SECOND
    }
}
