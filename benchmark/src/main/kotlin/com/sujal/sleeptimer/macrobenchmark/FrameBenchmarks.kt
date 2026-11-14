package com.sujal.sleeptimer.macrobenchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.sujal.sleeptimer.performance.TimerJourney
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrameBenchmarks {
    @get:Rule val rule = MacrobenchmarkRule()
    private val journey =
        TimerJourney(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()), BuildConfig.TARGET_PACKAGE)

    @Before fun prepare() {
        journey.prepareAccess()
    }

    @After fun cleanup() {
        journey.stopTestPackage()
    }

    @Test fun dialDrag() = frames(setup = { journey.awaitHome() }) { journey.dragDial() }

    @Test fun settingsScroll() = frames(setup = { journey.openSettings() }) { journey.scrollSettings() }

    @Test fun countdownAndWave() = frames(setup = { journey.startLongTimer() }) {
        try {
            journey.observeWave()
            journey.extendAndReduce()
        } finally {
            journey.cancelIfRunning()
        }
    }

    @Test fun steadyCountdownAndWave() = frames(setup = {
        journey.startLongTimer()
        journey.settleHomeTransition()
    }) {
        journey.observeWave()
    }

    private fun frames(setup: MacrobenchmarkScope.() -> Unit, measure: MacrobenchmarkScope.() -> Unit) {
        try {
            rule.measureRepeated(
                packageName = BuildConfig.TARGET_PACKAGE,
                metrics = listOf(FrameTimingMetric()),
                compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
                iterations = ITERATIONS,
                setupBlock = {
                    journey.cancelIfRunning()
                    killProcess()
                    pressHome()
                    startActivityAndWait()
                    setup()
                },
                measureBlock = measure,
            )
        } finally {
            journey.cancelIfRunning()
        }
    }

    private companion object {
        const val ITERATIONS = 5
    }
}
