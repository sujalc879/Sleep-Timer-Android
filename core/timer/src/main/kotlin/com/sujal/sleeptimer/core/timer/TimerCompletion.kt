package com.sujal.sleeptimer.core.timer

import com.sujal.sleeptimer.core.model.AppSettings
import com.sujal.sleeptimer.core.model.EffectToken
import com.sujal.sleeptimer.core.model.StopResult
import com.sujal.sleeptimer.core.model.StopStatus

internal suspend fun EngineRuntime.lockAfterStop(
    runId: String,
    activeSettings: AppSettings,
    currentToken: EffectToken,
    result: StopResult,
) {
    val successful = result.status in setOf(StopStatus.STOPPED, StopStatus.NOTHING_PLAYING)
    if (!activeSettings.screenOff || currentRun(runId).isTest || !successful) return
    val observation = playback.observe()
    if (!observation.mustDefer && !observation.playing) currentToken.guarded { screenLocker.lockIfSafe() }
}
