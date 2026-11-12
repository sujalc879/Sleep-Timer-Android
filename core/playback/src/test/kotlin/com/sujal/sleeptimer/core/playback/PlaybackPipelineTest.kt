package com.sujal.sleeptimer.core.playback

import com.google.common.truth.Truth.assertThat
import com.sujal.sleeptimer.core.model.AppSettings
import com.sujal.sleeptimer.core.model.FocusResult
import com.sujal.sleeptimer.core.model.Interruption
import com.sujal.sleeptimer.core.model.StopMethod
import com.sujal.sleeptimer.core.model.StopStatus
import com.sujal.sleeptimer.core.model.TimerLimits
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PlaybackPipelineTest {
    @Test
    fun nothingPlayingSkipsEveryPlaybackMutation() = runTest {
        val fixture = PlaybackFixture(testScheduler)
        fixture.backend.playing = false
        fixture.backend.remoteObservable = false
        val result = fixture.coordinator.stop(fixture.run(), AppSettings(), fixture.token) {}

        assertThat(result.status).isEqualTo(StopStatus.NOTHING_PLAYING)
        assertThat(result.detail).contains("remote playback unverified")
        assertThat(fixture.backend.sessionCalls).isEqualTo(0)
        assertThat(fixture.backend.focusCalls).isEqualTo(0)
        assertThat(fixture.backend.keyCalls).isEqualTo(0)
    }

    @Test
    fun sessionPauseStopsEscalationIncludingRemotePlayback() = runTest {
        val fixture = PlaybackFixture(testScheduler)
        fixture.backend.playing = false
        fixture.backend.remotePlaying = true
        fixture.backend.sessionAction = { backend ->
            backend.remotePlaying = false
            true
        }
        val result = fixture.coordinator.stop(fixture.run(), AppSettings(), fixture.token) {}

        assertThat(result.status).isEqualTo(StopStatus.STOPPED)
        assertThat(result.method).isEqualTo(StopMethod.MEDIA_SESSION)
        assertThat(fixture.backend.focusCalls).isEqualTo(0)
        assertThat(fixture.backend.keyCalls).isEqualTo(0)
    }

    @Test
    fun grantedFocusStopsPlaybackAndIsAbandoned() = runTest {
        val fixture = PlaybackFixture(testScheduler)
        fixture.backend.focusAction = { backend ->
            backend.playing = false
            FocusResult.GRANTED
        }
        val result = fixture.coordinator.stop(fixture.run(), AppSettings(), fixture.token) {}

        assertThat(result.status).isEqualTo(StopStatus.STOPPED)
        assertThat(result.method).isEqualTo(StopMethod.AUDIO_FOCUS)
        assertThat(fixture.backend.keyCalls).isEqualTo(0)
        assertThat(fixture.backend.abandonedAt).hasSize(1)
        assertThat(fixture.diagnostics).contains("playback_attempt_late_ms:-60000")
        assertThat(fixture.diagnostics).contains("playback_verification:STOPPED:late_ms=-60000")
    }

    @Test
    fun delayedFocusHasBoundedVerificationAndCanCompleteBeforeKeys() = runTest {
        val fixture = PlaybackFixture(testScheduler)
        fixture.backend.focusAction = { FocusResult.DELAYED }
        fixture.backend.observeHook = { backend ->
            if (fixture.clock.elapsedRealtime() >= PlaybackFixture.BASE_ELAPSED + FOCUS_DELAY) {
                backend.focusState = FocusResult.GRANTED
                backend.playing = false
            }
        }
        val result = fixture.coordinator.stop(fixture.run(), AppSettings(), fixture.token) {}

        assertThat(result.method).isEqualTo(StopMethod.AUDIO_FOCUS)
        assertThat(fixture.backend.keyCalls).isEqualTo(0)
        assertThat(result.completedElapsed - PlaybackFixture.BASE_ELAPSED).isEqualTo(FOCUS_DELAY)
    }

    @Test
    fun failedFocusFallsBackToPauseKey() = runTest {
        val fixture = PlaybackFixture(testScheduler)
        val result = fixture.coordinator.stop(fixture.run(), AppSettings(), fixture.token) {}

        assertThat(result.status).isEqualTo(StopStatus.STOPPED)
        assertThat(result.method).isEqualTo(StopMethod.MEDIA_KEY)
        assertThat(fixture.backend.keyCalls).isEqualTo(1)
    }

    @Test
    fun playerIgnoringAllMethodsGetsOnlyOneKeyRetryAndFailure() = runTest {
        val fixture = PlaybackFixture(testScheduler)
        fixture.backend.keyAction = {}
        val result = fixture.coordinator.stop(fixture.run(), AppSettings(), fixture.token) {}

        assertThat(result.status).isEqualTo(StopStatus.FAILED)
        assertThat(fixture.backend.keyCalls).isEqualTo(2)
        assertThat(result.completedElapsed - PlaybackFixture.BASE_ELAPSED).isEqualTo(KEY_VERIFICATION_TOTAL)
    }

    @Test
    fun callOrAlarmDefersWithoutFocusOrKeys() = runTest {
        val fixture = PlaybackFixture(testScheduler)
        fixture.backend.interruption = Interruption.ALARM
        val result = fixture.coordinator.stop(fixture.run(), AppSettings(), fixture.token) {}

        assertThat(result.status).isEqualTo(StopStatus.DEFERRED)
        assertThat(fixture.backend.focusCalls).isEqualTo(0)
        assertThat(fixture.backend.keyCalls).isEqualTo(0)
        assertThat(fixture.backend.sessionCalls).isEqualTo(0)
    }

    @Test
    fun unknownInterruptionOrNoForegroundCapabilitySkipsFocus() = runTest {
        val fixture = PlaybackFixture(testScheduler)
        fixture.backend.interruption = Interruption.UNKNOWN
        fixture.backend.canControl = false
        val result = fixture.coordinator.stop(fixture.run(), AppSettings(), fixture.token) {}

        assertThat(result.method).isEqualTo(StopMethod.MEDIA_KEY)
        assertThat(fixture.backend.focusCalls).isEqualTo(0)
    }

    @Test
    fun losingRemoteVisibilityCannotBecomeFalseSuccess() = runTest {
        val fixture = PlaybackFixture(testScheduler)
        fixture.backend.playing = false
        fixture.backend.remotePlaying = true
        fixture.backend.sessionAction = { backend ->
            backend.remoteObservable = false
            backend.remotePlaying = false
            true
        }
        val result = fixture.coordinator.stop(fixture.run(), AppSettings(), fixture.token) {}

        assertThat(result.status).isEqualTo(StopStatus.FAILED)
        assertThat(fixture.backend.focusCalls).isEqualTo(0)
    }

    @Test
    fun screenOffWatchdogPausesAutoResumeAndEndsWhenScreenTurnsOn() = runTest {
        val fixture = PlaybackFixture(testScheduler)
        fixture.backend.interactive = false
        var resumed = false
        fixture.backend.focusAction = { backend ->
            backend.playing = false
            FocusResult.GRANTED
        }
        fixture.backend.observeHook = { backend ->
            val elapsed = fixture.clock.elapsedRealtime() - PlaybackFixture.BASE_ELAPSED
            if (!resumed && elapsed >= RESUME_AFTER) {
                backend.playing = true
                resumed = true
            }
            if (elapsed >= SCREEN_ON_AFTER) backend.interactive = true
        }
        val result = fixture.coordinator.stop(fixture.run(), AppSettings(), fixture.token) {}

        assertThat(result.status).isEqualTo(StopStatus.STOPPED)
        assertThat(fixture.backend.keyCalls).isEqualTo(1)
        assertThat(result.completedElapsed - PlaybackFixture.BASE_ELAPSED).isAtLeast(SCREEN_ON_AFTER)
        assertThat(result.completedElapsed - PlaybackFixture.BASE_ELAPSED).isAtMost(SCREEN_ON_AFTER + WATCHDOG_POLL)
    }

    @Test
    fun pendingRestoreWaitsForActualSilenceInsteadOfRaisingActiveAudio() = runTest {
        val fixture = PlaybackFixture(testScheduler)
        val record = fixture.record()
        fixture.backend.volumeIndex = record.lastWrittenIndex
        fixture.backend.keyAction = {}
        fixture.backend.observeHook = { backend ->
            if (fixture.clock.elapsedRealtime() - PlaybackFixture.BASE_ELAPSED >=
                MANUAL_PAUSE_AFTER
            ) {
                backend.playing = false
            }
        }
        val result = fixture.coordinator.stop(fixture.run(fade = record), AppSettings(), fixture.token) {}

        assertThat(result.status).isEqualTo(StopStatus.FAILED)
        assertThat(fixture.backend.writes.single().playing).isFalse()
        assertThat(fixture.backend.writes.single().elapsed - PlaybackFixture.BASE_ELAPSED)
            .isAtLeast(MANUAL_PAUSE_AFTER + SETTLE_MILLIS)
    }

    @Test
    fun unresolvedRestoreExpiresWithoutAnUpwardWrite() = runTest {
        val fixture = PlaybackFixture(testScheduler)
        val record = fixture.record()
        fixture.backend.volumeIndex = record.lastWrittenIndex
        fixture.backend.keyAction = {}
        val result = fixture.coordinator.stop(fixture.run(fade = record), AppSettings(), fixture.token) {}

        assertThat(result.status).isEqualTo(StopStatus.FAILED)
        assertThat(result.completedElapsed).isEqualTo(record.expiresElapsed)
        assertThat(fixture.backend.writes).isEmpty()
    }

    private companion object {
        const val FOCUS_DELAY = 500L
        const val KEY_VERIFICATION_TOTAL = 6 * TimerLimits.SECOND
        const val RESUME_AFTER = TimerLimits.SECOND
        const val WATCHDOG_POLL = 250L
        const val SCREEN_ON_AFTER = 5 * TimerLimits.SECOND
        const val MANUAL_PAUSE_AFTER = 10 * TimerLimits.SECOND
        const val SETTLE_MILLIS = 2_500L
    }
}
