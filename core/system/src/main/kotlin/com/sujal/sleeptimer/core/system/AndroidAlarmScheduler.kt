package com.sujal.sleeptimer.core.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.sujal.sleeptimer.core.model.AlarmKind
import com.sujal.sleeptimer.core.model.AlarmPlan
import com.sujal.sleeptimer.core.model.AlarmScheduler
import com.sujal.sleeptimer.core.model.Diagnostics
import com.sujal.sleeptimer.core.model.TimerCommand
import com.sujal.sleeptimer.core.model.TimerLimits

object TimerAlarmIntents {
    const val ACTION = "com.sujal.sleeptimer.action.TIMER_ALARM"
    const val EXTRA_RUN_ID = "run_id"
    const val EXTRA_GENERATION = "generation"
    const val EXTRA_KIND = "kind"

    fun create(receiver: ComponentName, runId: String, generation: Long, kind: AlarmKind): Intent = Intent(ACTION)
        .setComponent(receiver)
        .setPackage(receiver.packageName)
        .setData(identity(runId, generation, kind))
        .putExtra(EXTRA_RUN_ID, runId)
        .putExtra(EXTRA_GENERATION, generation)
        .putExtra(EXTRA_KIND, kind.name)

    fun parse(intent: Intent): TimerCommand.AlarmFired? {
        if (intent.action != ACTION || intent.component == null) return null
        return try {
            val runId = intent.getStringExtra(EXTRA_RUN_ID)
            val generation = intent.getLongExtra(EXTRA_GENERATION, -1)
            val kind = intent.getStringExtra(EXTRA_KIND)?.let { AlarmKind.valueOf(it) }
            if (runId == null || kind == null) null else validatedCommand(intent, runId, generation, kind)
        } catch (_: RuntimeException) {
            // Reject malformed parcelables/types and unknown enum names at the component boundary.
            null
        }
    }

    private fun validatedCommand(
        intent: Intent,
        runId: String,
        generation: Long,
        kind: AlarmKind,
    ): TimerCommand.AlarmFired? = if (validIdentity(runId, generation) &&
        intent.data == identity(runId, generation, kind)
    ) {
        TimerCommand.AlarmFired(runId, generation, kind)
    } else {
        null
    }

    private fun validIdentity(runId: String, generation: Long): Boolean =
        runId.isNotBlank() && runId.length <= MAXIMUM_RUN_ID_LENGTH && generation >= 0

    private fun identity(runId: String, generation: Long, kind: AlarmKind): Uri =
        Uri.Builder().scheme("sleeptimer").authority("alarm")
            .appendPath(runId).appendPath(generation.toString()).appendPath(kind.name).build()

    private const val MAXIMUM_RUN_ID_LENGTH = 128
}

class AndroidAlarmScheduler(
    context: Context,
    private val receiver: ComponentName,
    private val diagnostics: Diagnostics,
) : AlarmScheduler {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(AlarmManager::class.java)

    override fun canScheduleExactAlarms(): Boolean = try {
        manager != null && (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms())
    } catch (_: SecurityException) {
        false
    }

    override fun schedule(plan: AlarmPlan): Boolean {
        if (!isValid(plan) || !canScheduleExactAlarms()) return false
        return manager?.let { alarmManager ->
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    plan.finalWindowElapsed,
                    operation(plan.runId, plan.generation, AlarmKind.FINAL_WINDOW),
                )
                val backupElapsed = if (plan.endElapsed > Long.MAX_VALUE - BACKUP_DELAY) {
                    Long.MAX_VALUE
                } else {
                    plan.endElapsed + BACKUP_DELAY
                }
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    backupElapsed,
                    operation(plan.runId, plan.generation, AlarmKind.BACKUP),
                )
                diagnostics.record("alarms_scheduled")
                true
            } catch (_: SecurityException) {
                cancel(plan.runId, plan.generation)
                diagnostics.record("exact_alarm_access_lost")
                false
            } catch (_: IllegalStateException) {
                cancel(plan.runId, plan.generation)
                diagnostics.record("alarm_schedule_rejected")
                false
            }
        } ?: false
    }

    override fun cancel(runId: String, generation: Long) {
        AlarmKind.entries.forEach { kind ->
            val pending = PendingIntent.getBroadcast(
                appContext,
                0,
                TimerAlarmIntents.create(receiver, runId, generation, kind),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pending != null) {
                manager?.cancel(pending)
                pending.cancel()
            }
        }
    }

    private fun operation(runId: String, generation: Long, kind: AlarmKind): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        0,
        TimerAlarmIntents.create(receiver, runId, generation, kind),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun isValid(plan: AlarmPlan): Boolean =
        plan.runId.isNotBlank() && plan.runId.length <= MAXIMUM_RUN_ID_LENGTH && plan.generation >= 0 &&
            plan.finalWindowElapsed >= 0 && plan.endElapsed >= plan.finalWindowElapsed

    private companion object {
        const val BACKUP_DELAY = 30 * TimerLimits.SECOND
        const val MAXIMUM_RUN_ID_LENGTH = 128
    }
}
