package com.sujal.sleeptimer.feature.home

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sujal.sleeptimer.core.model.AppSettings
import com.sujal.sleeptimer.core.model.Clock
import com.sujal.sleeptimer.core.model.PlaybackController
import com.sujal.sleeptimer.core.model.RuntimeCapabilities
import com.sujal.sleeptimer.core.model.SettingsStore
import com.sujal.sleeptimer.core.model.TimerCommand
import com.sujal.sleeptimer.core.model.TimerLimits
import com.sujal.sleeptimer.core.model.TimerPhase
import com.sujal.sleeptimer.core.model.isCancelling
import com.sujal.sleeptimer.core.timer.NightlyController
import com.sujal.sleeptimer.core.timer.TimerEngine
import com.sujal.sleeptimer.core.timer.TimerGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
// Explicit DI ports and UI intents; the ViewModel owns presentation, not runtime effects.
@Suppress("LongParameterList", "TooManyFunctions")
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: TimerEngine,
    private val settings: SettingsStore,
    private val clock: Clock,
    private val gateway: TimerGateway,
    private val playback: PlaybackController,
    private val capabilities: RuntimeCapabilities,
    private val nightly: NightlyController,
) : ViewModel() {
    private val selection = MutableStateFlow<Long?>(null)
    private val permissions = MutableStateFlow(readPermissions(context))
    val messages = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    private val ticks = elapsedDisplayTicks(clock)
    val state = combine(
        engine.state,
        settings.data.catch { error ->
            if (error is IOException) {
                messages.emit(R.string.command_failed)
                emit(AppSettings())
            } else {
                throw error
            }
        },
        ticks,
        selection,
        permissions,
    ) {
            runtime,
            preferences,
            now,
            selected,
            access,
        ->
        val snapshot = runtime.snapshot
        val run = snapshot.run
        val remaining = (run?.endElapsed?.minus(now) ?: 0).coerceAtLeast(0)
        val duration = selected ?: snapshot.selectedDurationMillis
        val finished = snapshot.lastResult?.takeIf {
            snapshot.phase == TimerPhase.FINISHED && now - it.completedElapsed in 0..FINISHED_DURATION
        }
        HomeUiState(
            initialized = runtime.initialized,
            phase = snapshot.phase,
            cancelling = snapshot.isCancelling,
            runId = run?.id,
            durationMillis = duration,
            remainingMillis = remaining,
            endWallMillis = clock.wallTimeMillis() + if (run == null) duration else remaining,
            progress = if (run ==
                null
            ) {
                0f
            } else {
                (remaining.toDouble() / run.totalDurationMillis.coerceAtLeast(1)).toFloat()
            },
            settings = preferences,
            finished = finished,
            lastTest = snapshot.lastTestResult,
            currentResult = snapshot.lastResult,
            exactAlarmsAllowed = access.exact,
            notificationsAllowed = access.notifications,
            batteryUnrestricted = access.batteryUnrestricted,
            notificationAccess = access.notificationAccess,
            shakeMonitoringActive = capabilities.shakeMonitoringActive,
            screenLockAvailable = capabilities.screenLockAvailable,
            degradedReason = runtime.degradedReason,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT), HomeUiState())

    fun markExactRationaleShown() {
        viewModelScope.launch {
            try {
                settings.update { it.copy(exactAlarmRationaleShown = true) }
            } catch (_: IOException) {
                messages.emit(R.string.command_failed)
            }
        }
    }

    fun selectMinutes(minutes: Int) {
        selection.value = minutes * TimerLimits.MINUTE
    }

    fun commitSelection(duration: Long = selection.value ?: state.value.durationMillis) {
        selection.value = duration
        viewModelScope.launch {
            val result = engine.send(TimerCommand.SelectDuration(duration, UUID.randomUUID().toString()))
            selection.value = null
            if (!result.accepted) messages.emit(R.string.command_failed)
        }
    }

    fun start(duration: Long = state.value.durationMillis): Boolean {
        selection.value = null
        return gateway.startFromVisibleActivity(duration)
    }

    fun undoReplacement(runId: String) {
        viewModelScope.launch {
            val result = engine.send(TimerCommand.UndoReplacement(runId, UUID.randomUUID().toString()))
            if (!result.accepted) messages.emit(R.string.undo_unavailable)
        }
    }

    fun cancel() {
        sendForRun { TimerCommand.Stop(UUID.randomUUID().toString(), it) }
    }
    fun adjust(extend: Boolean) {
        sendForRun { runId ->
            val id = UUID.randomUUID().toString()
            if (extend) {
                TimerCommand.Extend(state.value.settings.extendMillis, id, runId)
            } else {
                TimerCommand.Reduce(REDUCE_DURATION, id, runId)
            }
        }
    }

    val lastResultStamp: Long get() = engine.state.value.snapshot.lastResult?.completedElapsed ?: -1L

    fun startTest(): Boolean = !state.value.running && gateway.testFromVisibleActivity()
    suspend fun isPlaying(): Boolean = playback.observe().playing
    suspend fun saveNightly(settings: AppSettings): Boolean = try {
        nightly.save(settings)
    } catch (_: IOException) {
        false
    }

    fun refreshAccess() {
        val access = readPermissions(context)
        permissions.value = access
        viewModelScope.launch {
            if (engine.state.value.exactAlarmsAllowed != access.exact) {
                engine.send(
                    TimerCommand.Reconcile(
                        "exact-access-changed",
                        executionAllowed = capabilities.foregroundServiceActive,
                    ),
                )
            }
            if (engine.state.value.snapshot.run != null &&
                !capabilities.foregroundServiceActive
            ) {
                gateway.ensureForeground()
            }
            try {
                nightly.reconcile()
            } catch (_: IOException) {
                messages.emit(R.string.command_failed)
            }
        }
    }

    private fun sendForRun(command: (String) -> TimerCommand) {
        val runId = state.value.runId ?: return
        viewModelScope.launch {
            val result = engine.send(command(runId))
            if (!result.accepted) {
                messages.emit(R.string.command_failed)
            } else if (result.detail.isNotBlank()) {
                messages.emit(R.string.maximum_duration)
            }
        }
    }

    private companion object {
        const val FINISHED_DURATION = 3_000L
        const val SUBSCRIPTION_TIMEOUT = 0L
    }
}

private data class HomePermissions(
    val exact: Boolean,
    val notifications: Boolean,
    val batteryUnrestricted: Boolean,
    val notificationAccess: Boolean,
)

private fun readPermissions(context: Context): HomePermissions = HomePermissions(
    exact =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
    notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
    batteryUnrestricted = context.getSystemService(
        PowerManager::class.java,
    ).isIgnoringBatteryOptimizations(context.packageName),
    notificationAccess = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context),
)

private const val REDUCE_DURATION = 5 * TimerLimits.MINUTE
