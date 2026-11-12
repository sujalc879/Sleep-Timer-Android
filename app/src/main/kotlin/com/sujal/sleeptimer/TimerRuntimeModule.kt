package com.sujal.sleeptimer

import android.content.ComponentName
import android.content.Context
import com.sujal.sleeptimer.core.data.JsonSettingsStore
import com.sujal.sleeptimer.core.data.JsonSnapshotStore
import com.sujal.sleeptimer.core.model.AlarmScheduler
import com.sujal.sleeptimer.core.model.Clock
import com.sujal.sleeptimer.core.model.Diagnostics
import com.sujal.sleeptimer.core.model.PlaybackBackend
import com.sujal.sleeptimer.core.model.PlaybackController
import com.sujal.sleeptimer.core.model.RuntimeCapabilities
import com.sujal.sleeptimer.core.model.SettingsStore
import com.sujal.sleeptimer.core.model.SnapshotStore
import com.sujal.sleeptimer.core.model.TimerFeedback
import com.sujal.sleeptimer.core.model.WakeLocks
import com.sujal.sleeptimer.core.playback.AndroidPlaybackBackend
import com.sujal.sleeptimer.core.playback.PlaybackCoordinator
import com.sujal.sleeptimer.core.system.AndroidAlarmScheduler
import com.sujal.sleeptimer.core.system.AndroidClock
import com.sujal.sleeptimer.core.system.AndroidRecoveryInspector
import com.sujal.sleeptimer.core.system.AndroidTimerFeedback
import com.sujal.sleeptimer.core.system.AndroidWakeLocks
import com.sujal.sleeptimer.core.timer.TimerAlarmReceiver
import com.sujal.sleeptimer.core.timer.TimerAppScope
import com.sujal.sleeptimer.core.timer.TimerDependencies
import com.sujal.sleeptimer.core.timer.TimerEngine
import com.sujal.sleeptimer.core.timer.TimerNotifications
import com.sujal.sleeptimer.surface.notification.BasicTimerNotifications
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object TimerRuntimeModule {
    @Provides @Singleton @TimerAppScope
    fun appScope(diagnostics: Diagnostics): CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
            diagnostics.record("runtime-failure-${error.javaClass.simpleName}")
        },
    )

    @Provides @Singleton
    fun capabilities(): RuntimeCapabilities = RuntimeCapabilities()

    @Provides @Singleton
    fun clock(@ApplicationContext context: Context): Clock = AndroidClock(context)

    @Provides @Singleton
    fun settings(
        @ApplicationContext context: Context,
        diagnostics: Diagnostics,
        @TimerAppScope scope: CoroutineScope,
    ): SettingsStore = JsonSettingsStore(context, diagnostics, scope)

    @Provides @Singleton
    fun snapshots(
        @ApplicationContext context: Context,
        diagnostics: Diagnostics,
        @TimerAppScope scope: CoroutineScope,
    ): SnapshotStore = JsonSnapshotStore(context, diagnostics, scope)

    @Provides @Singleton
    fun alarms(@ApplicationContext context: Context, diagnostics: Diagnostics): AlarmScheduler =
        AndroidAlarmScheduler(context, ComponentName(context, TimerAlarmReceiver::class.java), diagnostics)

    @Provides @Singleton
    fun wakes(@ApplicationContext context: Context, diagnostics: Diagnostics): WakeLocks =
        AndroidWakeLocks(context, diagnostics)

    @Provides @Singleton
    fun feedback(@ApplicationContext context: Context, diagnostics: Diagnostics): TimerFeedback =
        AndroidTimerFeedback(context, diagnostics)
}

@Module
@InstallIn(SingletonComponent::class)
object PlaybackRuntimeModule {
    @Provides @Singleton
    fun backend(
        @ApplicationContext context: Context,
        capabilities: RuntimeCapabilities,
        clock: Clock,
        diagnostics: Diagnostics,
    ): PlaybackBackend = AndroidPlaybackBackend(context, capabilities, clock, diagnostics)

    @Provides @Singleton
    fun playback(backend: PlaybackBackend, clock: Clock, diagnostics: Diagnostics): PlaybackController =
        PlaybackCoordinator(backend, clock, diagnostics)

    @Provides @Singleton
    @Suppress("LongParameterList") // Dagger provides each explicit port; the engine receives one bundle.
    fun engine(
        clock: Clock,
        snapshots: SnapshotStore,
        settings: SettingsStore,
        alarms: AlarmScheduler,
        wakes: WakeLocks,
        feedback: TimerFeedback,
        playback: PlaybackController,
        diagnostics: Diagnostics,
        @TimerAppScope scope: CoroutineScope,
        @ApplicationContext context: Context,
    ): TimerEngine = TimerEngine(
        TimerDependencies(
            clock, snapshots, settings, alarms, wakes, feedback, playback, diagnostics,
            AndroidRecoveryInspector(context, diagnostics),
            com.sujal.sleeptimer.core.system.AndroidScreenLocker(context, diagnostics),
        ),
        scope,
        initialExecutionAllowed = false,
    )

    @Provides @Singleton
    fun notifications(
        @ApplicationContext context: Context,
        clock: Clock,
        settings: SettingsStore,
        @TimerAppScope scope: CoroutineScope,
        diagnostics: Diagnostics,
    ): TimerNotifications = BasicTimerNotifications(
        context,
        clock,
        ComponentName(context, MainActivity::class.java),
        settings,
        scope,
        diagnostics,
    )
}
