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
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
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
            HomeContent(state, actions, Modifier.fillMaxSize().padding(padding))
        }
    }
}

@Composable
private fun HomeContent(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    val adaptive = currentWindowAdaptiveInfoV2()
    BoxWithConstraints(modifier) {
        val wide =
            maxWidth >= EXPANDED_WIDTH.dp || (maxHeight < COMPACT_HEIGHT.dp && !adaptive.windowPosture.isTabletop)
        val largeFont = LocalDensity.current.fontScale >= LARGE_FONT_SCALE
        val minimumHeight = if (wide) {
            360.dp
        } else if (largeFont) {
            920.dp
        } else {
            660.dp
        }
        val bodyHeight = maxOf(maxHeight, minimumHeight)
        val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
        val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        SharedTransitionLayout(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = state.running,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
                label = "timer-state",
            ) { running ->
                val dialModifier = Modifier.size(306.dp).sharedBounds(
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
                HomePanels(state, actions, running, wide, content, dialModifier, primaryModifier)
            }
        }
    }
}

@Composable
private fun StatusChips(state: HomeUiState, actions: HomeActions) {
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
) {
    if (wide) {
        Row(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!running) StatusChips(state, actions)
                HomeDial(state, actions, dialModifier)
            }
            Column(
                Modifier.weight(1f).widthIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                if (running) RunningDetails(state) else Presets(state, actions)
                BottomActions(state, actions, running, primaryModifier)
            }
        }
    } else {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            if (!running) StatusChips(state, actions)
            if (!running) Spacer(Modifier.weight(1f)) else Spacer(Modifier.height(8.dp))
            HomeDial(state, actions, dialModifier)
            if (running) {
                Spacer(Modifier.height(24.dp))
                RunningDetails(state)
            }
            Spacer(Modifier.weight(1f))
            if (!running) {
                Presets(state, actions)
                Spacer(Modifier.height(28.dp))
            }
            BottomActions(state, actions, running, primaryModifier)
        }
    }
}
private const val EXPANDED_WIDTH = 600

private const val LARGE_FONT_SCALE = 1.5f
