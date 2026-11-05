package com.sujal.sleeptimer.feature.settings

import android.os.Build
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.sujal.sleeptimer.core.designsystem.SegmentedSettingsCard
import com.sujal.sleeptimer.core.designsystem.SleepTimerConnectedChoices
import com.sujal.sleeptimer.core.designsystem.SleepTimerSettingsScaffold
import com.sujal.sleeptimer.core.designsystem.SleepTimerSymbol
import com.sujal.sleeptimer.core.designsystem.TimerChoice
import com.sujal.sleeptimer.core.model.ThemeMode
import java.util.Calendar
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SleepTimerSettingsScaffold(onBackClick = onBackClick, modifier = modifier) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).widthIn(max = 840.dp).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            endingSettings(state, actions)
            extendingSettings(state, actions)
            startingSettings(state, actions)
            appearanceSettings(state, actions)
            reliabilitySettings(state, actions)
        }
    }
}

private fun LazyListScope.endingSettings(state: SettingsUiState, actions: SettingsActions) {
    item { SectionTitle(R.string.ending) }
    item { FadeSetting(state, actions) }
    item {
        SettingSwitch(
            R.string.screen_off,
            R.string.screen_off_support,
            SleepTimerSymbol.PHONE,
            state.settings.screenOff,
            actions.screenOff,
        )
    }
    item {
        SettingSwitch(
            R.string.restore,
            R.string.restore_support,
            SleepTimerSymbol.VOLUME_UP,
            state.settings.restoreVolume,
            { checked -> actions.change { it.copy(restoreVolume = checked) } },
            last = true,
        )
    }
}

private fun LazyListScope.extendingSettings(state: SettingsUiState, actions: SettingsActions) {
    item { SectionTitle(R.string.extending) }
    if (state.hasAccelerometer) {
        item {
            SettingSwitch(
                R.string.shake,
                R.string.shake_support,
                SleepTimerSymbol.VIBRATION,
                state.settings.shakeToExtend,
                { checked -> actions.change { it.copy(shakeToExtend = checked) } },
                first = true,
            )
        }
    }
    item {
        val minutes = (state.settings.extendMillis / MINUTE).toInt()
        SettingLink(
            R.string.extend,
            SleepTimerSymbol.ADD,
            actions.extend,
            first = !state.hasAccelerometer,
            value = pluralStringResource(R.plurals.minutes, minutes, minutes),
        )
    }
    item {
        SettingLink(
            R.string.warning,
            SleepTimerSymbol.NOTIFICATIONS,
            actions.warning,
            last = true,
            supporting = stringResource(R.string.warning_support),
            value = stringResource(warningLabel(state.settings.warning)),
        )
    }
}

private fun LazyListScope.startingSettings(state: SettingsUiState, actions: SettingsActions) {
    item { SectionTitle(R.string.starting) }
    item {
        SegmentedSettingsCard(first = true, last = false) {
            SettingRow(
                stringResource(R.string.nightly),
                SleepTimerSymbol.MOON,
                supporting = nightlySummary(state),
                onClick = actions.nightly,
                trailing = { Switch(state.settings.nightlyEnabled, { actions.nightly() }) },
            )
        }
    }
    item {
        SettingLink(
            R.string.tile,
            SleepTimerSymbol.TIMER,
            actions.tile,
            value = stringResource(if (state.tileAdded) R.string.added else R.string.add),
        )
    }
    item {
        SettingLink(
            R.string.widget,
            SleepTimerSymbol.WIDGETS,
            actions.widget,
            last = true,
            value = stringResource(R.string.add),
        )
    }
}

private fun LazyListScope.appearanceSettings(state: SettingsUiState, actions: SettingsActions) {
    item { SectionTitle(R.string.appearance) }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        item {
            SettingSwitch(
                R.string.dynamic,
                R.string.dynamic_support,
                SleepTimerSymbol.PALETTE,
                state.settings.dynamicColor,
                { checked -> actions.change { it.copy(dynamicColor = checked) } },
                first = true,
            )
        }
    }
    item { ThemeSetting(state, actions) }
    item {
        SettingSwitch(
            R.string.dim,
            R.string.dim_support,
            SleepTimerSymbol.PHONE,
            state.settings.dimWhileRunning,
            { checked -> actions.change { it.copy(dimWhileRunning = checked) } },
            last = true,
        )
    }
}

