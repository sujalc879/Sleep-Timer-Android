package com.sujal.sleeptimer.feature.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.material3.adaptive.Posture
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import com.sujal.sleeptimer.core.designsystem.SleepTimerTheme
import com.sujal.sleeptimer.core.model.TimerLimits
import java.util.Locale
import java.util.TimeZone
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
    private val originalZone = TimeZone.getDefault()

    @Before fun environment() {
        Locale.setDefault(rule.activity.resources.configuration.locales[0])
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        rule.mainClock.autoAdvance = false
    }

    @After fun restoreEnvironment() {
        Locale.setDefault(originalLocale)
        TimeZone.setDefault(originalZone)
    }

    @Test
    @Config(qualifiers = "en-rXA-w320dp-h640dp-mdpi")
    fun expandedPseudoLocaleAtTwoHundredPercent() {
        assertThat(rule.activity.getString(R.string.custom)).isNotEqualTo("Custom")
        capture("en-xa-320-font200-dark", homeFixture().copy(durationMillis = TimerLimits.MAX_DURATION), true, 2f)
    }

    @Test
    @Config(qualifiers = "ar-rXB-w360dp-h800dp-mdpi")
    fun rtlPseudoLocaleRunning() {
        assertThat(rule.activity.getString(R.string.stop)).isNotEqualTo("Stop")
        capture(
            "ar-xb-360-font130-light",
            homeFixture().copy(runId = "running", remainingMillis = 24 * TimerLimits.MINUTE, progress = 0.8f),
            false,
            1.3f,
        )
    }

    @Test
    @Config(qualifiers = "w600dp-h390dp-mdpi")
    fun compactLandscape() = capture("landscape-600-dark", homeFixture(), true)

    @Test
    @Config(qualifiers = "w600dp-h900dp-mdpi")
    fun tabletop() = capture(
        "tabletop-600-light",
        homeFixture(),
        false,
        posture = Posture(true, listOf(HingeInfo(Rect(0f, 440f, 600f, 460f), false, false, true, true))),
    )

    private fun capture(
        name: String,
        state: HomeUiState,
        dark: Boolean,
        fontScale: Float = 1f,
        posture: Posture = Posture(),
    ) {
        val direction = if (Locale.getDefault().language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(density, fontScale),
                LocalLayoutDirection provides direction,
            ) {
                SleepTimerTheme(dynamicColor = false, darkTheme = dark) {
                    HomeScreen(state, homeActions(), {}, posture = posture)
                }
            }
        }
        rule.mainClock.advanceTimeBy(32)
        val text = rule.onNodeWithText(
            CountdownFormatter(Locale.getDefault()).format(state.displayMillis),
            useUnmergedTree = true,
        )
        text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { get ->
            val layouts = mutableListOf<TextLayoutResult>()
            get(layouts)
            assertThat(layouts).hasSize(1)
            assertThat(layouts.single().hasVisualOverflow).isFalse()
        }
        rule.onRoot().captureRoboImage("src/test/screenshots/hardening/home-$name.png")
    }
}
