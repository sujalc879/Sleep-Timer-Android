package com.sujal.sleeptimer.feature.settings

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sujal.sleeptimer.core.model.AppSettings
import com.sujal.sleeptimer.core.model.Diagnostics
import com.sujal.sleeptimer.core.model.RuntimeCapabilities
import com.sujal.sleeptimer.core.model.SettingsStore
import com.sujal.sleeptimer.core.model.TimerFeedback
import com.sujal.sleeptimer.core.model.WarningMode
import com.sujal.sleeptimer.core.model.isActive
import com.sujal.sleeptimer.core.system.AndroidScreenLocker
import com.sujal.sleeptimer.core.timer.TimerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
@Suppress("LongParameterList") // Explicit platform and runtime ports at the presentation boundary.
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: SettingsStore,
    private val engine: TimerEngine,
    private val feedback: TimerFeedback,
    private val capabilities: RuntimeCapabilities,
    diagnostics: Diagnostics,
) : ViewModel() {
    val locker = AndroidScreenLocker(context, diagnostics)
    val messages = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    private val access = MutableStateFlow(0)
    val state = combine(
        store.data.catch { error ->
            if (error is IOException) {
                messages.emit(R.string.save_failed)
                emit(AppSettings())
            } else {
                throw error
            }
        },
        engine.state,
        access,
    ) { settings, runtime, _ ->
        SettingsUiState(
            settings,
            runtime.initialized,
            runtime.snapshot.isActive,
            context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null,
            locker.active,
            context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) ==
                true,
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context),
            com.sujal.sleeptimer.core.system.TileMembership(context).added,
        )
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun change(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            try {
                store.update(transform)
            } catch (_: IOException) {
                messages.emit(R.string.save_failed)
            }
        }
    }

    fun preview(mode: WarningMode) = feedback.warning(mode)

    fun refresh() {
        access.value++
        capabilities.screenLockAvailable = locker.active
        if (state.value.settings.screenOff &&
            !locker.active
        ) {
            change { it.copy(screenOff = false, screenOffAdminRevoked = true) }
        }
    }
}
