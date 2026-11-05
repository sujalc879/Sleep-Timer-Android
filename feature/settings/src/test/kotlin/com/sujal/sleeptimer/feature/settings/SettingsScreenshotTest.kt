package com.sujal.sleeptimer.feature.settings

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.sujal.sleeptimer.core.designsystem.SleepTimerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun darkSettings() = capture(true)

    @Test fun lightSettings() = capture(false)
    private fun capture(dark: Boolean) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            SleepTimerTheme(dynamicColor = false, darkTheme = dark) {
                SettingsScreen(SettingsUiState(initialized = true, hasAccelerometer = true), settingsActions(), {})
            }
        }
        rule.mainClock.advanceTimeByFrame()
        rule.onRoot().captureRoboImage("src/test/screenshots/settings-${if (dark) "dark" else "light"}.png")
    }
}
