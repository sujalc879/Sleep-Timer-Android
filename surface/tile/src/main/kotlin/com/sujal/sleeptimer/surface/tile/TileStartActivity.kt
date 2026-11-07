package com.sujal.sleeptimer.surface.tile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.sujal.sleeptimer.core.model.RuntimeCapabilities
import com.sujal.sleeptimer.core.model.StartSource
import com.sujal.sleeptimer.core.timer.TimerEngine
import com.sujal.sleeptimer.core.timer.TimerGateway
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A resumed, transparent user-interaction surface for Android 17 audio hardening. */
@AndroidEntryPoint
class TileStartActivity : ComponentActivity() {
    @Inject lateinit var engine: TimerEngine

    @Inject lateinit var gateway: TimerGateway

    @Inject lateinit var capabilities: RuntimeCapabilities
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.O_MR1
        ) {
            setShowWhenLocked(true)
        }
    }
    override fun onPostResume() {
        super.onPostResume()
        capabilities.activityVisible = true
        lifecycleScope.launch {
            val snapshot = engine.state.first { it.initialized }.snapshot
            gateway.startFromVisibleActivity(
                snapshot.selectedDurationMillis,
                source = StartSource.TILE,
                onlyIfIdle = true,
            )
            finish()
        }
    }
    override fun onPause() {
        capabilities.activityVisible = false
        super.onPause()
    }
}
