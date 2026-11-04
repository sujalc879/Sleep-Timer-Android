package com.sujal.sleeptimer.feature.home

import android.content.Context
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
import com.sujal.sleeptimer.core.timer.NightlyController
import com.sujal.sleeptimer.core.timer.TimerEngine
import com.sujal.sleeptimer.core.timer.TimerGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@HiltViewModel
@Suppress("LongParameterList") // Explicit DI ports; the ViewModel owns presentation rather than runtime effects.
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: TimerEngine,
    settings: SettingsStore,
    private val clock: Clock,
    private val gateway: TimerGateway,
    private val playback: PlaybackController,
    private val capabilities: RuntimeCapabilities,
    private val nightly: NightlyController,
) : ViewModel() {
    private val selection = MutableStateFlow<Long?>(null)
    private val permissions = MutableStateFlow(readPermissions(context))
    val messages = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    private val ticks = flow {
        while (currentCoroutineContext().isActive) {
            emit(clock.elapsedRealtime())
            delay(DISPLAY_TICK)
        }
    }
    val state = combine(engine.state, settings.data, ticks, selection, permissions) {
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
            lastTest = snapshot.lastResult?.takeIf { it.isTest },
            exactAlarmsAllowed = runtime.exactAlarmsAllowed,
            notificationsAllowed = access.notifications,
            batteryUnrestricted = access.batteryUnrestricted,
            notificationAccess = access.notificationAccess,
            shakeMonitoringActive = capabilities.shakeMonitoringActive,
            screenLockAvailable = capabilities.screenLockAvailable,
            degradedReason = runtime.degradedReason,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT), HomeUiState())

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

    fun startTest(): Boolean = !state.value.running && gateway.testFromVisibleActivity()
    suspend fun isPlaying(): Boolean = playback.observe().playing
    suspend fun saveNightly(settings: AppSettings): Boolean = nightly.save(settings)

    fun refreshAccess() {
        permissions.value = readPermissions(context)
        viewModelScope.launch {
            if (engine.state.value.snapshot.run != null &&
                !capabilities.foregroundServiceActive
            ) {
                gateway.ensureForeground()
            }
            nightly.reconcile()
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
        const val DISPLAY_TICK = 250L
        const val FINISHED_DURATION = 3_000L
        const val SUBSCRIPTION_TIMEOUT = 5_000L
    }
}

private data class HomePermissions(
    val notifications: Boolean,
    val batteryUnrestricted: Boolean,
    val notificationAccess: Boolean,
)

private fun readPermissions(context: Context): HomePermissions = HomePermissions(
    notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
    batteryUnrestricted = context.getSystemService(
        PowerManager::class.java,
    ).isIgnoringBatteryOptimizations(context.packageName),
    notificationAccess = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context),
)

private const val REDUCE_DURATION = 5 * TimerLimits.MINUTE
