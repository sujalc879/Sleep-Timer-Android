package com.sujal.sleeptimer.surface.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sujal.sleeptimer.core.timer.TimerNotifications
import com.sujal.sleeptimer.core.timer.TimerServiceIntents
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NotificationDismissReceiver : BroadcastReceiver() {
    @Inject lateinit var notifications: TimerNotifications

    override fun onReceive(context: Context, intent: Intent) {
        val runId = intent.getStringExtra(TimerServiceIntents.EXPECTED_RUN) ?: return
        notifications.dismissed(runId, intent.getLongExtra("revision", -1))
    }
}
