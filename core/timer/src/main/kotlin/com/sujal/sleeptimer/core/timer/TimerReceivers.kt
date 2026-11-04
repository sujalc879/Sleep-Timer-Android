package com.sujal.sleeptimer.core.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sujal.sleeptimer.core.model.Diagnostics
import com.sujal.sleeptimer.core.model.RuntimeCapabilities
import com.sujal.sleeptimer.core.model.TimerCommand
import com.sujal.sleeptimer.core.model.WakeLocks
import com.sujal.sleeptimer.core.model.isActive
import com.sujal.sleeptimer.core.system.TimerAlarmIntents
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@AndroidEntryPoint
class TimerAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var engine: TimerEngine

    @Inject lateinit var gateway: TimerGateway

    @Inject lateinit var wakeLocks: WakeLocks

    @Inject lateinit var diagnostics: Diagnostics

    @Inject @TimerAppScope
    lateinit var scope: CoroutineScope

    @Suppress("TooGenericExceptionCaught") // Always finish goAsync, including storage/OEM failures.
    override fun onReceive(context: Context, intent: Intent) {
        val command = TimerAlarmIntents.parse(intent) ?: return
        val pending = goAsync()
        val lease = try {
            wakeLocks.acquire("alarm-handoff", HANDOFF_TIMEOUT)
        } catch (_: RuntimeException) {
            diagnostics.record("alarm-handoff-wake-failed")
            pending.finish()
            return
        }
        scope.launch {
            try {
                withTimeout(RECEIVER_TIMEOUT) {
                    if (!gateway.ensureForeground()) {
                        engine.send(TimerCommand.Reconcile("alarm-foreground-denied", executionAllowed = false))
                    }
                    engine.send(command)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                diagnostics.record("alarm-handoff-failed-${error.javaClass.simpleName}")
            } finally {
                lease.release()
                pending.finish()
            }
        }
    }

    private companion object {
        const val HANDOFF_TIMEOUT = 15_000L
        const val RECEIVER_TIMEOUT = 8_000L
    }
}

@AndroidEntryPoint
class TimerSystemReceiver : BroadcastReceiver() {
    @Inject lateinit var engine: TimerEngine

    @Inject lateinit var gateway: TimerGateway

    @Inject lateinit var capabilities: RuntimeCapabilities

    @Inject lateinit var diagnostics: Diagnostics

    @Inject @TimerAppScope
    lateinit var scope: CoroutineScope

    @Suppress("TooGenericExceptionCaught") // Always finish goAsync, including storage/OEM failures.
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action?.takeIf { it in ACTIONS } ?: return
        val pending = goAsync()
        scope.launch {
            try {
                withTimeout(RECEIVER_TIMEOUT) {
                    engine.send(TimerCommand.Reconcile(action, executionAllowed = capabilities.foregroundServiceActive))
                    if (engine.state.value.snapshot.isActive) gateway.ensureForeground()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                diagnostics.record("system-reconcile-failed-${error.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val RECEIVER_TIMEOUT = 8_000L
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
