package com.sujal.sleeptimer.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sujal.sleeptimer.core.model.TimerLimits

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DurationSheet(duration: Long, onDismiss: () -> Unit, onStart: (Long) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(stringResource(R.string.custom_title), style = MaterialTheme.typography.headlineSmall)
            var hours by rememberSaveable { mutableStateOf((duration / HOUR).toString()) }
            var minutes by rememberSaveable {
                mutableStateOf((duration / TimerLimits.MINUTE % MINUTES_IN_HOUR).toString())
            }
            val selected = customDuration(hours, minutes)
            DurationInputs(hours, minutes, { hours = it }, { minutes = it }) { selected?.let(onStart) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    90 to R.string.ninety_minutes,
                    120 to R.string.two_hours,
                    180 to R.string.three_hours,
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = selected == value * TimerLimits.MINUTE,
                        onClick = {
                            hours = (value / MINUTES_IN_HOUR).toString()
                            minutes =
                                (value % MINUTES_IN_HOUR).toString()
                        },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
            if (selected == null) Text(stringResource(R.string.custom_error), color = MaterialTheme.colorScheme.error)
            Button(onClick = {
                selected?.let(onStart)
            }, enabled = selected != null, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.start))
            }
        }
    }
}

@Composable
internal fun DurationInputs(
    hours: String,
    minutes: String,
    onHours: (String) -> Unit,
    onMinutes: (String) -> Unit,
    onDone: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            hours,
            onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) onHours(it) },
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.hours)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        )
        OutlinedTextField(
            minutes,
            onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) onMinutes(it) },
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.minutes)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
        )
    }
}

internal const val MINUTES_IN_HOUR = 60
internal const val HOUR = 60 * TimerLimits.MINUTE
