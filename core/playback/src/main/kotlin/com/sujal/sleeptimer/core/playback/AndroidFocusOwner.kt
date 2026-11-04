package com.sujal.sleeptimer.core.playback

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.sujal.sleeptimer.core.model.Diagnostics
import com.sujal.sleeptimer.core.model.FocusResult

// OEM audio binder failures must fail closed rather than crash the timer.
@Suppress("TooGenericExceptionCaught", "ReturnCount")
internal class AndroidFocusOwner(private val audio: AudioManager?, private val diagnostics: Diagnostics) {
    private var owner: String? = null
    private var request: AudioFocusRequest? = null
    private var result = FocusResult.FAILED

    @Synchronized
    fun request(ownerId: String): FocusResult {
        if (owner == ownerId && request != null) return result
        owner?.let(::abandon)
        val manager = audio ?: return FocusResult.FAILED
        owner = ownerId
        val listener = AudioManager.OnAudioFocusChangeListener { change ->
            synchronized(this) {
                if (owner == ownerId) {
                    result = if (change == AudioManager.AUDIOFOCUS_GAIN) FocusResult.GRANTED else FocusResult.FAILED
                }
            }
        }
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(MEDIA_ATTRIBUTES)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(listener, Handler(Looper.getMainLooper()))
            .build()
        request = focusRequest
        result = try {
            when (manager.requestAudioFocus(focusRequest)) {
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> FocusResult.GRANTED
                AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> FocusResult.DELAYED
                else -> FocusResult.FAILED
            }
        } catch (error: RuntimeException) {
            diagnostics.record("audio_focus_rejected_${error.javaClass.simpleName}")
            FocusResult.FAILED
        }
        return result
    }

    @Synchronized
    fun result(ownerId: String): FocusResult = if (owner == ownerId) result else FocusResult.FAILED

    @Synchronized
    fun abandon(ownerId: String) {
        if (owner != ownerId) return
        val previous = request
        owner = null
        request = null
        result = FocusResult.FAILED
        if (previous != null) {
            try {
                audio?.abandonAudioFocusRequest(previous)
            } catch (_: RuntimeException) {
                diagnostics.record("audio_focus_abandon_rejected")
            }
        }
    }

    companion object {
        val MEDIA_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }
}
