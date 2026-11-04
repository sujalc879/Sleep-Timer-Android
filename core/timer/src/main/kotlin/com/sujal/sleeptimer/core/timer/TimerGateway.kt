package com.sujal.sleeptimer.core.timer

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.sujal.sleeptimer.core.model.Diagnostics
import com.sujal.sleeptimer.core.model.RuntimeCapabilities
import com.sujal.sleeptimer.core.model.StartSource
import com.sujal.sleeptimer.core.model.TimerCommand
import com.sujal.sleeptimer.core.model.TimerLimits
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Singleton
class TimerGateway @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: TimerEngine,
    private val capabilities: RuntimeCapabilities,
    private val notifications: TimerNotifications,
    private val diagnostics: Diagnostics,
    @TimerAppScope private val scope: CoroutineScope,
) {
    fun startFromVisibleActivity(durationMillis: Long, commandId: String = UUID.randomUUID().toString()): Boolean {
        if (!capabilities.activityVisible ||
            durationMillis !in TimerLimits.MINUTE..TimerLimits.MAX_DURATION
        ) {
            return false
        }
        val command = TimerCommand.Start(durationMillis, StartSource.ACTIVITY, true, commandId)
        val intent = TimerServiceIntents.base(context, TimerServiceIntents.START)
            .putExtra(TimerServiceIntents.DURATION, durationMillis)
            .putExtra(TimerServiceIntents.SOURCE, StartSource.ACTIVITY.name)
            .putExtra(TimerServiceIntents.WHILE_IN_USE, true)
            .putExtra(TimerServiceIntents.COMMAND_ID, commandId)
        return deliver(intent, command)
    }

    fun testFromVisibleActivity(commandId: String = UUID.randomUUID().toString()): Boolean {
        if (!capabilities.activityVisible) return false
        val command = TimerCommand.StartTest(true, commandId)
        val intent = TimerServiceIntents.base(context, TimerServiceIntents.TEST)
            .putExtra(TimerServiceIntents.WHILE_IN_USE, true)
            .putExtra(TimerServiceIntents.COMMAND_ID, commandId)
        return deliver(intent, command)
    }

    fun ensureForeground(): Boolean = startService(TimerServiceIntents.base(context, TimerServiceIntents.RECONCILE))

    private fun deliver(intent: Intent, fallback: TimerCommand): Boolean {
        if (startService(intent)) return true
        scope.launch {
            engine.send(TimerCommand.Reconcile("foreground-start-denied", executionAllowed = false))
            engine.send(fallback)
            notifications.interrupted()
        }
        return false
    }

    private fun startService(intent: Intent): Boolean = try {
        ContextCompat.startForegroundService(context, intent)
        true
    } catch (_: IllegalStateException) {
        diagnostics.record("foreground-start-denied")
        false
    } catch (_: SecurityException) {
        diagnostics.record("foreground-start-permission-denied")
        false
    }
}
