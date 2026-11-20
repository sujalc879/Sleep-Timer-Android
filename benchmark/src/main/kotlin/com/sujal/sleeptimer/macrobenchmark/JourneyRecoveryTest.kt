package com.sujal.sleeptimer.macrobenchmark

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.sujal.sleeptimer.performance.TimerJourney
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real snapshot/relaunch regressions; these methods collect no performance metrics. */
@RunWith(AndroidJUnit4::class)
class JourneyRecoveryTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val journey = TimerJourney(device, BuildConfig.TARGET_PACKAGE)

    @Before fun prepare() {
        journey.prepareAccess()
        journey.recoverToIdle()
    }

    @After fun cleanup() {
        journey.cleanup()
    }

    @Test fun aForceStoppedTimerIsCancelledAndStaysIdleAfterAnotherLaunch() {
        journey.startLongTimer()
        journey.stopTestPackage()
        journey.recoverToIdle()
        journey.awaitHome()
        journey.stopTestPackage()
        journey.recoverToIdle()
        journey.awaitHome()
    }

    @Test fun cleanupFromTheLauncherCancelsTheTimerAndCanBeRepeated() {
        journey.startLongTimer()
        device.pressHome()
        journey.cleanup()
        journey.cleanup()
        journey.recoverToIdle()
        journey.awaitHome()
    }

    @Test fun theNormalAppIsRejectedBeforeAnyFixtureAction() {
        assertThrows(IllegalStateException::class.java) { TimerJourney(device, "com.sujal.sleeptimer.debug") }
    }

    @Test fun cleanupCanReturnFromSettingsToIdleHome() {
        journey.openSettings()
        journey.cleanup()
        journey.recoverToIdle()
        journey.awaitHome()
    }
}
