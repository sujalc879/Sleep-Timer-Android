package com.sujal.sleeptimer.feature.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sujal.sleeptimer.core.model.WarningMode
import com.sujal.sleeptimer.core.system.oemBatteryGuide
import kotlinx.coroutines.launch

@Composable
// Saveable overlay and Android result ownership at one route boundary.
@Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod")
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNightly: () -> Unit,
    onTest: () -> Unit,
    onAddTile: (onHelp: () -> Unit) -> Unit,
    onAddWidget: (presets: Boolean, onHelp: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf(SettingsDialog.NONE) }
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refresh() }
    val admin = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (viewModel.locker.active) viewModel.change { it.copy(screenOff = true, screenOffAdminRevoked = false) }
        viewModel.refresh()
    }
    val open: (Intent) -> Unit = { intent ->
        try {
            launcher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            scope.launch { snackbar.showSnackbar(resources.getString(R.string.unavailable)) }
        } catch (_: SecurityException) {
            scope.launch { snackbar.showSnackbar(resources.getString(R.string.unavailable)) }
        }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event ==
                Lifecycle.Event.ON_RESUME
            ) {
                viewModel.refresh()
            }
        }
        owner.lifecycle.addObserver(observer)
        viewModel.refresh()
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(resources.getString(it)) } }
    Box(modifier) {
        SettingsScreen(
            state,
            SettingsActions(
                change = viewModel::change,
                screenOff = { enabled ->
                    if (enabled) {
                        if (viewModel.locker.active) {
                            viewModel.change { it.copy(screenOff = true, screenOffAdminRevoked = false) }
                        } else {
                            dialog =
                                SettingsDialog.ADMIN
                        }
                    } else {
                        viewModel.change { it.copy(screenOff = false, screenOffAdminRevoked = false) }
                        if (viewModel.locker.active) dialog = SettingsDialog.REMOVE_ADMIN
                    }
                },
                nightly = onNightly, test = onTest,
                tile = { onAddTile { dialog = SettingsDialog.TILE_HELP } },
                widget = { dialog = SettingsDialog.WIDGET_PICKER },
                extend = { dialog = SettingsDialog.EXTEND }, warning = { dialog = SettingsDialog.WARNING },
                battery = { dialog = SettingsDialog.BATTERY }, sessions = { dialog = SettingsDialog.SESSIONS },
                diagnostics = { open(diagnosticsShareIntent(viewModel.diagnostics())) },
            ),
            onBack,
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        val dismiss = { dialog = SettingsDialog.NONE }
        when (dialog) {
            SettingsDialog.WIDGET_PICKER -> SettingsWidgetPicker({ presets ->
                dialog = SettingsDialog.NONE
                onAddWidget(presets) { dialog = SettingsDialog.WIDGET_HELP }
            }, dismiss)

            SettingsDialog.NONE -> Unit

            SettingsDialog.EXTEND, SettingsDialog.WARNING -> SettingsPicker(dialog, state, { value ->
                if (dialog == SettingsDialog.EXTEND) {
                    viewModel.change { it.copy(extendMillis = value * MINUTE) }
                } else {
                    val mode = WarningMode.entries[value]
                    viewModel.change { it.copy(warning = mode) }
                    viewModel.preview(mode)
                }
            }, dismiss)

            SettingsDialog.ADMIN -> SettingsExplanation(
                stringResource(R.string.screen_off),
                stringResource(R.string.admin_explanation),
                stringResource(R.string.activate),
                {
                    dismiss()
                    try {
                        admin.launch(viewModel.locker.activationIntent())
                    } catch (_: ActivityNotFoundException) {
                        scope.launch { snackbar.showSnackbar(resources.getString(R.string.unavailable)) }
                    } catch (_: SecurityException) {
                        scope.launch { snackbar.showSnackbar(resources.getString(R.string.unavailable)) }
                    }
                },
                dismiss,
            )

            SettingsDialog.REMOVE_ADMIN -> SettingsExplanation(
                stringResource(R.string.manage_admin),
                stringResource(R.string.admin_remove),
                stringResource(R.string.deactivate),
                {
                    viewModel.locker.remove()
                    viewModel.refresh()
                    dismiss()
                },
                dismiss,
            )

            SettingsDialog.SESSIONS -> SettingsExplanation(
                stringResource(R.string.sessions),
                stringResource(R.string.sessions_explanation),
                stringResource(R.string.open_settings),
                {
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                            android.content.ComponentName(
                                context.packageName,
                                "com.sujal.sleeptimer.core.playback.MediaSessionBridge",
                            ).flattenToString(),
                        )
                    } else {
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    }
                    open(intent)
                    dismiss()
                },
                dismiss,
            )

            SettingsDialog.BATTERY -> {
                val guide = remember { oemBatteryGuide(Build.MANUFACTURER) }
                SettingsExplanation(
                    stringResource(R.string.battery),
                    stringResource(R.string.battery_explanation) + "\n\n" + stringResource(guide.instructions),
                    stringResource(R.string.open_settings),
                    {
                        open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        dismiss()
                    },
                    dismiss,
                    extra = {
                        TextButton(onClick = {
                            open(Intent(Intent.ACTION_VIEW, guide.url.toUri()))
                        }) { Text(stringResource(R.string.learn_more)) }
                    },
                )
            }

            SettingsDialog.TILE_HELP, SettingsDialog.WIDGET_HELP -> SettingsExplanation(
                stringResource(if (dialog == SettingsDialog.TILE_HELP) R.string.tile else R.string.widget),
                stringResource(if (dialog == SettingsDialog.TILE_HELP) R.string.tile_help else R.string.widget_help),
                stringResource(R.string.done),
                dismiss,
                dismiss,
            )
        }
    }
}

private const val MINUTE = 60_000L
