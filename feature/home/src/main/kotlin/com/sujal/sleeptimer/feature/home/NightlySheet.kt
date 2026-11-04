package com.sujal.sleeptimer.feature.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sujal.sleeptimer.core.designsystem.SleepTimerConnectedChoices
import com.sujal.sleeptimer.core.designsystem.SleepTimerTimePicker
import com.sujal.sleeptimer.core.designsystem.TimerChoice
import com.sujal.sleeptimer.core.model.AppSettings
import com.sujal.sleeptimer.core.model.TimerLimits
import java.time.DayOfWeek
import java.time.format.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NightlySheet(
    state: HomeUiState,
    onDismiss: () -> Unit,
    onSave: (AppSettings) -> Unit,
    onAccess: () -> Unit,
) {
    val original = state.settings
    var enabled by rememberSaveable { mutableStateOf(original.nightlyEnabled) }
    var hour by rememberSaveable { mutableStateOf(original.nightlyHour) }
    var minute by rememberSaveable { mutableStateOf(original.nightlyMinute) }
    var days by rememberSaveable { mutableStateOf(original.nightlyDays.toList()) }
    var duration by rememberSaveable { mutableStateOf(original.nightlyDurationMillis) }
    var keyboard by rememberSaveable { mutableStateOf(false) }
    var custom by rememberSaveable { mutableStateOf(false) }
    var hours by rememberSaveable { mutableStateOf((duration / HOUR).toString()) }
    var minutes by rememberSaveable { mutableStateOf((duration / TimerLimits.MINUTE % MINUTES_IN_HOUR).toString()) }
    var explain by rememberSaveable { mutableStateOf(false) }
    val chosen = if (custom) customDuration(hours, minutes) else duration
    val locale = LocalConfiguration.current.locales[0]
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.nightly_title), style = MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.nightly_enabled), Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            Text(stringResource(R.string.start_time), color = MaterialTheme.colorScheme.primary)
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                SleepTimerTimePicker(hour, minute, keyboard, { h, m ->
                    hour = h
                    minute = m
                })
            }
            TextButton(onClick = {
                keyboard = !keyboard
            }) { Text(stringResource(if (keyboard) R.string.clock_time else R.string.keyboard_time)) }
            Text(stringResource(R.string.repeat_days), color = MaterialTheme.colorScheme.primary)
            val choices = DayOfWeek.entries.map {
                TimerChoice(
                    it.getDisplayName(TextStyle.NARROW, locale),
                    it.value,
                    it.getDisplayName(TextStyle.FULL, locale),
                )
            }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                SleepTimerConnectedChoices(
                    choices,
                    days.toSet(),
                    { value ->
                        days =
                            if (value in days) days - value else days + value
                    },
                    stringResource(R.string.repeat_days),
                    Modifier.width(420.dp),
                )
            }
            Text(stringResource(R.string.duration), color = MaterialTheme.colorScheme.primary)
            SleepTimerConnectedChoices(
                listOf(15, 30, 45, 60).map {
                    TimerChoice(stringResource(R.string.preset, it), it)
                },
                if (custom) emptySet() else setOf((duration / TimerLimits.MINUTE).toInt()),
                {
                    duration = it * TimerLimits.MINUTE
                    custom = false
                },
                stringResource(R.string.more_presets),
            )
            TextButton(onClick = { custom = !custom }) { Text(stringResource(R.string.custom)) }
            if (custom) DurationInputs(hours, minutes, { hours = it }, { minutes = it }) {}
            if (chosen == null) Text(stringResource(R.string.custom_error), color = MaterialTheme.colorScheme.error)
            if (!state.notificationAccess) {
                Text(
                    stringResource(R.string.nightly_access_help),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = {
                if (enabled && !state.exactAlarmsAllowed) {
                    explain = true
                } else {
                    onSave(
                        original.copy(
                            nightlyEnabled = enabled,
                            nightlyHour = hour,
                            nightlyMinute = minute,
                            nightlyDays = days.toSet(),
                            nightlyDurationMillis = chosen ?: duration,
                        ),
                    )
                }
            }, modifier = Modifier.fillMaxWidth(), enabled = !enabled || (days.isNotEmpty() && chosen != null)) {
                Text(stringResource(R.string.save))
            }
        }
    }
    if (explain) {
        AlertDialog(
            onDismissRequest = { explain = false },
            title = { Text(stringResource(R.string.exact_title)) },
            text = { Text(stringResource(R.string.exact_help)) },
            confirmButton = {
                TextButton(onClick = {
                    explain = false
                    onAccess()
                }) { Text(stringResource(R.string.open_settings)) }
            },
            dismissButton = { TextButton(onClick = { explain = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
