package com.sujal.sleeptimer.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sujal.sleeptimer.core.designsystem.SegmentedSettingsCard
import com.sujal.sleeptimer.core.designsystem.SleepTimerIcon
import com.sujal.sleeptimer.core.designsystem.SleepTimerSymbol

@Composable
internal fun SettingRow(
    title: String,
    icon: SleepTimerSymbol,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    trailing: @Composable () -> Unit = {},
) {
    val click = if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier
    val stack = onClick != null && LocalDensity.current.fontScale >= LARGE_FONT
    Column(modifier.then(click).fillMaxWidth().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            SleepTimerIcon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (supporting != null) {
                    Text(
                        supporting,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!stack) trailing()
        }
        if (stack) Row(Modifier.padding(start = 40.dp, top = 8.dp)) { trailing() }
    }
}

@Composable
internal fun SettingSwitch(
    title: Int,
    supporting: Int,
    icon: SleepTimerSymbol,
    checked: Boolean,
    change: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    first: Boolean = false,
    last: Boolean = false,
) {
    SegmentedSettingsCard(first, last, modifier.toggleable(checked, role = Role.Switch, onValueChange = change)) {
        SettingRow(stringResource(title), icon, supporting = stringResource(supporting), trailing = {
            Switch(checked, null)
        })
    }
}

@Composable
internal fun SettingLink(
    title: Int,
    icon: SleepTimerSymbol,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    first: Boolean = false,
    last: Boolean = false,
    supporting: String? = null,
    value: String? = null,
    enabled: Boolean = true,
) {
    SegmentedSettingsCard(first, last, modifier) {
        SettingRow(
            stringResource(
                title,
            ),
            icon,
            supporting = supporting,
            onClick = onClick,
            enabled = enabled,
            trailing = {
                if (value !=
                    null
                ) {
                    Text(
                        value,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (title == R.string.extend ||
                    title == R.string.warning
                ) {
                    SleepTimerIcon(SleepTimerSymbol.CHEVRON, null, Modifier.size(20.dp))
                }
            },
        )
    }
}

private const val LARGE_FONT = 1.5f
