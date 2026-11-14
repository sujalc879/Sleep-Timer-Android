package com.sujal.sleeptimer.core.timer

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.sujal.sleeptimer.core.model.Diagnostics
import com.sujal.sleeptimer.core.model.RuntimeCapabilities
import com.sujal.sleeptimer.core.model.StartSource
import com.sujal.sleeptimer.core.model.TimerCommand
import com.sujal.sleeptimer.core.model.TimerLimits
import com.sujal.sleeptimer.core.model.isActive
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TimerService : Service() {
    @Inject lateinit var engine: TimerEngine

    @Inject lateinit var notifications: TimerNotifications

    @Inject lateinit var capabilities: RuntimeCapabilities

    @Inject lateinit var diagnostics: Diagnostics

    @Inject @TimerAppScope
    lateinit var appScope: CoroutineScope

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pending = AtomicInteger()
    private val instanceId = UUID.randomUUID().toString()

    @Inject lateinit var settingsStore: com.sujal.sleeptimer.core.model.SettingsStore

    @Inject lateinit var clock: com.sujal.sleeptimer.core.model.Clock

    @Inject lateinit var wakes: com.sujal.sleeptimer.core.model.WakeLocks

    @Inject lateinit var feedback: com.sujal.sleeptimer.core.model.TimerFeedback
    private lateinit var sensors: com.sujal.sleeptimer.core.system.TimerSensorController
    private var preferences = com.sujal.sleeptimer.core.model.AppSettings()
    private var receivedCommand = false
    private var lastPresentation = ""
    private var reportedResult: Any? = null

    override fun onCreate() {
        super.onCreate()
        capabilities.foregroundServiceHasWhileInUse = false
        reportedResult = engine.state.value.snapshot.lastResult
        sensors =
            com.sujal.sleeptimer.core.system.TimerSensorController(this, clock, wakes, capabilities, diagnostics) {
                    id,
                    delta,
                ->
                serviceScope.launch {
                    val result = engine.send(TimerCommand.Extend(delta, UUID.randomUUID().toString(), id))
                    if (result.accepted) feedback.warning(com.sujal.sleeptimer.core.model.WarningMode.SOFT)
                }
            }
        serviceScope.launch {
            settingsStore.data.collect {
                val changed = preferences.fadeMillis != it.fadeMillis || preferences.extendMillis != it.extendMillis
                preferences = it
                notifications.settingsChanged(it)
                updateSensors()
                if (changed && capabilities.foregroundServiceActive && engine.state.value.snapshot.isActive) {
                    notifications.refresh(engine.state.value, stateChanged = true)
                }
            }
        }
        serviceScope.launch {
            while (isActive) {
                updateSensors()
                delay(TimerLimits.SECOND)
            }
        }
        observeState()
        serviceScope.launch {
            while (isActive) {
                delay(TimerLimits.MINUTE)
                if (capabilities.foregroundServiceActive && engine.state.value.snapshot.isActive) {
                    notifications.refresh(engine.state.value, stateChanged = false)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        receivedCommand = true
        pending.incrementAndGet()
        if (!promote()) {
            pending.decrementAndGet()
            appScope.launch(start = CoroutineStart.UNDISPATCHED) {
                engine.send(TimerCommand.Reconcile("promotion-denied", executionAllowed = false))
            }
            stopSelf()
            return START_NOT_STICKY
        }
        val command = TimerServiceIntents.decode(intent, "$instanceId:$startId")
        markWhileInUse(command)
        serviceScope.launch {
            try {
                engine.send(TimerCommand.ExecutionReady)
                if (command != null) engine.send(command) else diagnostics.record("invalid-service-command")
            } finally {
                pending.decrementAndGet()
                finishIfIdle()
            }
        }
        return if (command == null) START_NOT_STICKY else START_STICKY
    }

    private fun promote(): Boolean = try {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            TimerNotificationIds.RUNNING,
            notifications.foreground(engine.state.value),
            type,
        )
        capabilities.foregroundServiceActive = true
        true
    } catch (_: RuntimeException) {
        diagnostics.record("foreground-promotion-failed")
        capabilities.foregroundServiceActive = false
        capabilities.foregroundServiceHasWhileInUse = false
        notifications.interrupted()
        false
    }

    private fun markWhileInUse(command: TimerCommand?) {
        val fromVisibleStart =
            command is TimerCommand.Start &&
                command.source in
                setOf(StartSource.ACTIVITY, StartSource.SHORTCUT, StartSource.WIDGET, StartSource.TILE) &&
                command.hasWhileInUse
        val fromVisibleTest = command is TimerCommand.StartTest && command.hasWhileInUse
        if (capabilities.activityVisible || fromVisibleStart ||
            fromVisibleTest
        ) {
            capabilities.foregroundServiceHasWhileInUse = true
        }
    }

    private fun updateSensors() {
        val state = engine.state.value.snapshot
        val run = state.run.takeIf {
            state.phase == com.sujal.sleeptimer.core.model.TimerPhase.RUNNING ||
                state.phase == com.sujal.sleeptimer.core.model.TimerPhase.FADING
        }
        sensors.update(run, preferences)
    }

    private fun observeState() {
        serviceScope.launch {
            engine.state.collect { state ->
                if (!state.initialized || !capabilities.foregroundServiceActive) return@collect
                updateSensors()
                val snapshot = state.snapshot
                val run = snapshot.run
                val presentation = "${run?.id}:${snapshot.phase}:${run?.endElapsed}:${state.degradedReason}:" +
                    "${preferences.fadeMillis}:${preferences.extendMillis}"
                if (snapshot.isActive && presentation != lastPresentation) {
                    lastPresentation = presentation
                    notifications.refresh(state, stateChanged = true)
                }
                snapshot.lastResult?.let { result ->
                    if (result != reportedResult) {
                        reportedResult = result
                        notifications.completed(result)
                    }
                }
                finishIfIdle()
            }
        }
    }

    private fun finishIfIdle() {
        val state = engine.state.value
        val readyToFinish = receivedCommand && pending.get() == 0 && state.initialized
        if (readyToFinish && !state.snapshot.isActive) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        capabilities.foregroundServiceActive = false
        capabilities.foregroundServiceHasWhileInUse = false
        sensors.close()
        serviceScope.cancel()
        // Enqueue before a replacement service can promote on the main thread.
        appScope.launch(start = CoroutineStart.UNDISPATCHED) {
            engine.send(TimerCommand.Reconcile("service-destroyed", executionAllowed = false))
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
