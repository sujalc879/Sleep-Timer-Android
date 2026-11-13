package com.sujal.sleeptimer.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sujal.sleeptimer.core.model.AppSettings
import com.sujal.sleeptimer.core.model.StopStatus
import com.sujal.sleeptimer.core.model.TimerLimits
import com.sujal.sleeptimer.core.timer.SurfaceEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

enum class HomeRequest { NONE, NIGHTLY, TEST }

private enum class HomeSheet { NONE, CUSTOM, NIGHTLY, READINESS }
internal enum class ReadinessFix { EXACT, NOTIFICATIONS, BATTERY, SESSIONS, ADMIN, RUNTIME }

@Composable
// Own saveable overlay state and event routing together; rendering lives in separate components.
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun HomeRoute(
    viewModel: HomeViewModel,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    request: HomeRequest = HomeRequest.NONE,
    onRequestHandled: () -> Unit = {},
    surfaceEvents: Flow<SurfaceEvent?> = emptyFlow(),
    onSurfaceConsumed: (String) -> Unit = {},
) {
    val liveState = viewModel.state.collectAsStateWithLifecycle()
    val state by viewModel.layoutState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var sheet by rememberSaveable { mutableStateOf(HomeSheet.NONE) }
    var testIntro by rememberSaveable { mutableStateOf(false) }
    var awaitingTest by rememberSaveable { mutableStateOf(false) }
    var testBaseline by rememberSaveable { mutableStateOf(-1L) }
    var testResult by rememberSaveable { mutableStateOf(false) }
    var exactRationale by rememberSaveable { mutableStateOf(false) }
    var reliabilityRationale by rememberSaveable { mutableStateOf<ReadinessFix?>(null) }
    val system = homeSystemActions(viewModel::refreshAccess, state, snackbar)
    val fix: (ReadinessFix) -> Unit = { item ->
        when (item) {
            ReadinessFix.ADMIN -> {
                sheet = HomeSheet.NONE
                onSettings()
            }

            ReadinessFix.RUNTIME -> {
                sheet = HomeSheet.NONE
                viewModel.refreshAccess()
            }

            ReadinessFix.EXACT -> exactRationale = true

            ReadinessFix.BATTERY, ReadinessFix.SESSIONS -> reliabilityRationale = item

            ReadinessFix.NOTIFICATIONS -> system.fix(item)
        }
    }
    val requestNotifications = system.requestNotifications
    val start: (Long) -> Unit = { duration ->
        if (viewModel.start(duration)) {
            requestNotifications {
                if (!state.exactAlarmsAllowed && !state.settings.exactAlarmRationaleShown) {
                    viewModel.markExactRationaleShown()
                    exactRationale = true
                }
            }
        } else {
            scope.launch { snackbar.showSnackbar(resources.getString(R.string.command_failed)) }
        }
    }
    val test: () -> Unit = {
        val baseline = viewModel.lastResultStamp
        if (viewModel.startTest()) {
            testBaseline = baseline
            awaitingTest = true
            requestNotifications {
                if (!state.exactAlarmsAllowed && !state.settings.exactAlarmRationaleShown) {
                    viewModel.markExactRationaleShown()
                    exactRationale = true
                }
            }
        }
    }
    LaunchedEffect(request) {
        when (request) {
            HomeRequest.NIGHTLY -> sheet = HomeSheet.NIGHTLY

            HomeRequest.TEST -> if (!state.running) {
                if (viewModel.isPlaying()) test() else testIntro = true
            }

            HomeRequest.NONE -> Unit
        }
        onRequestHandled()
    }
    ObserveSurfaceEvent(surfaceEvents, {
        sheet = HomeSheet.CUSTOM
    }, viewModel::undoReplacement, snackbar, onSurfaceConsumed)
    ObserveHomeLifecycle(viewModel::refreshAccess)
    LaunchedEffect(viewModel, LocalConfiguration.current) {
        viewModel.messages.collect { snackbar.showSnackbar(resources.getString(it)) }
    }
    ObserveTestResult(state, awaitingTest, testBaseline, { show ->
        awaitingTest = false
        testResult = show
    }, { sheet = HomeSheet.NONE })
    ObserveInterruptedResult(state) { sheet = HomeSheet.READINESS }
    Box(modifier) {
        HomeScreen(
            state,
            HomeActions(
                onStart = { start(state.durationMillis) }, onStop = viewModel::cancel,
                onMinutesChange = viewModel::selectMinutes, onSelectionFinished = { viewModel.commitSelection() },
                onPreset = {
                    viewModel.commitSelection(it * TimerLimits.MINUTE)
                }, onReduce = { viewModel.adjust(false) }, onExtend = { viewModel.adjust(true) },
                onTest = { scope.launch { if (viewModel.isPlaying()) test() else testIntro = true } },
                onReadiness = { sheet = HomeSheet.READINESS }, onCustom = { sheet = HomeSheet.CUSTOM }, onNightly = {
                    sheet =
                        HomeSheet.NIGHTLY
                },
            ),
            onSettings,
            snackbar = snackbar,
            liveState = liveState,
        )
        HomeSheets(sheet, state, viewModel::saveNightly, { sheet = it }, fix, start, snackbar)
        ExactAlarmRationale(exactRationale, { exactRationale = false }, {
            exactRationale = false
            system.fix(ReadinessFix.EXACT)
        })
        ReliabilityRationale(reliabilityRationale, { reliabilityRationale = null }, { item ->
            reliabilityRationale = null
            system.fix(item)
        })
        TestDialogs(
            testIntro,
            { testIntro = false },
            {
                testIntro = false
                test()
            },
            testResult,
            { testResult = false },
            state.currentResult,
        )
    }
}

