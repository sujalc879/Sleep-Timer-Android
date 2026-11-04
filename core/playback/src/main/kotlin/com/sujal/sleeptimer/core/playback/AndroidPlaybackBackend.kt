package com.sujal.sleeptimer.core.playback

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.view.KeyEvent
import com.sujal.sleeptimer.core.model.Clock
import com.sujal.sleeptimer.core.model.Diagnostics
import com.sujal.sleeptimer.core.model.FocusResult
import com.sujal.sleeptimer.core.model.PlaybackBackend
import com.sujal.sleeptimer.core.model.PlaybackObservation
import com.sujal.sleeptimer.core.model.RuntimeCapabilities
import com.sujal.sleeptimer.core.model.TimerRun

// OEM audio binder failures must fail closed rather than crash the timer.
@Suppress("TooGenericExceptionCaught", "ReturnCount")
class AndroidPlaybackBackend(
    context: Context,
    private val capabilities: RuntimeCapabilities,
    clock: Clock,
    private val diagnostics: Diagnostics,
) : PlaybackBackend {
    private val appContext = context.applicationContext
    private val audio = appContext.getSystemService(AudioManager::class.java)
    private val power = appContext.getSystemService(PowerManager::class.java)
    private val sessions = ActiveMediaSessions(appContext, diagnostics)
    private val probe = AndroidPlaybackProbe(audio, sessions, clock, diagnostics)
    private val focus = AndroidFocusOwner(audio, diagnostics)

    override fun observe(): PlaybackObservation = probe.observe()

    override fun pauseSessions(): Boolean = sessions.pause()

    override fun requestFocus(ownerId: String): FocusResult =
        if (hasLiveAudioCapability()) focus.request(ownerId) else FocusResult.FAILED

    override fun focusResult(ownerId: String): FocusResult = focus.result(ownerId)

    override fun abandonFocus(ownerId: String) = focus.abandon(ownerId)

    override fun dispatchPause() {
        val manager = audio ?: return
        // KeyEvent timestamps are uptime, independently of the elapsed-realtime countdown.
        val eventTime = SystemClock.uptimeMillis()
        listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP).forEach { action ->
            try {
                manager.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, action, KeyEvent.KEYCODE_MEDIA_PAUSE, 0))
            } catch (error: RuntimeException) {
                diagnostics.record("media_pause_rejected_${error.javaClass.simpleName}")
            }
        }
    }

    override fun setVolume(index: Int) {
        check(hasLiveAudioCapability()) { "No current foreground audio capability" }
        val manager = checkNotNull(audio) { "Audio service unavailable" }
        manager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
    }

    override fun canControlAudio(run: TimerRun): Boolean = hasLiveAudioCapability() &&
        (Build.VERSION.SDK_INT < ANDROID_17 || capabilities.activityVisible || run.hasWhileInUse)

    override fun isInteractive(): Boolean = power?.isInteractive ?: true

    private fun hasLiveAudioCapability(): Boolean = capabilities.activityVisible ||
        (
            capabilities.foregroundServiceActive &&
                (Build.VERSION.SDK_INT < ANDROID_17 || capabilities.foregroundServiceHasWhileInUse)
            )

    private companion object {
        const val ANDROID_17 = 37
    }
}
