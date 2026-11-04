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
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
// Keep the saveable draft beside its sheet lifecycle; row sections are separate composables.
@Suppress("LongMethod")
internal fun NightlySheet(
    state: HomeUiState,
    onDismiss: () -> Unit,
    onSave: (AppSettings) -> Unit,
    onAccess: () -> Unit,
) {
    val original = state.settings
    var enabled by rememberSaveable { mutableStateOf(original.nightlyEnabled) }
    var hour by rememberSaveable { mutableIntStateOf(original.nightlyHour) }
    var minute by rememberSaveable { mutableIntStateOf(original.nightlyMinute) }
    var days by rememberSaveable { mutableStateOf(original.nightlyDays.toList()) }
    var duration by rememberSaveable { mutableLongStateOf(original.nightlyDurationMillis) }
    var keyboard by rememberSaveable { mutableStateOf(false) }
    var custom by rememberSaveable { mutableStateOf(false) }
    var hours by rememberSaveable { mutableStateOf((duration / HOUR).toString()) }
    var minutes by rememberSaveable { mutableStateOf((duration / TimerLimits.MINUTE % MINUTES_IN_HOUR).toString()) }
    var explain by rememberSaveable { mutableStateOf(false) }
    val chosen = if (custom) customDuration(hours, minutes) else duration
    androidx.compose.foundation.layout.Box {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberBottomSheetState(
                SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.nightly_title), style = MaterialTheme.typography.headlineSmall)
                val enabledLabel = stringResource(R.string.nightly_enabled)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.nightly_enabled), Modifier.weight(1f))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        modifier = Modifier.semantics {
                            contentDescription = enabledLabel
                        },
                    )
                }
                NightlyTime(hour, minute, keyboard, { h, m ->
                    hour = h
                    minute = m
                }, { keyboard = !keyboard })
                NightlyDays(days) { days = it }
                NightlyDurationFields(
                    duration,
                    custom,
                    hours,
                    minutes,
                    {
                        duration = it * TimerLimits.MINUTE
                        custom = false
                    },
                    { custom = !custom },
                    { hours = it },
                    {
                        minutes =
                            it
                    },
                )
                if (!state.notificationAccess) {
                    Text(
                        stringResource(R.string.nightly_access_help),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val draft = original.copy(
                    nightlyEnabled = enabled,
                    nightlyHour = hour,
                    nightlyMinute = minute,
                    nightlyDays = days.toSet(),
                    nightlyDurationMillis = chosen ?: duration,
                )
                NightlySaveButton(draft, chosen != null, state.exactAlarmsAllowed, onSave, { explain = true })
            }
        }
        if (explain) {
            NightlyAccessDialog({ explain = false }, {
                explain = false
                onAccess()
            })
        }
    }
}

@Composable
private fun NightlyTime(hour: Int, minute: Int, keyboard: Boolean, onChange: (Int, Int) -> Unit, onToggle: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.start_time), color = MaterialTheme.colorScheme.primary)
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            SleepTimerTimePicker(hour, minute, keyboard, onChange)
        }
        TextButton(onClick = onToggle) {
            Text(stringResource(if (keyboard) R.string.clock_time else R.string.keyboard_time))
        }
    }
}

@Composable
private fun NightlyDays(days: List<Int>, onChange: (List<Int>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val locale = LocalConfiguration.current.locales[0]
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
                { value -> onChange(if (value in days) days - value else days + value) },
                stringResource(R.string.repeat_days),
                Modifier.width(420.dp),
            )
        }
    }
}

@Composable
private fun NightlyDurationFields(
    duration: Long,
    custom: Boolean,
    hours: String,
    minutes: String,
    onPreset: (Int) -> Unit,
    onCustom: () -> Unit,
    onHours: (String) -> Unit,
    onMinutes: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        Text(stringResource(R.string.duration), color = MaterialTheme.colorScheme.primary)
        SleepTimerConnectedChoices(
            PRESET_MINUTES.map { TimerChoice(stringResource(R.string.preset, it), it) },
            if (custom) emptySet() else setOf((duration / TimerLimits.MINUTE).toInt()),
            onPreset,
            stringResource(R.string.more_presets),
        )
        TextButton(onClick = onCustom) { Text(stringResource(R.string.custom)) }
        if (custom) DurationInputs(hours, minutes, onHours, onMinutes) { keyboard?.hide() }
        if (custom &&
            customDuration(hours, minutes) == null
        ) {
            Text(stringResource(R.string.custom_error), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun NightlySaveButton(
    draft: AppSettings,
    validDuration: Boolean,
    exactAllowed: Boolean,
    onSave: (AppSettings) -> Unit,
    onExplain: () -> Unit,
) {
    Button(
        onClick = { if (draft.nightlyEnabled && !exactAllowed) onExplain() else onSave(draft) },
        modifier = Modifier.fillMaxWidth(),
        enabled =
            !draft.nightlyEnabled || (draft.nightlyDays.isNotEmpty() && validDuration),
    ) {
        Text(stringResource(R.string.save))
    }
}

@Composable
private fun NightlyAccessDialog(onDismiss: () -> Unit, onAccess: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exact_title)) },
        text = { Text(stringResource(R.string.exact_help)) },
        confirmButton = { TextButton(onClick = onAccess) { Text(stringResource(R.string.open_settings)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
