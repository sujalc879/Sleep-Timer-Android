package com.sujal.sleeptimer.core.designsystem

import android.text.format.DateFormat
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable
fun SleepTimerTimePicker(
    hour: Int,
    minute: Int,
    keyboard: Boolean,
    onChange: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberTimePickerState(hour, minute, DateFormat.is24HourFormat(LocalContext.current))
    val change by rememberUpdatedState(onChange)
    LaunchedEffect(state.hour, state.minute) { change(state.hour, state.minute) }
    if (keyboard) TimeInput(state, modifier) else TimePicker(state, modifier)
}
