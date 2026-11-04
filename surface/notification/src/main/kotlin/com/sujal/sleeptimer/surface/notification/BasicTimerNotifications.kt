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

/** Phase 1's ordinary notification; promoted Live Updates arrive with the surfaces phase. */
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
    private val settings = settingsStore.data.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    @Volatile private var dismissedRun: String? = null

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
            },
        )
    }

    override fun foreground(state: TimerUiState): Notification = buildRunning(state)

    override fun refresh(state: TimerUiState, stateChanged: Boolean) {
        if (stateChanged) dismissedRun = null
        if (dismissedRun != state.snapshot.run?.id) post(TimerNotificationIds.RUNNING, buildRunning(state))
    }

    override fun dismissed(runId: String, revision: Long) {
        dismissedRun = runId
        diagnostics.record("timer-notification-dismissed")
    }

    override fun completed(result: StopResult) {
        if (result.status == StopStatus.CANCELLED || result.status == StopStatus.DEFERRED) return
        if (!result.isTest && result.status in setOf(StopStatus.STOPPED, StopStatus.NOTHING_PLAYING)) return
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
        val notification = base(RESULT_CHANNEL)
            .setContentTitle(context.getString(title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        post(TimerNotificationIds.RESULT, notification)
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
        val fraction = remaining.toDouble() / run.totalDurationMillis.coerceAtLeast(1)
        val progress = (fraction * PROGRESS_MAX).toInt().coerceIn(0, PROGRESS_MAX)
        val extend = settings.value.extendMillis
        return builder.setContentTitle(title).setContentText(text)
            .setWhen(endWall).setShowWhen(true).setUsesChronometer(remaining > 0).setChronometerCountDown(true)
            .setProgress(PROGRESS_MAX, progress, false)
            .setDeleteIntent(dismissIntent(run.id, state.snapshot.revision))
            .addAction(
                0,
                context.getString(R.string.extend_action, extend / TimerLimits.MINUTE),
                action(run.id, TimerServiceIntents.EXTEND, extend),
            )
            .addAction(0, context.getString(R.string.stop_action), action(run.id, TimerServiceIntents.CANCEL, 0))
            .build()
    }

    private fun base(channel: String): NotificationCompat.Builder = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_timer_moon).setContentIntent(contentIntent()).setSilent(true)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent().setComponent(activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        FLAGS,
    )

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

    private fun post(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            if (permission != PackageManager.PERMISSION_GRANTED) return
        }
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            diagnostics.record("notification-permission-changed")
        }
    }

    private companion object {
        const val RUNNING_CHANNEL = "timer_running"
        const val RESULT_CHANNEL = "timer_results"
        const val PROGRESS_MAX = 1_000
        const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}
