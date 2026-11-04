package com.sujal.sleeptimer.feature.home

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import kotlinx.coroutines.launch

internal data class HomeSystemActions(val fix: (ReadinessFix) -> Unit, val requestNotifications: () -> Unit)

@Composable
internal fun homeSystemActions(
    onRefresh: () -> Unit,
    state: HomeUiState,
    snackbar: SnackbarHostState,
): HomeSystemActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val unavailable = stringResource(R.string.system_settings_unavailable)
    val settingsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            onRefresh()
        }
    val notificationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onRefresh() }
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
            scope.launch { snackbar.showSnackbar(unavailable) }
        } catch (
            _: SecurityException,
        ) {
            scope.launch { snackbar.showSnackbar(unavailable) }
        }
    }
    val requestNotifications: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !state.notificationsAllowed) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    return HomeSystemActions(fix, requestNotifications)
}
