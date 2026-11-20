package com.sujal.sleeptimer.macrobenchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
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
class StartupBenchmarks {
    @get:Rule val rule = MacrobenchmarkRule()
    private val journey =
        TimerJourney(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()), BuildConfig.TARGET_PACKAGE)

    @Before fun prepare() {
        journey.prepareAccess()
        journey.recoverToIdle()
    }

    @After fun cleanup() {
        journey.cleanup()
    }

    @Test fun coldWithoutProfile() = cold(CompilationMode.None())

    @Test fun coldWithRequiredProfile() = cold(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun cold(compilation: CompilationMode) = rule.measureRepeated(
        packageName = BuildConfig.TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilation,
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }

    private companion object {
        const val ITERATIONS = 10
    }
}