@Composable
private fun ObserveHomeLifecycle(onResume: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, onResume) {
        val observer = LifecycleEventObserver { _, event ->
            if (event ==
                Lifecycle.Event.ON_RESUME
            ) {
                onResume()
            }
        }
        owner.lifecycle.addObserver(observer)
        onResume()
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun HomeSheets(
    sheet: HomeSheet,
    state: HomeUiState,
    onSaveNightly: suspend (AppSettings) -> Boolean,
    onSheetChange: (HomeSheet) -> Unit,
    fix: (ReadinessFix) -> Unit,
    start: (Long) -> Unit,
    snackbar: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    when (sheet) {
        HomeSheet.CUSTOM -> DurationSheet(state.durationMillis, { onSheetChange(HomeSheet.NONE) }) { duration ->
            onSheetChange(HomeSheet.NONE)
            start(duration)
        }

        HomeSheet.NIGHTLY -> NightlySheet(state, { onSheetChange(HomeSheet.NONE) }, { value ->
            scope.launch {
                if (onSaveNightly(value)) {
                    onSheetChange(HomeSheet.NONE)
                } else {
                    snackbar.showSnackbar(resources.getString(R.string.exact_help))
                }
            }
        }, { fix(ReadinessFix.EXACT) })

        HomeSheet.READINESS -> ReadinessSheet(state, { onSheetChange(HomeSheet.NONE) }, fix)

        HomeSheet.NONE -> Unit
    }
}

@Composable
internal fun ObserveTestResult(
    state: HomeUiState,
    awaitingTest: Boolean,
    baseline: Long,
    onResult: (Boolean) -> Unit,
    onRunning: () -> Unit,
) {
    LaunchedEffect(state.currentResult, state.running, awaitingTest, baseline) {
        val completion = state.currentResult?.takeIf { it.isTest && it.completedElapsed != baseline }
        if (awaitingTest && !state.running && completion != null) {
            onResult(completion.status !in setOf(StopStatus.CANCELLED, StopStatus.INTERRUPTED))
        }
        if (state.running) onRunning()
    }
}

@Composable
internal fun ObserveInterruptedResult(state: HomeUiState, onInterrupted: () -> Unit) {
    var shown by rememberSaveable { mutableStateOf<String?>(null) }
    val result = state.currentResult
    LaunchedEffect(state.initialized, state.interrupted, result) {
        if (state.initialized && state.interrupted && result != null) {
            val identity = "${result.completedElapsed}:${result.detail}:${result.isTest}"
            if (shown != identity) {
                shown = identity
                onInterrupted()
            }
        }
    }
}
