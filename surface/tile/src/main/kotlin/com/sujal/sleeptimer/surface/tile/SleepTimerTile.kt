package com.sujal.sleeptimer.surface.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.sujal.sleeptimer.core.model.Clock
import com.sujal.sleeptimer.core.model.Diagnostics
import com.sujal.sleeptimer.core.model.StartSource
import com.sujal.sleeptimer.core.model.TimerLimits
import com.sujal.sleeptimer.core.model.isActive
import com.sujal.sleeptimer.core.system.TileMembership
import com.sujal.sleeptimer.core.timer.TimerEngine
import com.sujal.sleeptimer.core.timer.TimerServiceIntents
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SleepTimerTile : TileService() {
    @Inject lateinit var engine: TimerEngine

    @Inject lateinit var clock: Clock

    @Inject lateinit var diagnostics: Diagnostics
    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, _ ->
                diagnostics.record("tile-operation-unavailable")
            },
        )
    private var listening: Job? = null

    override fun onTileAdded() {
        TileMembership(this).added = true
    }
    override fun onTileRemoved() {
        TileMembership(this).added = false
    }
    override fun onStartListening() {
        listening?.cancel()
        listening = scope.launch {
            engine.state.collectLatest { state ->
                if (state.initialized) {
                    while (isActive) {
                        refresh()
                        delay(REFRESH)
                    }
                }
            }
        }
    }
    override fun onStopListening() {
        listening?.cancel()
    }
    override fun onClick() {
        scope.launch {
            val snapshot = engine.state.first { it.initialized }.snapshot
            if (!snapshot.isActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(
                        this@SleepTimerTile,
                        0,
                        Intent(this@SleepTimerTile, TileStartActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                return@launch
            }
            val intent = if (snapshot.isActive) {
                TimerServiceIntents.base(this@SleepTimerTile, TimerServiceIntents.CANCEL)
                    .putExtra(TimerServiceIntents.EXPECTED_RUN, snapshot.run?.id)
            } else {
                TimerServiceIntents.base(this@SleepTimerTile, TimerServiceIntents.START)
                    .putExtra(TimerServiceIntents.DURATION, snapshot.selectedDurationMillis)
                    .putExtra(TimerServiceIntents.SOURCE, StartSource.TILE.name)
                    .putExtra(TimerServiceIntents.ONLY_IF_IDLE, true)
            }
            try {
                ContextCompat.startForegroundService(this@SleepTimerTile, intent)
            } catch (_: RuntimeException) {
                diagnostics.record("tile-start-denied")
            }
        }
    }
    private fun refresh() {
        val snapshot = engine.state.value.snapshot
        qsTile?.apply {
            state = if (snapshot.isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(R.string.tile_label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val remaining = (snapshot.run?.endElapsed ?: 0) - clock.elapsedRealtime()
                val minutes = (remaining / TimerLimits.MINUTE).coerceAtLeast(1).toInt()
                subtitle = when {
                    !snapshot.isActive -> getString(R.string.tile_off)
                    remaining < TimerLimits.MINUTE -> getString(R.string.tile_under_minute)
                    else -> resources.getQuantityString(R.plurals.tile_minutes, minutes, minutes)
                }
            }
            updateTile()
        }
    }
    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
    companion object {
        private const val REFRESH = 1_000L
    }
}
