package com.sujal.sleeptimer.core.playback

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.sujal.sleeptimer.core.model.Clock
import com.sujal.sleeptimer.core.model.Diagnostics
import com.sujal.sleeptimer.core.model.Interruption
import com.sujal.sleeptimer.core.model.OutputRoute
import com.sujal.sleeptimer.core.model.PlaybackObservation
import com.sujal.sleeptimer.core.model.VolumeState

// OEM audio binder failures must fail closed rather than crash the timer.
@Suppress("TooGenericExceptionCaught", "ReturnCount")
internal class AndroidPlaybackProbe(
    private val audio: AudioManager?,
    private val sessions: ActiveMediaSessions,
    private val clock: Clock,
    private val diagnostics: Diagnostics,
) {
    fun observe(): PlaybackObservation {
        val sessionState = sessions.snapshot()
        val manager = audio ?: return PlaybackObservation(
            localPlaying = false,
            remotePlaying = sessionState.remotePlaying,
            interruption = Interruption.UNKNOWN,
            remoteObservable = sessionState.observable,
        )
        return try {
            val playing = manager.isMusicActive
            PlaybackObservation(
                localPlaying = playing,
                remotePlaying = sessionState.remotePlaying,
                interruption = interruption(manager, playing),
                volume = volume(manager),
                remoteObservable = sessionState.observable,
            )
        } catch (error: RuntimeException) {
            diagnostics.record("playback_probe_unavailable_${error.javaClass.simpleName}")
            PlaybackObservation(
                localPlaying = true,
                remotePlaying = sessionState.remotePlaying,
                interruption = Interruption.UNKNOWN,
                remoteObservable = sessionState.observable,
            )
        }
    }

    private fun interruption(manager: AudioManager, musicPlaying: Boolean): Interruption {
        if (manager.mode in CALL_MODES) return Interruption.CALL
        val usages = manager.activePlaybackConfigurations.map { it.audioAttributes.usage }
        return when {
            usages.any { it in ALARM_USAGES } -> Interruption.ALARM
            usages.any { it in CALL_USAGES } -> Interruption.CALL
            musicPlaying && (usages.isEmpty() || AudioAttributes.USAGE_UNKNOWN in usages) -> Interruption.UNKNOWN
            else -> Interruption.NONE
        }
    }

    private fun volume(manager: AudioManager): VolumeState {
        val maximum = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val minimum = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        } else {
            // API 26–27 has no public minimum query; never use a mute operation.
            minOf(1, maximum)
        }
        return VolumeState(
            index = manager.getStreamVolume(AudioManager.STREAM_MUSIC),
            minimum = minimum,
            maximum = maximum,
            fixed = manager.isVolumeFixed,
            route = route(manager),
        )
    }

    private fun route(manager: AudioManager): OutputRoute? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        return try {
            // A connected-device list alone is not the player's route. Accept only a singleton
            // output topology which also agrees with the public media-routing prediction.
            val connected = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { it.isSink }
            val device = connected.singleOrNull() ?: return null
            if (device.type == AudioDeviceInfo.TYPE_UNKNOWN) return null
            val anticipated = manager.getAudioDevicesForAttributes(AndroidFocusOwner.MEDIA_ATTRIBUTES)
            if (anticipated.singleOrNull()?.id != device.id) return null
            OutputRoute("${clock.bootCount()}:${device.id}:${device.type}")
        } catch (_: RuntimeException) {
            null
        }
    }

    private companion object {
        val CALL_MODES =
            setOf(AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION, AudioManager.MODE_RINGTONE)
        val ALARM_USAGES = setOf(AudioAttributes.USAGE_ALARM, AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
        val CALL_USAGES =
            setOf(AudioAttributes.USAGE_VOICE_COMMUNICATION, AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
    }
}
