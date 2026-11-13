package com.sujal.sleeptimer.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.sujal.sleeptimer.core.designsystem.SleepTimerHomeTopAppBar
import com.sujal.sleeptimer.core.designsystem.SleepTimerIcon
import com.sujal.sleeptimer.core.designsystem.SleepTimerSymbol

@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    posture: Posture = currentWindowAdaptiveInfoV2().windowPosture,
    liveState: State<HomeUiState>? = null,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { SleepTimerHomeTopAppBar(onSettingsClick, showSettings = !state.running) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!state.initialized) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            HomeContent(state, actions, posture, Modifier.fillMaxSize().padding(padding), liveState)
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    actions: HomeActions,
    posture: Posture,
    modifier: Modifier = Modifier,
    liveState: State<HomeUiState>? = null,
) {
    var windowPosition by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(modifier.onGloballyPositioned { windowPosition = it.positionInWindow() }) {
        val panes = posture.hingeList.firstNotNullOfOrNull {
            hingePanes(Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()), windowPosition, it)
        }
        val wide =
            maxWidth >= EXPANDED_WIDTH.dp ||
                (maxHeight < COMPACT_HEIGHT.dp && maxWidth >= 480.dp && !posture.isTabletop)
        val dialSize = minOf(306.dp, if (wide) (maxWidth - 56.dp) / 2 else maxWidth - 32.dp)
        val bodyHeight = maxOf(maxHeight, minimumHomeHeight(wide, LocalDensity.current.fontScale >= LARGE_FONT_SCALE))
        val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
        val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        SharedTransitionLayout(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = state.running,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
                label = "timer-state",
            ) { running ->
                val dialModifier = Modifier.sharedBounds(
                    rememberSharedContentState("timer-dial"),
                    this,
                    boundsTransform = { _, _ -> spatial },
                    resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
                )
                val primaryModifier = Modifier.sharedBounds(
                    rememberSharedContentState("primary-action"),
                    this,
                    boundsTransform = { _, _ -> spatial },
                )
                val current = if (running == state.running) Modifier else Modifier.clearAndSetSemantics {}
                val content = current.fillMaxWidth().verticalScroll(
                    rememberScrollState(),
                ).height(bodyHeight).padding(16.dp)
                if (panes == null) {
                    HomePanels(
                        state,
                        actions,
                        running,
                        wide,
                        content,
                        Modifier.size(dialSize).then(dialModifier),
                        primaryModifier,
                        liveState,
                    )
                } else {
                    HingeHomePanels(state, actions, running, panes, current, primaryModifier, dialModifier, liveState)
                }
            }
        }
    }
}

@Composable
internal fun StatusChips(state: HomeUiState, actions: HomeActions) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(
            onClick = actions.onReadiness,
            label = {
                Text(
                    if (state.needsAttention) {
                        pluralStringResource(R.plurals.needs_fix, state.criticalCount, state.criticalCount)
                    } else {
                        stringResource(R.string.ready)
                    },
                )
            },
            leadingIcon = {
                SleepTimerIcon(
                    if (state.needsAttention) SleepTimerSymbol.WARNING else SleepTimerSymbol.CHECK,
                    null,
                    Modifier.size(18.dp),
                    MaterialTheme.colorScheme.primary,
                )
            },
        )
        AssistChip(
            onClick = actions.onTest,
            label = { Text(stringResource(R.string.test)) },
            leadingIcon = {
                SleepTimerIcon(SleepTimerSymbol.TIMER, null, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
            },
        )
    }
}

private const val COMPACT_HEIGHT = 480

@Composable
private fun HomePanels(
    state: HomeUiState,
    actions: HomeActions,
    running: Boolean,
    wide: Boolean,
    modifier: Modifier = Modifier,
    dialModifier: Modifier = Modifier,
    primaryModifier: Modifier = Modifier,
    liveState: State<HomeUiState>? = null,
) {
    if (wide) {
        Row(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!running) StatusChips(state, actions)
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    HomeDial(
                        state,
                        actions,
                        Modifier.size(minOf(306.dp, maxWidth, maxHeight)).then(dialModifier),
                        liveState,
                    )
                }
            }
            Column(
                Modifier.weight(1f).widthIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                if (running) RunningDetails(state) else Presets(state, actions)
                BottomActions(state, actions, running, primaryModifier, liveState)
            }
        }
    } else {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            if (!running) StatusChips(state, actions)
            if (!running) Spacer(Modifier.weight(1f)) else Spacer(Modifier.height(8.dp))
            HomeDial(state, actions, dialModifier, liveState)
            if (running) {
                Spacer(Modifier.height(24.dp))
                RunningDetails(state)
            }
            Spacer(Modifier.weight(1f))
            if (!running) {
                Presets(state, actions)
                Spacer(Modifier.height(28.dp))
            }
            BottomActions(state, actions, running, primaryModifier, liveState)
        }
    }
}
private const val EXPANDED_WIDTH = 600

private const val LARGE_FONT_SCALE = 1.5f

private fun minimumHomeHeight(wide: Boolean, largeFont: Boolean) = when {
    wide && largeFont -> 600.dp
    wide -> 0.dp
    largeFont -> 920.dp
    else -> 660.dp
}
