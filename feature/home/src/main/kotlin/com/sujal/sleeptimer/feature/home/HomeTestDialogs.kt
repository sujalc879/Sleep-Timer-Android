package com.sujal.sleeptimer.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sujal.sleeptimer.core.model.StopMethod
import com.sujal.sleeptimer.core.model.StopResult
import com.sujal.sleeptimer.core.model.StopStatus

@Composable
internal fun TestDialogs(
    testIntro: Boolean,
    onIntroDismiss: () -> Unit,
    onStartTest: () -> Unit,
    testResult: Boolean,
    onResultDismiss: () -> Unit,
    lastResult: StopResult?,
) {
    Box {
        if (testIntro) {
            AlertDialog(
                onDismissRequest = onIntroDismiss,
                title = { Text(stringResource(R.string.test_title)) },
                text = { Text(stringResource(R.string.test_intro)) },
                confirmButton = { TextButton(onClick = onStartTest) { Text(stringResource(R.string.test_start)) } },
                dismissButton = { TextButton(onClick = onIntroDismiss) { Text(stringResource(R.string.cancel)) } },
            )
        }
        if (testResult) {
            AlertDialog(
                onDismissRequest = onResultDismiss,
                title = { Text(stringResource(testTitle(lastResult))) },
                text = { Text(stringResource(testDescription(lastResult))) },
                confirmButton = { TextButton(onClick = onResultDismiss) { Text(stringResource(R.string.done)) } },
            )
        }
    }
}

private fun testTitle(result: StopResult?): Int = when (result?.status) {
    StopStatus.STOPPED -> if (result.localOnly) R.string.test_local_stopped else R.string.test_stopped
    StopStatus.NOTHING_PLAYING -> R.string.finished_quiet
    StopStatus.INTERRUPTED -> R.string.finished_interrupted
    else -> R.string.test_failed
}

private fun testDescription(result: StopResult?): Int = when {
    result?.status == StopStatus.NOTHING_PLAYING -> R.string.test_intro
    result?.status != StopStatus.STOPPED -> R.string.test_fix
    result.method == StopMethod.MEDIA_SESSION -> R.string.test_method_session
    result.method == StopMethod.AUDIO_FOCUS -> R.string.test_method_focus
    else -> R.string.test_method_key
}
