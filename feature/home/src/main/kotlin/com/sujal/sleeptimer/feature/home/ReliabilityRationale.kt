package com.sujal.sleeptimer.feature.home

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.sujal.sleeptimer.core.system.oemBatteryGuide

@Composable
internal fun ReliabilityRationale(item: ReadinessFix?, onDismiss: () -> Unit, onContinue: (ReadinessFix) -> Unit) {
    if (item != ReadinessFix.BATTERY && item != ReadinessFix.SESSIONS) return
    val battery = item == ReadinessFix.BATTERY
    val context = LocalContext.current
    val guide = remember { oemBatteryGuide(Build.MANUFACTURER) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (battery) R.string.battery else R.string.notification_access)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    if (battery) {
                        stringResource(R.string.battery_rationale, stringResource(guide.instructions))
                    } else {
                        stringResource(R.string.session_rationale)
                    },
                )
                if (battery) {
                    TextButton(onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, guide.url.toUri()))
                        } catch (
                            _: ActivityNotFoundException,
                        ) {
                            onDismiss()
                        } catch (_: SecurityException) {
                            onDismiss()
                        }
                    }) { Text(stringResource(R.string.manufacturer_guide)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { item?.let(onContinue) }) { Text(stringResource(R.string.open_settings)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
