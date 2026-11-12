package com.sujal.sleeptimer.surface.notification

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.sujal.sleeptimer.core.model.AppSettings
import com.sujal.sleeptimer.core.model.Clock
import com.sujal.sleeptimer.core.model.Diagnostics
import com.sujal.sleeptimer.core.model.SettingsStore
import com.sujal.sleeptimer.core.model.StartSource
import com.sujal.sleeptimer.core.model.StopResult
import com.sujal.sleeptimer.core.model.StopStatus
import com.sujal.sleeptimer.core.model.TimerLimits
import com.sujal.sleeptimer.core.model.TimerPhase
import com.sujal.sleeptimer.core.model.TimerRun
import com.sujal.sleeptimer.core.model.TimerSnapshot
import com.sujal.sleeptimer.core.model.TimerUiState
import com.sujal.sleeptimer.core.timer.TimerServiceIntents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class BasicTimerNotificationsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = object : Clock {
        override fun elapsedRealtime(): Long = TimerLimits.MINUTE
        override fun wallTimeMillis(): Long = 0
        override fun bootCount(): Int = 1
    }
    private val store = object : SettingsStore {
        override val data = MutableStateFlow(AppSettings())
        override suspend fun update(transform: (AppSettings) -> AppSettings): AppSettings = transform(data.value).also {
            data.value =
                it
        }
    }

    @Test fun recoveredInterruptionAlertsOnceAcrossControllerRecreation() = runTest {
        fun factory() = BasicTimerNotifications(
            context,
            clock,
            ComponentName(context, "MainActivity"),
            store,
            backgroundScope,
            Diagnostics {},
        )
        val result = StopResult(StopStatus.INTERRUPTED, completedElapsed = 123)
        val manager = context.getSystemService(NotificationManager::class.java)
        factory().completed(result)
        assertThat(manager.activeNotifications.single().notification.extras.getString(Notification.EXTRA_TITLE))
            .isEqualTo("Your timer was interrupted")
        manager.cancelAll()
        val recreated = factory()
        recreated.completed(result)
        assertThat(manager.activeNotifications).isEmpty()
        recreated.completed(result.copy(completedElapsed = 124))
        assertThat(manager.activeNotifications).hasLength(1)
    }

    @Test fun dismissalSurvivesControllerRecreationAndOldRevisionsCannotHideANewerNotification() = runTest {
        fun factory() = BasicTimerNotifications(
            context,
            clock,
            ComponentName(context, "MainActivity"),
            store,
            backgroundScope,
            Diagnostics {
            },
        )
        factory().dismissed("run-1", 0)
        val recreated = factory()
        val manager = context.getSystemService(NotificationManager::class.java)
        recreated.refresh(running(), stateChanged = false)
        assertThat(manager.activeNotifications).isEmpty()
        val newer = running().copy(snapshot = running().snapshot.copy(revision = 4))
        recreated.refresh(newer, stateChanged = true)
        recreated.dismissed("run-1", 3)
        recreated.refresh(newer, stateChanged = false)
        assertThat(manager.activeNotifications).hasLength(1)
    }

    @Test fun configuredFadeAndExtendAreReflectedByTheOrdinaryFallback() = runTest {
        val factory =
            BasicTimerNotifications(
                context,
                clock,
                ComponentName(context, "MainActivity"),
                store,
                backgroundScope,
                Diagnostics {
                },
            )
        factory.settingsChanged(AppSettings(fadeMillis = 5_000, extendMillis = 20 * TimerLimits.MINUTE))
        val notification = factory.foreground(running())
        assertThat(notification.extras.getString(Notification.EXTRA_TEXT)).isEqualTo("1 min left · fades out over 5 s")
        assertThat(notification.actions[0].title.toString()).isEqualTo("+20 min")
        assertThat(notification.contentView).isNull()
    }

    @Test
    fun actionsAreImmutableExplicitRunBoundAndDoNotReuseCommandIds() = runTest {
        val factory =
            BasicTimerNotifications(
                context,
                clock,
                ComponentName(context, "MainActivity"),
                store,
                backgroundScope,
                Diagnostics {
                },
            )
        val notification = factory.foreground(running())
        val extend = shadowOf(notification.actions[0].actionIntent)
        val stop = shadowOf(notification.actions[1].actionIntent)
        assertThat(extend.savedIntent.component).isNotNull()
        assertThat(extend.flags and PendingIntent.FLAG_IMMUTABLE).isNotEqualTo(0)
        assertThat(extend.savedIntent.getStringExtra(TimerServiceIntents.EXPECTED_RUN)).isEqualTo("run-1")
        assertThat(extend.savedIntent.hasExtra(TimerServiceIntents.COMMAND_ID)).isFalse()
        assertThat(stop.savedIntent.action).isEqualTo(TimerServiceIntents.CANCEL)
        assertThat(notification.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)).isTrue()
    }

    @Test
    fun userDismissalIsRespectedUntilStateChanges() = runTest {
        val factory =
            BasicTimerNotifications(
                context,
                clock,
                ComponentName(context, "MainActivity"),
                store,
                backgroundScope,
                Diagnostics {
                },
            )
        val manager = context.getSystemService(NotificationManager::class.java)
        factory.dismissed("run-1", 0)
        factory.refresh(running(), stateChanged = false)
        assertThat(manager.activeNotifications).isEmpty()
        factory.refresh(running(), stateChanged = true)
        assertThat(manager.activeNotifications).hasLength(1)
    }

    @Test
    fun noPlaybackAndLocalOnlyResultsNeverClaimRemotePlayerSuccess() = runTest {
        val factory =
            BasicTimerNotifications(
                context,
                clock,
                ComponentName(context, "MainActivity"),
                store,
                backgroundScope,
                Diagnostics {
                },
            )
        val manager = context.getSystemService(NotificationManager::class.java)
        factory.completed(StopResult(StopStatus.NOTHING_PLAYING, isTest = true))
        assertThat(
            manager.activeNotifications.single().notification.extras.getString(Notification.EXTRA_TITLE),
        ).isEqualTo("Nothing was playing")
        factory.completed(StopResult(StopStatus.STOPPED, isTest = true, localOnly = true))
        assertThat(
            manager.activeNotifications.single().notification.extras.getString(Notification.EXTRA_TITLE),
        ).isEqualTo("Local playback stopped")
    }

    private fun running(): TimerUiState = TimerUiState(
        initialized = true,
        snapshot = TimerSnapshot(
            phase = TimerPhase.RUNNING,
            run = TimerRun(
                "run-1",
                0,
                2 * TimerLimits.MINUTE,
                2 * TimerLimits.MINUTE,
                StartSource.ACTIVITY,
                true,
            ),
        ),
    )
}
