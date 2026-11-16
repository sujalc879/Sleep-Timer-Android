package com.sujal.sleeptimer.feature.home

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.sujal.sleeptimer.core.designsystem.DialPresentation
import com.sujal.sleeptimer.core.designsystem.SleepTimerConnectedChoices
import com.sujal.sleeptimer.core.designsystem.SleepTimerDial
import com.sujal.sleeptimer.core.designsystem.SleepTimerIcon
import com.sujal.sleeptimer.core.designsystem.SleepTimerPrimaryAction
import com.sujal.sleeptimer.core.designsystem.SleepTimerSymbol
import com.sujal.sleeptimer.core.designsystem.TimerChoice
import com.sujal.sleeptimer.core.model.StopResult
import com.sujal.sleeptimer.core.model.StopStatus
import com.sujal.sleeptimer.core.model.TimerLimits
import com.sujal.sleeptimer.core.model.TimerPhase
import java.util.Date

@Composable
internal fun HomeDial(
    state: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    liveState: State<HomeUiState>? = null,
) {
    val current = liveState?.value ?: state
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { CountdownFormatter(locale) }
    val displaySeconds = countdownSeconds(current.displayMillis)
    val countdown = remember(formatter, displaySeconds) { formatter.format(current.displayMillis) }
    val end = stringResource(R.string.ends_at, DateFormat.getTimeFormat(context).format(Date(current.endWallMillis)))
    val endLabel = when (current.phase) {
        TimerPhase.STOPPING -> stringResource(R.string.stopping)
        TimerPhase.DEFERRED -> stringResource(R.string.deferred)
        else -> end
    }
    val spokenMinutes = (current.displayMillis / TimerLimits.MINUTE).toInt().coerceAtLeast(1)
    val seconds = displaySeconds.toInt()
    val spoken = if (current.running && current.displayMillis < TimerLimits.MINUTE) {
        pluralStringResource(R.plurals.spoken_seconds, seconds, seconds)
    } else {
        pluralStringResource(R.plurals.spoken_minutes, spokenMinutes, spokenMinutes)
    }
    val message = finishedMessage(current.finished)
    SleepTimerDial(
        DialPresentation(
            minutes = (current.durationMillis / TimerLimits.MINUTE).toInt(),
            countdown = countdown,
            endLabel = if (message == null) endLabel else "",
            spokenDuration = message ?: spoken,
            controlLabel = stringResource(
                if (current.running) R.string.remaining_control else R.string.duration_control,
            ),
            increaseLabel = stringResource(R.string.increase_minute),
            decreaseLabel = stringResource(R.string.decrease_minute),
            increaseFiveLabel = stringResource(R.string.increase_five_minutes),
            decreaseFiveLabel = stringResource(R.string.decrease_five_minutes),
            running = current.running, progress = current.progress, message = message,
        ),
        actions.onMinutesChange,
        actions.onSelectionFinished,
        modifier,
    )
}

@Composable
private fun finishedMessage(result: StopResult?): String? = result?.let {
    stringResource(
        when (it.status) {
            StopStatus.STOPPED -> R.string.finished_stopped
            StopStatus.NOTHING_PLAYING -> R.string.finished_quiet
            StopStatus.INTERRUPTED -> R.string.finished_interrupted
            else -> R.string.finished_failed
        },
    )
}

@Composable
internal fun Presets(state: HomeUiState, actions: HomeActions) {
    val options = PRESET_MINUTES.map { TimerChoice(stringResource(R.string.preset, it), it) }
    SleepTimerConnectedChoices(
        options,
        setOf((state.durationMillis / TimerLimits.MINUTE).toInt()),
        actions.onPreset,
        stringResource(R.string.more_presets),
    )
}

@Composable
internal fun RunningDetails(state: HomeUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.settings.shakeToExtend && state.shakeMonitoringActive) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(
                    Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SleepTimerIcon(
                        SleepTimerSymbol.VIBRATION,
                        null,
                        Modifier.size(24.dp),
                        MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        pluralStringResource(
                            R.plurals.shake_card,
                            (state.settings.extendMillis / TimerLimits.MINUTE).toInt(),
                            (
                                state.settings.extendMillis /
                                    TimerLimits.MINUTE
                                ).toInt(),
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val steps = buildList {
            if (state.settings.fadeMillis >
                0
            ) {
                add(stringResource(R.string.pipeline_fade, state.settings.fadeMillis / TimerLimits.SECOND))
            }
            add(stringResource(R.string.pipeline_stop))
            if (state.settings.screenOff && state.screenLockAvailable) add(stringResource(R.string.pipeline_screen))
        }
        val separator = if (LocalLayoutDirection.current == LayoutDirection.Rtl) " ‹ " else " › "
        Text(
            steps.joinToString(separator),
            Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun BottomActions(
    state: HomeUiState,
    actions: HomeActions,
    running: Boolean,
    modifier: Modifier = Modifier,
    liveState: State<HomeUiState>? = null,
) {
    val reduceEnabled by remember(liveState, state.remainingMillis) {
        derivedStateOf { (liveState?.value?.remainingMillis ?: state.remainingMillis) > REDUCE_MILLIS }
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (running) {
            FilledTonalButton(
                actions.onReduce,
                Modifier.weight(1f).heightIn(min = 56.dp),
                enabled = reduceEnabled,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(stringResource(R.string.reduce), textAlign = TextAlign.Center)
            }
        } else {
            LabeledAction(
                stringResource(R.string.custom),
                SleepTimerSymbol.CLOCK,
                actions.onCustom,
                Modifier.weight(1f),
            )
        }
        SleepTimerPrimaryAction(
            running,
            stringResource(if (running) R.string.stop else R.string.start),
            if (running) actions.onStop else actions.onStart,
            modifier,
        )
        if (running) {
            FilledTonalButton(
                actions.onExtend,
                Modifier.weight(1f).heightIn(min = 56.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(
                    stringResource(R.string.extend, state.settings.extendMillis / TimerLimits.MINUTE),
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LabeledAction(
                stringResource(R.string.nightly),
                SleepTimerSymbol.MOON,
                actions.onNightly,
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LabeledAction(label: String, symbol: SleepTimerSymbol, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FilledTonalButton(onClick, Modifier.size(width = 72.dp, height = 56.dp), contentPadding = PaddingValues(0.dp)) {
            SleepTimerIcon(symbol, label, Modifier.size(24.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}
