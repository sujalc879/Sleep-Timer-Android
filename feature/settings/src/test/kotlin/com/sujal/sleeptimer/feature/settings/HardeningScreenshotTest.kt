package com.sujal.sleeptimer.feature.settings

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import com.sujal.sleeptimer.core.designsystem.SleepTimerTheme
import java.util.Locale
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HardeningScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val originalLocale = Locale.getDefault()

    @Before fun environment() {
        Locale.setDefault(rule.activity.resources.configuration.locales[0])
        rule.mainClock.autoAdvance = true
    }

    @After fun restoreEnvironment() {
        Locale.setDefault(originalLocale)
    }

    @Test
    @Config(qualifiers = "en-rXA-w320dp-h640dp-mdpi")
    fun expandedDiagnosticsAtTwoHundredPercent() = capture("en-xa-320-font200-dark", true, 2f)

    @Test
    @Config(qualifiers = "ar-rXB-w600dp-h900dp-mdpi")
    fun rtlDiagnostics() = capture("ar-xb-600-font130-light", false, 1.3f)

    private fun capture(name: String, dark: Boolean, fontScale: Float) {
        val label = rule.activity.getString(R.string.diagnostics)
        assertThat(label).isNotEqualTo("Share diagnostics")
        val direction = if (Locale.getDefault().language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(density, fontScale),
                LocalLayoutDirection provides direction,
            ) {
                SleepTimerTheme(dynamicColor = false, darkTheme = dark) {
                    SettingsScreen(SettingsUiState(), settingsActions(), {})
                }
            }
        }
        rule.mainClock.advanceTimeByFrame()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label))
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeByFrame()
        rule.onRoot().captureRoboImage("src/test/screenshots/hardening/settings-$name.png")
    }
}
