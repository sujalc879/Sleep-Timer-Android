package com.sujal.sleeptimer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.sujal.sleeptimer.core.model.PlaybackController
import com.sujal.sleeptimer.core.model.RuntimeCapabilities
import com.sujal.sleeptimer.core.timer.TimerAppScope
import com.sujal.sleeptimer.core.timer.TimerGateway
import com.sujal.sleeptimer.core.timer.TimerServiceIntents
import com.sujal.sleeptimer.feature.home.HomeRoute
import com.sujal.sleeptimer.feature.home.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModels()

    @Inject lateinit var capabilities: RuntimeCapabilities

    @Inject lateinit var gateway: TimerGateway

    @Inject lateinit var playback: PlaybackController

    @Inject @TimerAppScope
    lateinit var scope: CoroutineScope

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SleepTimerApp(homeContent = { onSettings -> HomeRoute(homeViewModel, onSettings) }) }
    }

    override fun onPostResume() {
        super.onPostResume()
        capabilities.activityVisible = true
        handleDebugCommand()
    }

    override fun onPause() {
        capabilities.activityVisible = false
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (capabilities.activityVisible) handleDebugCommand()
    }

    private fun handleDebugCommand() {
        if (!BuildConfig.DEBUG) return
        val action = intent.action
        if (action !in
            setOf(TimerServiceIntents.DEBUG_START, TimerServiceIntents.DEBUG_TEST, TimerServiceIntents.DEBUG_STATUS)
        ) {
            return
        }
        val commandId =
            intent.getStringExtra(TimerServiceIntents.COMMAND_ID)?.takeIf {
                it.isNotBlank() &&
                    it.length <= MAX_COMMAND_ID
            }
                ?: UUID.randomUUID().toString()
        val duration = intent.getLongExtra(TimerServiceIntents.DURATION, -1)
        intent.action = Intent.ACTION_MAIN
        when (action) {
            TimerServiceIntents.DEBUG_START -> gateway.startFromVisibleActivity(duration, commandId)

            TimerServiceIntents.DEBUG_TEST -> gateway.testFromVisibleActivity(commandId)

            TimerServiceIntents.DEBUG_STATUS -> scope.launch {
                val state = playback.observe()
                Log.d(
                    "SleepTimer",
                    "probe-local=${state.localPlaying} remote=${state.remotePlaying} " +
                        "interruption=${state.interruption} route-known=${state.volume?.route != null}",
                )
            }
        }
    }

    private companion object {
        const val MAX_COMMAND_ID = 128
    }
}
