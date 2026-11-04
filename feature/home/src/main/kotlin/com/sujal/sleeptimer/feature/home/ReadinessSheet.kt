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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sujal.sleeptimer.core.model.StopStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReadinessSheet(state: HomeUiState, onDismiss: () -> Unit, onFix: (ReadinessFix) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.readiness_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(8.dp))
            ReadinessRow(stringResource(R.string.exact_alarms), stringResource(if (state.exactAlarmsAllowed) R.string.allowed else R.string.off)) { onFix(ReadinessFix.EXACT) }
            ReadinessRow(stringResource(R.string.notifications), stringResource(if (state.notificationsAllowed) R.string.on else R.string.off)) { onFix(ReadinessFix.NOTIFICATIONS) }
            ReadinessRow(stringResource(R.string.battery), stringResource(if (state.batteryUnrestricted) R.string.unrestricted else R.string.optimized)) { onFix(ReadinessFix.BATTERY) }
            ReadinessRow(stringResource(R.string.notification_access), stringResource(if (state.notificationAccess) R.string.on else R.string.off)) { onFix(ReadinessFix.SESSIONS) }
            val result = when (state.lastTest?.status) {
                null -> R.string.test_not_run
                StopStatus.STOPPED -> if (state.lastTest.localOnly) {
                    R.string.test_local_stopped
                } else {
                    R.string.test_stopped
                }
                StopStatus.NOTHING_PLAYING -> R.string.finished_quiet
                else -> R.string.test_failed
            }
            ListItem(headlineContent = { Text(stringResource(R.string.last_test)) }, supportingContent = { Text(stringResource(result)) })
        }
    }
}

@Composable
private fun ReadinessRow(title: String, status: String, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(status) },
        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.open_settings), onClick = onClick))
}
