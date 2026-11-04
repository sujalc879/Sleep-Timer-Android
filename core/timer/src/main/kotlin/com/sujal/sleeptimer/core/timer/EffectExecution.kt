package com.sujal.sleeptimer.core.timer

import com.sujal.sleeptimer.core.model.AppSettings
import com.sujal.sleeptimer.core.model.EffectToken
import com.sujal.sleeptimer.core.model.StopResult
import com.sujal.sleeptimer.core.model.StopStatus
import com.sujal.sleeptimer.core.model.TimerRun
import com.sujal.sleeptimer.core.model.WakeLease
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class EffectExecution(
    val run: TimerRun,
    val currentSettings: AppSettings,
    val currentToken: EffectToken,
    val cancelling: Boolean,
    val previous: Job?,
    val recoverFade: Boolean,
    val allowPlayingRecovery: Boolean,
    val criticalLease: WakeLease?,
)

@Suppress("TooGenericExceptionCaught") // Isolate unexpected effect failures; cancellation propagates.
internal fun EngineRuntime.launchEffect(execution: EffectExecution): Job = with(execution) {
    scope.launch {
        try {
            previous?.join()
            if (!currentToken.isValid()) return@launch
            if (cancelling) {
                cancelRecovery(run, currentToken, allowPlayingRecovery)
            } else {
                executePipeline(run, currentSettings, currentToken, recoverFade, allowPlayingRecovery)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            diagnostics.record("timer effect failed: ${error.javaClass.simpleName}")
            events.send(
                EngineEvent.Finished(
                    run.id,
                    currentToken,
                    StopResult(
                        StopStatus.FAILED,
                        completedElapsed = clock.elapsedRealtime(),
                        detail = "Playback could not be verified",
                        isTest = run.isTest,
                    ),
                ),
            )
        } finally {
            playback.abandonFocus(run.id)
            criticalLease?.release()
        }
    }
}
