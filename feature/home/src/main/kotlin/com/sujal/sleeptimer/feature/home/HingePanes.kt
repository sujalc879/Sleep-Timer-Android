package com.sujal.sleeptimer.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

internal data class HingePanes(val dial: Rect, val controls: Rect)

/** Hinge bounds are window coordinates; remove Scaffold's actual origin before placing panes. */
internal fun hingePanes(size: Size, origin: Offset, hinge: HingeInfo): HingePanes? {
    if (!hinge.isSeparating && !hinge.isOccluding && hinge.isFlat) return null
    val bounds = hinge.bounds.translate(-origin)
    val withinHeight = bounds.bottom > 0 && bounds.top < size.height
    val withinWidth = bounds.right > 0 && bounds.left < size.width
    val verticalSplit = hinge.isVertical && bounds.left > 0 && bounds.right < size.width
    val horizontalSplit = !hinge.isVertical && bounds.top > 0 && bounds.bottom < size.height
    return if (verticalSplit && withinHeight) {
        HingePanes(Rect(0f, 0f, bounds.left, size.height), Rect(bounds.right, 0f, size.width, size.height))
    } else if (horizontalSplit && withinWidth) {
        HingePanes(Rect(0f, 0f, size.width, bounds.top), Rect(0f, bounds.bottom, size.width, size.height))
    } else {
        null
    }
}

@Composable
internal fun HingeHomePanels(
    state: HomeUiState,
    actions: HomeActions,
    running: Boolean,
    panes: HingePanes,
    modifier: Modifier = Modifier,
    primaryModifier: Modifier = Modifier,
    dialModifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = AbsoluteAlignment.TopLeft) {
        HingePane(panes.dial, fill = true) {
            if (!running) StatusChips(state, actions)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                HomeDial(state, actions, Modifier.size(minOf(306.dp, maxWidth, maxHeight)).then(dialModifier))
            }
        }
        HingePane(panes.controls) {
            if (running) RunningDetails(state) else Presets(state, actions)
            BottomActions(state, actions, running, primaryModifier)
        }
    }
}

@Composable
private fun HingePane(bounds: Rect, fill: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val density = LocalDensity.current
    val modifier = with(density) {
        Modifier.absoluteOffset(bounds.left.toDp(), bounds.top.toDp()).size(bounds.width.toDp(), bounds.height.toDp())
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val bodyHeight = with(density) {
            maxOf(
                bounds.height.toDp(),
                if (fontScale >=
                    LARGE_FONT_SCALE
                ) {
                    640.dp
                } else {
                    360.dp
                },
            )
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .then(if (fill) Modifier.height(bodyHeight) else Modifier).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) { content() }
    }
}

private const val LARGE_FONT_SCALE = 1.5f
