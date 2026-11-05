package com.sujal.sleeptimer.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sujal.sleeptimer.core.model.WarningMode

internal fun warningLabel(mode: WarningMode): Int = when (mode) {
    WarningMode.OFF -> R.string.off
    WarningMode.SOFT -> R.string.soft
    WarningMode.STRONG -> R.string.strong
}

@Composable
internal fun SettingsPicker(
    dialog: SettingsDialog,
    state: SettingsUiState,
    onChoose: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val extend = dialog == SettingsDialog.EXTEND
    val values = if (extend) EXTEND_MINUTES else WarningMode.entries.map { it.ordinal }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (extend) R.string.extend else R.string.warning))
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                values.forEach { value ->
                    val label = if (extend) {
                        pluralStringResource(R.plurals.minutes, value, value)
                    } else {
                        stringResource(warningLabel(WarningMode.entries[value]))
                    }
                    val selected = if (extend) {
                        state.settings.extendMillis == value * MINUTE
                    } else {
                        state.settings.warning.ordinal == value
                    }
                    Row(
                        Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = {
                            onChoose(value)
                        }).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected, null)
                        Text(label, Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

@Composable
internal fun SettingsExplanation(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(text)
                extra?.invoke()
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Suppress("MagicNumber") // Product's supported extension choices.
private val EXTEND_MINUTES = listOf(5, 10, 15, 20, 30)
private const val MINUTE = 60_000L
