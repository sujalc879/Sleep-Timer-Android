package com.sujal.sleeptimer.feature.home

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sujal.sleeptimer.core.model.StopStatus
import com.sujal.sleeptimer.core.model.TimerLimits
import kotlinx.coroutines.launch

private enum class HomeSheet { NONE, CUSTOM, NIGHTLY, READINESS }
internal enum class ReadinessFix { EXACT, NOTIFICATIONS, BATTERY, SESSIONS }

@Composable
fun HomeRoute(viewModel: HomeViewModel, onSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var sheet by rememberSaveable { mutableStateOf(HomeSheet.NONE) }
    var testIntro by rememberSaveable { mutableStateOf(false) }
    var awaitingTest by rememberSaveable { mutableStateOf(false) }
    var testResult by rememberSaveable { mutableStateOf(false) }
    val settingsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.refreshAccess()
        }
    val notificationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refreshAccess() }
    val fix: (ReadinessFix) -> Unit = { item ->
        val intent = when (item) {
            ReadinessFix.EXACT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
            }

            ReadinessFix.NOTIFICATIONS -> Intent(
                Settings.ACTION_APP_NOTIFICATION_SETTINGS,
            ).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

            ReadinessFix.BATTERY -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

            ReadinessFix.SESSIONS -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        try {
            settingsLauncher.launch(intent)
        } catch (
            _: ActivityNotFoundException,
        ) {
            scope.launch { snackbar.showSnackbar(context.getString(R.string.system_settings_unavailable)) }
        } catch (
            _: SecurityException,
        ) {
            scope.launch { snackbar.showSnackbar(context.getString(R.string.system_settings_unavailable)) }
        }
    }
    val requestNotifications: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !state.notificationsAllowed) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val start: (Long) -> Unit = { duration ->
        if (viewModel.start(duration)) {
            requestNotifications()
        } else {
            scope.launch { snackbar.showSnackbar(context.getString(R.string.command_failed)) }
        }
    }
    val test: () -> Unit = {
        if (viewModel.startTest()) {
            awaitingTest = true
            requestNotifications()
        }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event ==
                Lifecycle.Event.ON_RESUME
            ) {
                viewModel.refreshAccess()
            }
        }
        owner.lifecycle.addObserver(observer)
        viewModel.refreshAccess()
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(context.getString(it)) } }
    LaunchedEffect(state.lastTest, state.running) {
        if (awaitingTest && !state.running && state.lastTest != null) {
            awaitingTest = false
            testResult = true
        }
        if (state.running) sheet = HomeSheet.NONE
    }
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
    when (sheet) {
        HomeSheet.CUSTOM -> DurationSheet(state.durationMillis, { sheet = HomeSheet.NONE }) { duration ->
            sheet =
                HomeSheet.NONE
            start(duration)
        }

        HomeSheet.NIGHTLY -> NightlySheet(state, { sheet = HomeSheet.NONE }, { value ->
            scope.launch {
                if (viewModel.saveNightly(value)) {
                    sheet = HomeSheet.NONE
                } else {
                    snackbar.showSnackbar(context.getString(R.string.exact_help))
                }
            }
        }, { fix(ReadinessFix.EXACT) })

        HomeSheet.READINESS -> ReadinessSheet(state, { sheet = HomeSheet.NONE }, fix)

        HomeSheet.NONE -> Unit
    }
    if (testIntro) {
        AlertDialog(
            onDismissRequest = { testIntro = false },
            title = { Text(stringResource(R.string.test_title)) },
            text = { Text(stringResource(R.string.test_intro)) },
            confirmButton = {
                TextButton(onClick = {
                    testIntro = false
                    test()
                }) { Text(stringResource(R.string.test_start)) }
            },
            dismissButton = { TextButton(onClick = { testIntro = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (testResult) {
        val result = state.lastTest
        val title = when (result?.status) {
            StopStatus.STOPPED -> if (result.localOnly) R.string.test_local_stopped else R.string.test_stopped
            StopStatus.NOTHING_PLAYING -> R.string.finished_quiet
            else -> R.string.test_failed
        }
        AlertDialog(
            onDismissRequest = { testResult = false },
            title = { Text(stringResource(title)) },
            text = {
                Text(
                    stringResource(
                        if (result?.status ==
                            StopStatus.STOPPED
                        ) {
                            R.string.pipeline_stop
                        } else {
                            R.string.test_fix
                        },
                    ),
                )
            },
            confirmButton = { TextButton(onClick = { testResult = false }) { Text(stringResource(R.string.done)) } },
        )
    }
}