private fun LazyListScope.reliabilitySettings(state: SettingsUiState, actions: SettingsActions) {
    item { SectionTitle(R.string.reliability) }
    item {
        SettingLink(
            R.string.test,
            SleepTimerSymbol.TIMER,
            actions.test,
            first = true,
            supporting = stringResource(R.string.test_support),
            enabled = !state.running,
        )
    }
    item {
        val status = if (state.batteryUnrestricted) R.string.unrestricted else R.string.optimized
        SettingLink(
            R.string.battery,
            SleepTimerSymbol.BATTERY,
            actions.battery,
            supporting = stringResource(R.string.battery_support),
            value = stringResource(status),
        )
    }
    item {
        val status = if (state.notificationAccess) R.string.on else R.string.off
        SettingLink(
            R.string.sessions,
            SleepTimerSymbol.NOTIFICATIONS,
            actions.sessions,
            last = true,
            supporting = stringResource(R.string.sessions_support),
            value = stringResource(status),
        )
    }
    if (state.adminActive) {
        item {
            SettingLink(
                R.string.manage_admin,
                SleepTimerSymbol.PHONE,
                { actions.screenOff(false) },
                first = true,
                last = true,
            )
        }
    }
}

@Composable
private fun SectionTitle(title: Int) {
    Text(
        stringResource(title),
        Modifier.padding(start = 16.dp, top = 30.dp, bottom = 10.dp).semantics { heading() },
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun FadeSetting(state: SettingsUiState, actions: SettingsActions) {
    var value by remember(state.settings.fadeMillis) {
        mutableFloatStateOf(state.settings.fadeMillis / MILLIS_PER_SECOND.toFloat())
    }
    val label = stringResource(R.string.fade)
    val text = if (value == 0f) {
        stringResource(R.string.off)
    } else {
        stringResource(R.string.seconds, value.roundToInt())
    }
    SegmentedSettingsCard(first = true, last = false) {
        SettingRow(
            label,
            SleepTimerSymbol.VOLUME_DOWN,
            supporting = stringResource(R.string.fade_support),
            trailing = { Text(text) },
        )
        val slider = rememberSliderState(value, steps = FADE_STEPS - 1, trackRange = 0f..MAX_FADE_SECONDS)
        slider.value = value
        Slider(
            slider,
            { value = it.roundToInt().toFloat() },
            Modifier.padding(start = 56.dp, end = 16.dp).semantics {
                contentDescription = label
                stateDescription = text
            },
            onValueChangeFinished = { actions.change { it.copy(fadeMillis = value.roundToInt() * MILLIS_PER_SECOND) } },
        )
    }
}

@Composable
private fun ThemeSetting(state: SettingsUiState, actions: SettingsActions) {
    SegmentedSettingsCard(first = Build.VERSION.SDK_INT < Build.VERSION_CODES.S, last = false) {
        SettingRow(stringResource(R.string.theme), SleepTimerSymbol.CONTRAST)
        SleepTimerConnectedChoices(
            listOf(
                TimerChoice(stringResource(R.string.light), ThemeMode.LIGHT.ordinal),
                TimerChoice(stringResource(R.string.dark), ThemeMode.DARK.ordinal),
                TimerChoice(stringResource(R.string.system), ThemeMode.SYSTEM.ordinal),
            ),
            setOf(state.settings.theme.ordinal),
            { index -> actions.change { it.copy(theme = ThemeMode.entries[index]) } },
            stringResource(R.string.theme),
            Modifier.padding(start = 56.dp, end = 16.dp, bottom = 16.dp),
        )
    }
}

@Composable
private fun nightlySummary(state: SettingsUiState): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, state.settings.nightlyHour)
        set(Calendar.MINUTE, state.settings.nightlyMinute)
    }
    val time = DateFormat.getTimeFormat(LocalContext.current).format(calendar.time)
    return stringResource(R.string.nightly_support, time)
}

private const val MINUTE = 60_000L
private const val MILLIS_PER_SECOND = 1_000L
private const val MAX_FADE_SECONDS = 60f
private const val FADE_STEPS = 12
