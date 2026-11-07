package com.sujal.sleeptimer.feature.home

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sujal.sleeptimer.core.timer.SurfaceEvent
import com.sujal.sleeptimer.core.timer.SurfaceEventKind
import kotlinx.coroutines.flow.Flow

@Composable
internal fun ObserveSurfaceEvent(
    events: Flow<SurfaceEvent?>,
    onCustom: () -> Unit,
    onUndo: (String) -> Unit,
    snackbar: SnackbarHostState,
    onConsumed: (String) -> Unit,
) {
    val event by events.collectAsStateWithLifecycle(initialValue = null)
    val resources = LocalResources.current
    LaunchedEffect(event) {
        val value = event ?: return@LaunchedEffect
        when (value.kind) {
            SurfaceEventKind.CUSTOM -> onCustom()

            SurfaceEventKind.FAILED -> snackbar.showSnackbar(resources.getString(R.string.command_failed))

            SurfaceEventKind.REPLACED -> {
                val result = snackbar.showSnackbar(
                    resources.getString(R.string.timer_replaced),
                    resources.getString(R.string.undo),
                )
                if (result == SnackbarResult.ActionPerformed) value.runId?.let(onUndo)
            }
        }
        onConsumed(value.id)
    }
}
