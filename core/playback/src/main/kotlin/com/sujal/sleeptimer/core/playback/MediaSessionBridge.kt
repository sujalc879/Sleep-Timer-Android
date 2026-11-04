package com.sujal.sleeptimer.core.playback

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import com.sujal.sleeptimer.core.model.Diagnostics

/** The system binding grants media-session access; notification content is neither read nor stored. */
class MediaSessionBridge : NotificationListenerService()

@Suppress("ReturnCount") // Missing/revoked access is an expected fail-closed boundary.
internal class ActiveMediaSessions(context: Context, private val diagnostics: Diagnostics) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(appContext, MediaSessionBridge::class.java)

    fun snapshot(): SessionSnapshot {
        if (appContext.packageName !in NotificationManagerCompat.getEnabledListenerPackages(appContext)) {
            return SessionSnapshot(emptyList(), observable = false)
        }
        return try {
            val controllers = manager?.getActiveSessions(component) ?: return SessionSnapshot(emptyList(), false)
            val remotePlaying = controllers.any { controller ->
                controller.playbackInfo?.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE &&
                    controller.playbackState?.state in PLAYING_STATES
            }
            SessionSnapshot(controllers, observable = true, remotePlaying = remotePlaying)
        } catch (_: RuntimeException) {
            diagnostics.record("media_session_access_unavailable")
            SessionSnapshot(emptyList(), observable = false)
        }
    }

    fun pause(): Boolean {
        var dispatched = false
        snapshot().controllers.forEach { controller ->
            try {
                if (controller.playbackState?.state in PLAYING_STATES) {
                    controller.transportControls.pause()
                    dispatched = true
                }
            } catch (_: RuntimeException) {
                // A session can disappear between enumeration and binder dispatch.
                diagnostics.record("media_session_pause_unavailable")
            }
        }
        return dispatched
    }

    companion object {
        val PLAYING_STATES = setOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING)
    }
}

internal data class SessionSnapshot(
    val controllers: List<MediaController>,
    val observable: Boolean,
    val remotePlaying: Boolean = false,
)
