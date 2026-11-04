package com.sujal.sleeptimer.core.playback

import com.sujal.sleeptimer.core.model.AppSettings
import com.sujal.sleeptimer.core.model.Clock
import com.sujal.sleeptimer.core.model.Diagnostics
import com.sujal.sleeptimer.core.model.EffectToken
import com.sujal.sleeptimer.core.model.FadeOutcome
import com.sujal.sleeptimer.core.model.PlaybackBackend
import com.sujal.sleeptimer.core.model.PlaybackController
import com.sujal.sleeptimer.core.model.PlaybackObservation
import com.sujal.sleeptimer.core.model.StopResult
import com.sujal.sleeptimer.core.model.StopStatus
import com.sujal.sleeptimer.core.model.TimerRun
import com.sujal.sleeptimer.core.model.VolumeRecord
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The engine owns a bounded wake lease while executing these elapsed-realtime effects. */
class PlaybackCoordinator(
    private val backend: PlaybackBackend,
    private val clock: Clock,
    diagnostics: Diagnostics,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PlaybackController {
    private val fader = VolumeFader(backend, clock, diagnostics)
    private val stopper = PlaybackStopper(backend, clock, diagnostics)

    override suspend fun observe(): PlaybackObservation = withContext(dispatcher) { backend.observe() }

    override suspend fun fade(
        run: TimerRun,
        settings: AppSettings,
        token: EffectToken,
        onRecord: suspend (VolumeRecord?) -> Unit,
    ): FadeOutcome = withContext(dispatcher) { fader.fade(run, settings, token, onRecord) }

    override suspend fun stop(
        run: TimerRun,
        settings: AppSettings,
        token: EffectToken,
        onRecord: suspend (VolumeRecord?) -> Unit,
    ): StopResult = withContext(dispatcher) {
        val result = try {
            stopper.stop(run, token)
        } finally {
            // Release focus before automatic restore: abandoning focus can make a player resume.
            backend.abandonFocus(run.id)
        }
        if (token.isValid() && result.status != StopStatus.DEFERRED && result.status != StopStatus.CANCELLED) {
            run.fade?.let { record ->
                if (settings.restoreVolume) fader.restoreWhenQuiet(record, token)
                if (token.isValid()) onRecord(null)
            }
        }
        result.copy(
            completedElapsed = clock.elapsedRealtime(),
            isTest = run.isTest,
            localOnly = result.localOnly || !backend.observe().remoteObservable,
        )
    }

    override suspend fun recoverFade(record: VolumeRecord, token: EffectToken, allowWhilePlaying: Boolean): Boolean =
        withContext(dispatcher) {
            if (allowWhilePlaying) {
                fader.rampCancellation(record, token)
            } else {
                val observation = backend.observe()
                if (observation.playing || observation.mustDefer || !observation.remoteObservable) {
                    false
                } else {
                    fader.restoreWhenQuiet(record, token)
                }
            }
        }

    override fun abandonFocus(ownerId: String) = backend.abandonFocus(ownerId)
}
