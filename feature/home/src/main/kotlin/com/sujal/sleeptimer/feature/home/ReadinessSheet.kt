package com.sujal.sleeptimer.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sujal.sleeptimer.core.model.StopStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReadinessSheet(state: HomeUiState, onDismiss: () -> Unit, onFix: (ReadinessFix) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.readiness_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(8.dp),
            )
            ReadinessRow(
                stringResource(R.string.exact_alarms),
                stringResource(if (state.exactAlarmsAllowed) R.string.allowed else R.string.off),
            ) {
                onFix(ReadinessFix.EXACT)
            }
            ReadinessRow(
                stringResource(R.string.notifications),
                stringResource(if (state.notificationsAllowed) R.string.on else R.string.off),
            ) {
                onFix(ReadinessFix.NOTIFICATIONS)
            }
            ReadinessRow(
                stringResource(R.string.battery),
                stringResource(if (state.batteryUnrestricted) R.string.unrestricted else R.string.optimized),
            ) {
                onFix(ReadinessFix.BATTERY)
            }
            ReadinessRow(
                stringResource(R.string.notification_access),
                stringResource(if (state.notificationAccess) R.string.on else R.string.off),
            ) {
                onFix(ReadinessFix.SESSIONS)
            }
            ReadinessExtraRows(state, onFix)
            val result = readinessResult(state)
            ListItem(supportingContent = { Text(stringResource(result)) }) {
                Text(stringResource(R.string.last_test))
            }
        }
    }
}

@Composable
private fun ReadinessRow(title: String, status: String, onClick: () -> Unit) {
    ListItem(onClick = onClick, supportingContent = { Text(status) }) { Text(title) }
}

private fun readinessResult(state: HomeUiState): Int = when (state.lastTest?.status) {
    null -> R.string.test_not_run

    StopStatus.STOPPED -> if (state.lastTest.localOnly) {
        R.string.test_local_stopped
    } else {
        R.string.test_stopped
    }

    StopStatus.NOTHING_PLAYING -> R.string.finished_quiet

    else -> R.string.test_failed
}

@Composable
private fun ReadinessExtraRows(state: HomeUiState, onFix: (ReadinessFix) -> Unit) {
    Column {
        if (state.interrupted) {
            ReadinessRow(stringResource(R.string.timer_interrupted), stringResource(R.string.timer_interrupted_help)) {
                onFix(ReadinessFix.BATTERY)
            }
        }
        if (state.degradedReason != null && state.exactAlarmsAllowed) {
            ReadinessRow(
                stringResource(R.string.runtime_attention),
                stringResource(R.string.runtime_attention_help),
            ) {
                onFix(ReadinessFix.RUNTIME)
            }
        }
        if (state.settings.screenOffAdminRevoked) {
            ReadinessRow(stringResource(R.string.admin_revoked), stringResource(R.string.admin_revoked_help)) {
                onFix(ReadinessFix.ADMIN)
            }
        }
    }
}
