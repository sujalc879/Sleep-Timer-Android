package com.sujal.sleeptimer.feature.home

import android.app.Application
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.sujal.sleeptimer.core.designsystem.SleepTimerTheme
import com.sujal.sleeptimer.core.model.TimerLimits
import java.util.Locale
import java.util.TimeZone
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Before fun fixedPresentationEnvironment() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Settings.System.putString(rule.activity.contentResolver, Settings.System.TIME_12_24, "12")
        rule.mainClock.autoAdvance = false
    }

    @Test fun idleDark() = capture("idle-dark", homeFixture(), true)

    @Test fun idleLight() = capture("idle-light", homeFixture(), false)

    @Test fun runningDark() = capture(
        "running-dark",
        homeFixture().copy(
            runId = "snapshot-run",
            remainingMillis = (24 * 60 + 18) * TimerLimits.SECOND,
            progress = 0.81f,
            settings = homeFixture().settings.copy(screenOff = true),
            shakeMonitoringActive = true,
            screenLockAvailable = true,
        ),
        true,
    )

    private fun capture(name: String, state: HomeUiState, dark: Boolean) {
        rule.setContent {
            SleepTimerTheme(dynamicColor = false, darkTheme = dark) { HomeScreen(state, homeActions(), {}) }
        }
        rule.mainClock.advanceTimeByFrame()
        rule.onRoot().captureRoboImage("src/test/screenshots/home-$name.png")
    }
}
