package com.sujal.sleeptimer.baselineprofile

import android.os.Build
import androidx.benchmark.macro.junit4.BaselineProfileRule
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
class BaselineProfiles {
    @get:Rule val rule = BaselineProfileRule()
    private val journey =
        TimerJourney(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()), BuildConfig.TARGET_PACKAGE)

    @Before fun prepare() {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            "Use an API 33+ connected device for profile generation"
        }
        journey.prepareAccess()
    }

    @After fun cleanup() {
        journey.stopTestPackage()
    }

    @Test fun startup() = rule.collect(BuildConfig.TARGET_PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        journey.awaitHome()
    }

    @Test fun settingsDialAndRunningTimer() = rule.collect(BuildConfig.TARGET_PACKAGE) {
        pressHome()
        startActivityAndWait()
        try {
            journey.awaitHome()
            journey.settingsAndBack()
            journey.dragDial()
            journey.startLongTimer()
            journey.observeWave()
            journey.extendAndReduce()
        } finally {
            journey.cancelIfRunning()
        }
    }
}
