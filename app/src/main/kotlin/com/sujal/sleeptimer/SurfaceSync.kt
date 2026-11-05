package com.sujal.sleeptimer

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import com.sujal.sleeptimer.core.model.Diagnostics
import com.sujal.sleeptimer.core.model.SettingsStore
import com.sujal.sleeptimer.core.model.TimerLimits
import com.sujal.sleeptimer.core.model.isActive
import com.sujal.sleeptimer.core.timer.TimerAppScope
import com.sujal.sleeptimer.core.timer.TimerEngine
import com.sujal.sleeptimer.surface.tile.SleepTimerTile
import com.sujal.sleeptimer.surface.widget.refreshTimerWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Singleton
class SurfaceSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: TimerEngine,
    private val settings: SettingsStore,
    private val diagnostics: Diagnostics,
    @TimerAppScope private val scope: CoroutineScope,
) {
    private var started = false
    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(engine.state, settings.data) { state, preferences ->
                val snapshot = state.snapshot
                "${state.initialized}:${snapshot.run?.id}:${snapshot.phase}:${snapshot.run?.endElapsed}:" +
                    "${snapshot.selectedDurationMillis}:${preferences.dynamicColor}:${preferences.theme}"
            }.distinctUntilChanged().collectLatest {
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
