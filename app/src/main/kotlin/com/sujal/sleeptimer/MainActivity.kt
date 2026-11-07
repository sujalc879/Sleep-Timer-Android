package com.sujal.sleeptimer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.sujal.sleeptimer.core.model.PlaybackController
import com.sujal.sleeptimer.core.model.RuntimeCapabilities
import com.sujal.sleeptimer.core.timer.TimerAppScope
import com.sujal.sleeptimer.core.timer.TimerGateway
import com.sujal.sleeptimer.core.timer.TimerServiceIntents
import com.sujal.sleeptimer.feature.home.HomeRoute
import com.sujal.sleeptimer.feature.home.HomeViewModel
import com.sujal.sleeptimer.feature.settings.SettingsRoute
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModels()
    private val settingsViewModel: com.sujal.sleeptimer.feature.settings.SettingsViewModel by viewModels()
    private var contentReady = false
    private var externalNavigation by mutableLongStateOf(0L)

    @Inject lateinit var surfaceLaunch: SurfaceLaunchCoordinator

    @Inject lateinit var capabilities: RuntimeCapabilities

    @Inject lateinit var gateway: TimerGateway

    @Inject lateinit var playback: PlaybackController

    @Inject @TimerAppScope
    lateinit var scope: CoroutineScope

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        splash.setKeepOnScreenCondition { !contentReady }
        val appearance = WindowAppearance(window)
        setContent {
            val state by settingsViewModel.state.collectAsStateWithLifecycle()
            SleepTimerRoot(
                state,
                settingsViewModel::refresh,
                homeContent = { openSettings, request, handled ->
                    HomeRoute(
                        homeViewModel,
                        openSettings,
                        request = request,
                        onRequestHandled = handled,
                        surfaceEvents = surfaceLaunch.events,
                        onSurfaceConsumed = surfaceLaunch::consumed,
                    )
                },
                settingsContent = { back, nightly, test ->
                    SettingsRoute(
                        settingsViewModel,
                        back,
                        nightly,
                        test,
                        { help -> addTimerTile(help) },
                        { presets, help -> addTimerWidget(help, presets) },
                    )
                },
                appearance = appearance,
                onReady = { contentReady = true },
                externalNavigation = externalNavigation,
            )
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        capabilities.activityVisible = true
        settingsViewModel.refresh()
        handleSurfaceCommand()
        handleDebugCommand()
    }

    override fun onPause() {
        capabilities.activityVisible = false
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (capabilities.activityVisible) {
            handleSurfaceCommand()
            handleDebugCommand()
        }
    }

    fun settingsRefresh() = settingsViewModel.refresh()

    private fun handleSurfaceCommand() {
        val request = com.sujal.sleeptimer.core.timer.SurfaceIntents.parse(
            intent.action,
            intent.getIntExtra(com.sujal.sleeptimer.core.timer.SurfaceIntents.MINUTES, -1),
        ) ?: return
        intent.action = Intent.ACTION_MAIN
        externalNavigation++
        lifecycleScope.launch { surfaceLaunch.launch(request) }
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
