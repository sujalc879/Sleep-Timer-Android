package com.sujal.sleeptimer.feature.home

import com.google.common.truth.Truth.assertThat
import com.sujal.sleeptimer.core.model.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DisplayTicksTest {
    @Test fun displayAlignsToElapsedSecondsAndStopsReadingWhenCollectionEnds() = runTest {
        var reads = 0
        val clock = object : Clock {
            override fun elapsedRealtime(): Long {
                reads++
                return testScheduler.currentTime + 250
            }
            override fun wallTimeMillis(): Long = error("Display ticker must not use wall time")
            override fun bootCount(): Int = 1
        }
        val values = mutableListOf<Long>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            elapsedDisplayTicks(clock).collect { values += it }
        }
        advanceTimeBy(749)
        assertThat(values).containsExactly(250L)
        advanceTimeBy(1)
        runCurrent()
        assertThat(values).containsExactly(250L, 1_000L).inOrder()
        advanceTimeBy(1_000)
        runCurrent()
        assertThat(values).containsExactly(250L, 1_000L, 2_000L).inOrder()
        collector.cancel()
        runCurrent()
        val previousReads = reads
        advanceTimeBy(60_000)
        assertThat(reads).isEqualTo(previousReads)
    }
}
