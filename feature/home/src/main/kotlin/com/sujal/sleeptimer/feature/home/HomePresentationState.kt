package com.sujal.sleeptimer.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy

/** Project the same snapshot the dial reads, without invalidating layout on clock ticks. */
@Composable
internal fun rememberHomeLayoutState(liveState: State<HomeUiState>): State<HomeUiState> = remember(liveState) {
    derivedStateOf(structuralEqualityPolicy()) { liveState.value.forLayout() }
}

/** An outgoing branch retains its last rendered dial instead of adopting a different run. */
@Stable
internal class HomeBranchState(initial: HomeUiState, private val source: State<HomeUiState>) : State<HomeUiState> {
    private val runId = initial.runId
    private var rendered by mutableStateOf(initial)

    override val value: HomeUiState
        get() = source.value.takeIf { it.runId == runId } ?: rendered

    // Called from the dial's SideEffect, after a successful composition.
    fun recordRendered(state: HomeUiState) {
        rendered = state
    }
}
