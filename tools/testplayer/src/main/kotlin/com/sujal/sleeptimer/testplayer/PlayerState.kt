package com.sujal.sleeptimer.testplayer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PlayerMode { GOOD, IGNORE_FOCUS, AUTO_RESUME }

data class PlayerStatus(
    val playing: Boolean = false,
    val mode: PlayerMode = PlayerMode.GOOD,
    val silent: Boolean = true,
)

object PlayerState {
    internal val mutable = MutableStateFlow(PlayerStatus())
    val status = mutable.asStateFlow()
}
