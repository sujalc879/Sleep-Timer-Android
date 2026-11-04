package com.sujal.sleeptimer.testplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat

/** A real local player with explicit bad-player modes. Never changes global stream volume. */
class TestPlayerService : Service() {
    private lateinit var worker: HandlerThread
    private lateinit var handler: Handler
    private lateinit var audio: AudioManager
    private lateinit var session: MediaSession
    private lateinit var focus: AudioFocusRequest
    private var player: MediaPlayer? = null
    private var mode = PlayerMode.GOOD
    private var silent = true
    private var resumedOnce = false

    @Volatile private var destroyed = false

    override fun onCreate() {
        super.onCreate()
        worker = HandlerThread("test-player").apply { start() }
        handler = Handler(worker.looper)
        audio = getSystemService(AudioManager::class.java)
        session = MediaSession(this, "SleepTimerTestPlayer").apply {
            setCallback(
                object : MediaSession.Callback() {
                    override fun onPause() {
                        pause()
                    }
                    override fun onStop() {
                        pause()
                    }
                    override fun onPlay() {
                        play()
                    }
                },
                handler,
            )
            isActive = true
        }
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(MEDIA_ATTRIBUTES).setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change -> focusChanged(change) }, handler).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)
        val requestedMode = intent?.getStringExtra("mode")?.let { value ->
            PlayerMode.entries.firstOrNull {
                it.name ==
                    value
            }
        }
        val requestedSilent = intent?.getBooleanExtra("silent", true) ?: true
        handler.post {
            if (intent?.action == ACTION_STOP) {
                pause()
                stopSelfResult(startId)
            } else {
                pause()
                mode = requestedMode ?: PlayerMode.GOOD
                silent = requestedSilent
                resumedOnce = false
                if (audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    play()
                } else {
                    Log.d(TAG, "focus-denied")
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun play() {
        if (destroyed) return
        if (player == null) {
            player = MediaPlayer().apply {
                setAudioAttributes(MEDIA_ATTRIBUTES)
                resources.openRawResourceFd(R.raw.test_tone).use { resource ->
                    setDataSource(resource.fileDescriptor, resource.startOffset, resource.length)
                }
                isLooping = true
                prepare()
            }
        }
        // Quiet mode is explicit; the automated path always defaults to silent.
        val gain = if (silent) 0f else QUIET_GAIN
        player?.setVolume(gain, gain)
        player?.start()
        publish(true)
    }

    private fun pause() {
        if (player?.isPlaying == true) player?.pause()
        publish(false)
    }

    private fun focusChanged(change: Int) {
        if (change >= 0 || mode == PlayerMode.IGNORE_FOCUS) return
        pause()
        if (mode == PlayerMode.AUTO_RESUME && !resumedOnce) {
            resumedOnce = true
            handler.postDelayed({ play() }, RESUME_DELAY)
        }
    }

    private fun publish(playing: Boolean) {
        val state = if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_STOP)
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, if (playing) 1f else 0f).build(),
        )
        PlayerState.mutable.value = PlayerStatus(playing, mode, silent)
        Log.d(TAG, "playing=$playing mode=$mode silent=$silent elapsed=${SystemClock.elapsedRealtime()}")
    }

    private fun notification(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                getString(R.string.testplayer_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setSound(null, null)
            },
        )
        val content = PendingIntent.getActivity(
            this,
            0,
            Intent(this, TestPlayerActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(
                getString(R.string.testplayer_name),
            ).setContentText(getString(R.string.player_channel_text))
            .setContentIntent(content).setOngoing(true).build()
    }

    override fun onDestroy() {
        destroyed = true
        session.setCallback(null)
        handler.removeCallbacksAndMessages(null)
        handler.post {
            pause()
            player?.release()
            player = null
            session.release()
            audio.abandonAudioFocusRequest(focus)
            worker.quitSafely()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_PLAY = "com.sujal.sleeptimer.testplayer.PLAY"
        const val ACTION_STOP = "com.sujal.sleeptimer.testplayer.STOP"
        private const val NOTIFICATION_ID = 200
        private const val CHANNEL = "test_player"
        private const val TAG = "SleepTimerPlayer"
        private const val QUIET_GAIN = 0.03f
        private const val RESUME_DELAY = 1_000L
        private val MEDIA_ATTRIBUTES = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    }
}
