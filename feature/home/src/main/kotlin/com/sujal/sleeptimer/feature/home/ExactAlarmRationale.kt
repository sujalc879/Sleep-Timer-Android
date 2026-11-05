package com.sujal.sleeptimer.feature.home

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
internal fun ExactAlarmRationale(show: Boolean, onDismiss: () -> Unit, onAllow: () -> Unit) {
    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.exact_rationale_title)) },
            text = { Text(stringResource(R.string.exact_rationale)) },
            confirmButton = { TextButton(onClick = onAllow) { Text(stringResource(R.string.exact_open)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.exact_later)) } },
        )
    }
}
