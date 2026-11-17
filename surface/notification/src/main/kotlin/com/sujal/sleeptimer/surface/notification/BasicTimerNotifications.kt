package com.sujal.sleeptimer.surface.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.drawable.IconCompat
import com.sujal.sleeptimer.core.model.AppSettings
import com.sujal.sleeptimer.core.model.Clock
import com.sujal.sleeptimer.core.model.Diagnostics
import com.sujal.sleeptimer.core.model.SettingsStore
import com.sujal.sleeptimer.core.model.StopResult
import com.sujal.sleeptimer.core.model.StopStatus
import com.sujal.sleeptimer.core.model.TimerLimits
import com.sujal.sleeptimer.core.model.TimerPhase
import com.sujal.sleeptimer.core.model.TimerUiState
import com.sujal.sleeptimer.core.timer.TimerNotificationIds
import com.sujal.sleeptimer.core.timer.TimerNotifications
import com.sujal.sleeptimer.core.timer.TimerServiceIntents
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

/** Silent platform countdown with conditional Live Update promotion and ordinary fallback. */
@Suppress("TooManyFunctions") // One notification lifecycle implementation plus its builders.
class BasicTimerNotifications(
    private val context: Context,
    private val clock: Clock,
    private val activity: ComponentName,
    settingsStore: SettingsStore,
    scope: CoroutineScope,
    private val diagnostics: Diagnostics,
) : TimerNotifications {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val storedSettings = settingsStore.data.stateIn(scope, SharingStarted.Eagerly, AppSettings())
    private val actionLock = Any()
    private var cachedActions: RunningActions? = null

    @Volatile private var explicitSettings: AppSettings? = null
    private val preferences: AppSettings get() = explicitSettings ?: storedSettings.value
    override fun settingsChanged(settings: AppSettings) {
        explicitSettings = settings
    }

    private val dismissal = context.getSharedPreferences("notification-dismissal", Context.MODE_PRIVATE)

    @Volatile private var dismissedRun: String? = dismissal.getString("run", null)

    @Volatile private var presentedRevision = -1L

    init {
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    RUNNING_CHANNEL,
                    context.getString(R.string.timer_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
                NotificationChannel(
                    RESULT_CHANNEL,
                    context.getString(R.string.result_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            ).onEach {
                it.setSound(null, null)
                it.enableVibration(false)
                it.setShowBadge(false)
            },
        )
    }

    override fun foreground(state: TimerUiState): Notification = buildRunning(state)

    override fun refresh(state: TimerUiState, stateChanged: Boolean) {
        if (stateChanged) {
            dismissedRun = null
            dismissal.edit { remove("run") }
        }
        if (dismissedRun != state.snapshot.run?.id) post(TimerNotificationIds.RUNNING, buildRunning(state))
    }

    override fun dismissed(runId: String, revision: Long) {
        if (presentedRevision >= 0 && revision != presentedRevision) return
        dismissedRun = runId
        dismissal.edit { putString("run", runId) }
        diagnostics.record("timer-notification-dismissed")
    }

    @Synchronized
    override fun completed(result: StopResult) {
        if (!shouldAlert(result)) return
        val interruption = if (result.status == StopStatus.INTERRUPTED) {
            "${result.completedElapsed}:${result.detail}:${result.isTest}"
        } else {
            null
        }
        if (interruption != null && dismissal.getString("interruption", null) == interruption) return
        if (post(TimerNotificationIds.RESULT, resultNotification(result)) && interruption != null) {
            dismissal.edit { putString("interruption", interruption) }
        }
    }

    private fun shouldAlert(result: StopResult): Boolean = when (result.status) {
        StopStatus.CANCELLED, StopStatus.DEFERRED -> false
        StopStatus.STOPPED, StopStatus.NOTHING_PLAYING -> result.isTest
        else -> true
    }

    private fun resultNotification(result: StopResult): Notification {
        val title = when (result.status) {
            StopStatus.STOPPED -> if (result.localOnly) R.string.local_player_stopped else R.string.player_stopped
            StopStatus.NOTHING_PLAYING -> R.string.nothing_playing
            StopStatus.INTERRUPTED -> R.string.timer_interrupted
            else -> R.string.player_still_playing
        }
        val text = when (result.status) {
            StopStatus.STOPPED -> context.getString(
                R.string.stop_method,
                result.method.name.lowercase().replace('_', ' '),
            )

            StopStatus.NOTHING_PLAYING -> context.getString(R.string.test_no_playback)

            StopStatus.INTERRUPTED -> context.getString(R.string.open_app_to_resume)

            else -> context.getString(R.string.stop_failure_help)
        }
        return base(RESULT_CHANNEL)
            .setContentTitle(context.getString(title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
    }

    override fun interrupted() {
        val notification = base(RESULT_CHANNEL)
            .setContentTitle(context.getString(R.string.timer_interrupted))
            .setContentText(context.getString(R.string.open_app_to_resume))
            .setAutoCancel(true)
            .build()
        post(TimerNotificationIds.RESULT, notification)
    }

    internal fun buildRunning(state: TimerUiState): Notification {
        val run = state.snapshot.run
        val builder = base(RUNNING_CHANNEL).setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (!state.initialized || run == null) {
            return builder.setContentTitle(context.getString(R.string.preparing_timer)).build()
        }
        presentedRevision = state.snapshot.revision
        val remaining = (run.endElapsed - clock.elapsedRealtime()).coerceAtLeast(0)
        val endWall = clock.wallTimeMillis() + remaining
        val end = DateFormat.getTimeFormat(context).format(Date(endWall))
        val title = when (state.snapshot.phase) {
            TimerPhase.DEFERRED -> context.getString(R.string.waiting_for_call)
            TimerPhase.STOPPING -> context.getString(R.string.stopping_playback)
            else -> context.getString(R.string.ends_at, end)
        }
        val minutes = (remaining / TimerLimits.MINUTE).toInt()
        val text = state.degradedReason?.let { context.getString(R.string.timer_needs_attention) }
            ?: if (minutes == 0) {
                context.getString(R.string.less_than_minute)
            } else {
                context.resources.getQuantityString(R.plurals.minutes_left, minutes, minutes)
            }
        val description = if (preferences.fadeMillis > 0 && state.degradedReason == null) {
            context.getString(R.string.remaining_with_fade, text, preferences.fadeMillis / TimerLimits.SECOND)
        } else {
            text
        }
        val fraction = remaining.toDouble() / run.totalDurationMillis.coerceAtLeast(1)
        val progress = (fraction * PROGRESS_MAX).toInt().coerceIn(0, PROGRESS_MAX)
        val extend = preferences.extendMillis
        val actions = runningActions(run.id, extend)
        applyPromotion(builder, progress)
        return builder.setContentTitle(title).setContentText(description)
            .setWhen(endWall).setShowWhen(true).setUsesChronometer(remaining > 0).setChronometerCountDown(true)
            .setProgress(PROGRESS_MAX, progress, false)
            .setDeleteIntent(dismissIntent(run.id, state.snapshot.revision))
            .addAction(
                0,
                context.getString(R.string.extend_action, extend / TimerLimits.MINUTE),
                actions.extendIntent,
            )
            .addAction(0, context.getString(R.string.stop_action), actions.stopIntent)
            .build()
    }

    private fun applyPromotion(builder: NotificationCompat.Builder, progress: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return
        if (Build.VERSION.SDK_INT_FULL < Build.VERSION_CODES_FULL.BAKLAVA_1) return
        try {
            if (manager.canPostPromotedNotifications()) {
                builder.setRequestPromotedOngoing(true).setStyle(
                    NotificationCompat.ProgressStyle().setProgressSegments(
                        listOf(NotificationCompat.ProgressStyle.Segment(PROGRESS_MAX)),
                    )
                        .setProgress(
                            progress,
                        ).setProgressTrackerIcon(IconCompat.createWithResource(context, R.drawable.ic_tracker_dot)),
                )
            }
        } catch (_: RuntimeException) {
            diagnostics.record("notification-promotion-unavailable")
        }
    }

    private fun base(channel: String): NotificationCompat.Builder = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_timer_moon).setContentIntent(contentIntent).setSilent(true)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

    private val contentIntent: PendingIntent by lazy {
        PendingIntent.getActivity(
            context,
            0,
            Intent().setComponent(activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            FLAGS,
        )
    }

    private fun runningActions(runId: String, extendMillis: Long): RunningActions = synchronized(actionLock) {
        cachedActions?.takeIf { it.runId == runId && it.extendMillis == extendMillis } ?: RunningActions(
            runId,
            extendMillis,
            action(runId, TimerServiceIntents.EXTEND, extendMillis),
            action(runId, TimerServiceIntents.CANCEL, 0),
        ).also { cachedActions = it }
    }

    private fun action(runId: String, action: String, delta: Long): PendingIntent {
        val intent = TimerServiceIntents.base(context, action)
            .setData(
                Uri.Builder().scheme("sleeptimer").authority("command").appendPath(runId).appendPath(action).build(),
            )
            .putExtra(TimerServiceIntents.EXPECTED_RUN, runId).putExtra(TimerServiceIntents.DELTA, delta)
        // Delivery IDs are allocated by the service for each tap, rather than reusing a static ID.
        return PendingIntent.getForegroundService(context, 0, intent, FLAGS)
    }

    private fun dismissIntent(runId: String, revision: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, NotificationDismissReceiver::class.java)
            .setData(Uri.Builder().scheme("sleeptimer").authority("dismiss").appendPath(runId).build())
            .putExtra(TimerServiceIntents.EXPECTED_RUN, runId).putExtra("revision", revision),
        FLAGS,
    )

    private fun post(id: Int, notification: Notification): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            if (permission != PackageManager.PERMISSION_GRANTED) return false
        }
        return try {
            manager.notify(id, notification)
            true
        } catch (_: SecurityException) {
            diagnostics.record("notification-permission-changed")
            false
        } catch (_: RuntimeException) {
            diagnostics.record("notification-post-unavailable")
            false
        }
    }

    private data class RunningActions(
        val runId: String,
        val extendMillis: Long,
        val extendIntent: PendingIntent,
        val stopIntent: PendingIntent,
    )

    private companion object {
        const val RUNNING_CHANNEL = "timer_running"
        const val RESULT_CHANNEL = "timer_results"
        const val PROGRESS_MAX = 1_000
        const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}
