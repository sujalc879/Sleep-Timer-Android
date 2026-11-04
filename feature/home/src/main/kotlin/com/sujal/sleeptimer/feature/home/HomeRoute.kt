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
import com.sujal.sleeptimer.core.model.TimerLimits
import kotlinx.coroutines.launch

private enum class HomeSheet { NONE, CUSTOM, NIGHTLY, READINESS }
internal enum class ReadinessFix { EXACT, NOTIFICATIONS, BATTERY, SESSIONS }

@Composable
// Own saveable overlay state and event routing together; rendering lives in separate components.
@Suppress("LongMethod")
fun HomeRoute(viewModel: HomeViewModel, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var sheet by rememberSaveable { mutableStateOf(HomeSheet.NONE) }
    var testIntro by rememberSaveable { mutableStateOf(false) }
    var awaitingTest by rememberSaveable { mutableStateOf(false) }
    var testResult by rememberSaveable { mutableStateOf(false) }
    val system = homeSystemActions(viewModel::refreshAccess, state, snackbar)
    val fix = system.fix
    val requestNotifications = system.requestNotifications
    val start: (Long) -> Unit = { duration ->
        if (viewModel.start(duration)) {
            requestNotifications()
        } else {
            scope.launch { snackbar.showSnackbar(resources.getString(R.string.command_failed)) }
        }
    }
    val test: () -> Unit = {
        if (viewModel.startTest()) {
            awaitingTest = true
            requestNotifications()
        }
    }
    ObserveHomeLifecycle(viewModel::refreshAccess)
    LaunchedEffect(viewModel, LocalConfiguration.current) {
        viewModel.messages.collect { snackbar.showSnackbar(resources.getString(it)) }
    }
    ObserveTestResult(state, awaitingTest, { show ->
        awaitingTest = false
        testResult = show
    }, { sheet = HomeSheet.NONE })
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
        )
        HomeSheets(sheet, state, viewModel::saveNightly, { sheet = it }, fix, start, snackbar)
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
private fun ObserveTestResult(
    state: HomeUiState,
    awaitingTest: Boolean,
    onResult: (Boolean) -> Unit,
    onRunning: () -> Unit,
) {
    LaunchedEffect(state.currentResult, state.running) {
        if (awaitingTest && !state.running && state.currentResult?.isTest == true) {
            onResult(state.currentResult.status != com.sujal.sleeptimer.core.model.StopStatus.CANCELLED)
        }
        if (state.running) onRunning()
    }
}
