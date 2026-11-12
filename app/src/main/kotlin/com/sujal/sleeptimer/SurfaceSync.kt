package com.sujal.sleeptimer

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.sujal.sleeptimer.core.model.Diagnostics
import com.sujal.sleeptimer.core.model.SettingsStore
import com.sujal.sleeptimer.core.model.StopStatus
import com.sujal.sleeptimer.core.model.TimerLimits
import com.sujal.sleeptimer.core.model.isActive
import com.sujal.sleeptimer.core.timer.TimerAppScope
import com.sujal.sleeptimer.core.timer.TimerEngine
import com.sujal.sleeptimer.core.timer.TimerNotifications
import com.sujal.sleeptimer.surface.tile.SleepTimerTile
import com.sujal.sleeptimer.surface.widget.publishWidgetPreviews
import com.sujal.sleeptimer.surface.widget.refreshTimerWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Singleton
class SurfaceSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: TimerEngine,
    private val settings: SettingsStore,
    private val diagnostics: Diagnostics,
    @TimerAppScope private val scope: CoroutineScope,
    private val notifications: TimerNotifications,
) {
    private var started = false
    private val configuration = MutableStateFlow(0L)
    private val configurationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            configuration.value++
        }
    }
    fun start() {
        if (started) return
        started = true
        ContextCompat.registerReceiver(
            context,
            configurationReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_LOCALE_CHANGED)
                addAction(Intent.ACTION_CONFIGURATION_CHANGED)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        scope.launch {
            try {
                publishWidgetPreviews(context)?.let { diagnostics.record("widget-previews-${it.first}-${it.second}") }
            } catch (
                _: RuntimeException,
            ) {
                diagnostics.record("widget-preview-unavailable")
            }
        }
        scope.launch {
            engine.state.map { state ->
                state.snapshot.lastResult?.takeIf { state.initialized && it.status == StopStatus.INTERRUPTED }
            }.distinctUntilChanged().filterNotNull().collect(notifications::completed)
        }
        scope.launch {
            var previousConfiguration = configuration.value
            combine(engine.state, settings.data, configuration) { state, preferences, version ->
                val snapshot = state.snapshot
                "${state.initialized}:${snapshot.run?.id}:${snapshot.phase}:${snapshot.run?.endElapsed}:" +
                    "${snapshot.selectedDurationMillis}:${preferences.dynamicColor}:${preferences.theme}:$version"
            }.distinctUntilChanged().collectLatest {
                if (configuration.value != previousConfiguration && engine.state.value.snapshot.isActive) {
                    previousConfiguration = configuration.value
                    notifications.refresh(engine.state.value, stateChanged = false)
                }
                refresh()
                while (currentCoroutineContext().isActive && engine.state.value.snapshot.isActive) {
                    delay(TimerLimits.MINUTE)
                    refresh()
                }
            }
        }
    }

    private suspend fun refresh() {
        try {
            TileService.requestListeningState(context, ComponentName(context, SleepTimerTile::class.java))
            refreshTimerWidget(context)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: java.io.IOException) {
            diagnostics.record("widget-refresh-storage-unavailable")
        } catch (_: RuntimeException) {
            diagnostics.record("surface-refresh-unavailable")
        }
    }
}
