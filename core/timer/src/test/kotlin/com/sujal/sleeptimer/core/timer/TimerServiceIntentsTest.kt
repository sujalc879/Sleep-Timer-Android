package com.sujal.sleeptimer.core.timer

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.sujal.sleeptimer.core.model.StartSource
import com.sujal.sleeptimer.core.model.TimerCommand
import com.sujal.sleeptimer.core.model.TimerLimits
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35])
class TimerServiceIntentsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun foregroundRestartReconcilesWithoutInventingUserInteraction() {
        val command = TimerServiceIntents.decode(null, "restart") as TimerCommand.Reconcile
        assertThat(command.serviceRestarted).isTrue()
    }

    @Test
    fun ensuringForegroundOnlyAnnouncesExecutionReadiness() {
        val ensure = TimerServiceIntents.base(context, TimerServiceIntents.RECONCILE)
        assertThat(TimerServiceIntents.decode(ensure, "alarm-delivery")).isEqualTo(TimerCommand.ExecutionReady)
    }

    @Test
    fun unknownActionInvalidDurationAndUnknownSourceAreRejected() {
        assertThat(TimerServiceIntents.decode(Intent("unknown"), "delivery")).isNull()
        val start = TimerServiceIntents.base(context, TimerServiceIntents.START)
            .putExtra(TimerServiceIntents.DURATION, 0L).putExtra(TimerServiceIntents.SOURCE, StartSource.ACTIVITY.name)
        assertThat(TimerServiceIntents.decode(start, "delivery")).isNull()
        start.putExtra(TimerServiceIntents.DURATION, TimerLimits.MINUTE).putExtra(TimerServiceIntents.SOURCE, "unknown")
        assertThat(TimerServiceIntents.decode(start, "delivery")).isNull()
    }

    @Test
    fun separateTapsHaveDifferentDeliveryIdsAndPreserveExpectedRun() {
        val cancel = TimerServiceIntents.base(context, TimerServiceIntents.CANCEL)
            .putExtra(TimerServiceIntents.EXPECTED_RUN, "run-1")
        val first = TimerServiceIntents.decode(cancel, "service:1") as TimerCommand.Stop
        val second = TimerServiceIntents.decode(cancel, "service:2") as TimerCommand.Stop
        assertThat(first.commandId).isNotEqualTo(second.commandId)
        assertThat(first.expectedRunId).isEqualTo("run-1")
    }

    @Test
    fun explicitRetryKeepsCommandIdAndInvalidAdjustmentIsRejected() {
        val extend = TimerServiceIntents.base(context, TimerServiceIntents.EXTEND)
            .putExtra(
                TimerServiceIntents.COMMAND_ID,
                "retry-id",
            ).putExtra(TimerServiceIntents.DELTA, TimerLimits.MINUTE)
        assertThat(
            TimerServiceIntents.decode(extend, "delivery-1"),
        ).isEqualTo(TimerServiceIntents.decode(extend, "delivery-2"))
        extend.putExtra(TimerServiceIntents.DELTA, Long.MAX_VALUE)
        assertThat(TimerServiceIntents.decode(extend, "delivery-3")).isNull()
    }
}
