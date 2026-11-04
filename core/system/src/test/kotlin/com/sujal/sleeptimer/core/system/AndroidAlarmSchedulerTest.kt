package com.sujal.sleeptimer.core.system

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.sujal.sleeptimer.core.model.AlarmKind
import com.sujal.sleeptimer.core.model.AlarmPlan
import com.sujal.sleeptimer.core.model.Diagnostics
import com.sujal.sleeptimer.core.model.TimerCommand
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = Application::class)
class AndroidAlarmSchedulerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val receiver = ComponentName(context.packageName, "example.TimerAlarmReceiver")
    private val alarms = context.getSystemService(AlarmManager::class.java)!!
    private val scheduler = AndroidAlarmScheduler(context, receiver, Diagnostics { })

    @Test
    fun finalWindowAndBestEffortBackupAreExactElapsedWakeups() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        assertThat(scheduler.schedule(AlarmPlan("first", 4, 10_000, 60_000))).isTrue()

        val scheduled = shadowOf(alarms).scheduledAlarms
        assertThat(scheduled).hasSize(2)
        assertThat(scheduled.map { it.triggerAtMs }).containsExactly(10_000L, 90_000L)
        scheduled.forEach { alarm ->
            assertThat(alarm.type).isEqualTo(AlarmManager.ELAPSED_REALTIME_WAKEUP)
            assertThat(alarm.windowLengthMs).isEqualTo(ShadowAlarmManager.WINDOW_EXACT)
            assertThat(alarm.isAllowWhileIdle).isTrue()
            val pending = shadowOf(alarm.operation)
            assertThat(pending.isImmutable).isTrue()
            assertThat(pending.savedIntent.component).isEqualTo(receiver)
        }
    }

    @Test
    fun generationsDoNotOverwriteEachOtherAndCancellationIsScoped() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        scheduler.schedule(AlarmPlan("first", 1, 10_000, 60_000))
        scheduler.schedule(AlarmPlan("first", 2, 20_000, 120_000))
        assertThat(shadowOf(alarms).scheduledAlarms).hasSize(4)

        scheduler.cancel("first", 1)

        val remaining = shadowOf(alarms).scheduledAlarms
        assertThat(remaining).hasSize(2)
        assertThat(remaining.map { TimerAlarmIntents.parse(shadowOf(it.operation).savedIntent)?.generation })
            .containsExactly(2L, 2L)
    }

    @Test
    @Config(sdk = [35])
    fun deniedExactAccessReturnsFailureWithoutScheduling() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        assertThat(scheduler.canScheduleExactAlarms()).isFalse()
        assertThat(scheduler.schedule(AlarmPlan("first", 1, 10_000, 60_000))).isFalse()
        assertThat(shadowOf(alarms).scheduledAlarms).isEmpty()
    }

    @Test
    @Config(sdk = [35], shadows = [RevokedDuringSchedule::class])
    fun permissionRevocationRaceIsReportedInsteadOfCrashing() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)

        assertThat(scheduler.schedule(AlarmPlan("first", 1, 10_000, 60_000))).isFalse()
        assertThat(shadowOf(alarms).scheduledAlarms).isEmpty()
    }

    @Test
    fun validatedAlarmIdentityRoundTripsAndRejectsTamperedExtras() {
        val intent = TimerAlarmIntents.create(receiver, "first", 2, AlarmKind.FINAL_WINDOW)
        assertThat(TimerAlarmIntents.parse(intent))
            .isEqualTo(TimerCommand.AlarmFired("first", 2, AlarmKind.FINAL_WINDOW))

        intent.putExtra(TimerAlarmIntents.EXTRA_GENERATION, 3L)
        assertThat(TimerAlarmIntents.parse(intent)).isNull()
        assertThat(TimerAlarmIntents.parse(Intent())).isNull()
    }

    @Implements(AlarmManager::class)
    class RevokedDuringSchedule : ShadowAlarmManager() {
        @Implementation
        public override fun setExactAndAllowWhileIdle(type: Int, triggerAtTime: Long, operation: PendingIntent): Unit =
            throw SecurityException("Exact access revoked")
    }
}
