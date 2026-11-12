package com.sujal.sleeptimer.feature.home

import com.sujal.sleeptimer.core.model.Clock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/** Display refresh only; timer deadlines remain owned by the elapsed-realtime engine. */
internal fun elapsedDisplayTicks(clock: Clock) = flow {
    while (currentCoroutineContext().isActive) {
        val now = clock.elapsedRealtime()
        emit(now)
        delay(SECOND - now.mod(SECOND))
    }
}

private const val SECOND = 1_000L
