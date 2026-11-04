package com.sujal.sleeptimer.core.playback

import android.app.Application
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import com.sujal.sleeptimer.core.model.Clock
import com.sujal.sleeptimer.core.model.Diagnostics
import com.sujal.sleeptimer.core.model.FocusResult
import com.sujal.sleeptimer.core.model.Interruption
import com.sujal.sleeptimer.core.model.RuntimeCapabilities
import com.sujal.sleeptimer.core.model.StartSource
import com.sujal.sleeptimer.core.model.TimerRun
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.AudioDeviceInfoBuilder
import org.robolectric.shadows.ShadowAudioManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = Application::class)
class AndroidPlaybackBackendTest {
    private lateinit var audio: AudioManager
    private lateinit var shadowAudio: ShadowAudioManager
    private lateinit var capabilities: RuntimeCapabilities
    private lateinit var backend: AndroidPlaybackBackend

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        audio = context.getSystemService(AudioManager::class.java)
        shadowAudio = shadowOf(audio)
        capabilities = RuntimeCapabilities()
        backend = AndroidPlaybackBackend(context, capabilities, TestClock, Diagnostics {})
    }

    @Test
    fun missingForegroundCapabilityRejectsFocusAndVolumeWrites() {
        assertThat(backend.canControlAudio(run())).isFalse()
        assertThat(backend.requestFocus("run")).isEqualTo(FocusResult.FAILED)
        assertThrows(IllegalStateException::class.java) { backend.setVolume(FADED_INDEX) }
        assertThat(shadowAudio.lastAudioFocusRequest).isNull()
    }

    @Test
    fun foregroundStreamWriteChangesMusicOnly() {
        capabilities.foregroundServiceActive = true
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, ORIGINAL_INDEX, 0)
        val alarmBefore = audio.getStreamVolume(AudioManager.STREAM_ALARM)

        backend.setVolume(FADED_INDEX)

        assertThat(audio.getStreamVolume(AudioManager.STREAM_MUSIC)).isEqualTo(FADED_INDEX)
        assertThat(audio.getStreamVolume(AudioManager.STREAM_ALARM)).isEqualTo(alarmBefore)
        assertThat(backend.canControlAudio(run().copy(hasWhileInUse = false))).isTrue()
    }

    @Test
    fun probeDetectsCallAndAlarmWithoutPhoneStatePermission() {
        shadowAudio.setIsMusicActive(true)
        shadowAudio.setActivePlaybackConfigurationsFor(
            listOf(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()),
        )
        assertThat(backend.observe().interruption).isEqualTo(Interruption.ALARM)

        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        assertThat(backend.observe().interruption).isEqualTo(Interruption.CALL)
    }

    @Test
    fun missingPlaybackMetadataIsUnknownRatherThanFocusSafe() {
        shadowAudio.setIsMusicActive(true)
        shadowAudio.setActivePlaybackConfigurationsFor(emptyList())
        val observation = backend.observe()

        assertThat(observation.interruption).isEqualTo(Interruption.UNKNOWN)
        assertThat(observation.remoteObservable).isFalse()
        assertThat(observation.remotePlaying).isFalse()
    }

    @Test
    fun volumeFloorUsesCompatibilityBehaviorOnApi26() {
        shadowAudio.setStreamMinVolume(0)
        val observation = backend.observe()

        assertThat(observation.volume?.minimum).isEqualTo(if (Build.VERSION.SDK_INT < 28) 1 else 0)
    }

    @Test
    fun pauseKeyIsAlwaysDownThenUpAndNeverToggle() {
        backend.dispatchPause()

        val keys = shadowAudio.dispatchedMediaKeyEvents
        assertThat(keys.map { it.keyCode }).containsExactly(KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_PAUSE)
        assertThat(keys.map { it.action }).containsExactly(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP).inOrder()
    }

    @Test
    fun staleFocusCleanupCannotAbandonNewOwner() {
        capabilities.foregroundServiceActive = true
        shadowAudio.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        backend.requestFocus("old-run")
        val oldRequest = shadowAudio.lastAudioFocusRequest.audioFocusRequest
        backend.requestFocus("new-run")
        val newRequest = shadowAudio.lastAudioFocusRequest.audioFocusRequest

        backend.abandonFocus("old-run")

        assertThat(shadowAudio.lastAbandonedAudioFocusRequest).isSameInstanceAs(oldRequest)
        assertThat(backend.focusResult("new-run")).isEqualTo(FocusResult.GRANTED)
        backend.abandonFocus("new-run")
        assertThat(shadowAudio.lastAbandonedAudioFocusRequest).isSameInstanceAs(newRequest)
    }

    @Test
    @Config(sdk = [35])
    fun connectedOutputsAloneNeverDetermineRoute() {
        val speaker = AudioDeviceInfoBuilder.newBuilder().setType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER).build()
        val headphones = AudioDeviceInfoBuilder.newBuilder().setType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES).build()
        shadowAudio.setOutputDevices(listOf(speaker, headphones))
        shadowAudio.setAudioDevicesForAttributes(AndroidFocusOwner.MEDIA_ATTRIBUTES, ImmutableList.of(speaker))

        assertThat(backend.observe().volume?.route).isNull()
    }

    private fun run(): TimerRun = TimerRun(
        id = "run",
        startedElapsed = 0,
        endElapsed = 60_000,
        totalDurationMillis = 60_000,
        source = StartSource.ACTIVITY,
        hasWhileInUse = true,
    )

    private object TestClock : Clock {
        override fun elapsedRealtime(): Long = 0
        override fun wallTimeMillis(): Long = 0
        override fun bootCount(): Int = 1
    }

    private companion object {
        const val ORIGINAL_INDEX = 6
        const val FADED_INDEX = 2
    }
}
