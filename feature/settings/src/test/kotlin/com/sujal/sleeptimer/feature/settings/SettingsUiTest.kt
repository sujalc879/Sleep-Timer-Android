package com.sujal.sleeptimer.feature.settings

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.google.common.truth.Truth.assertThat
import com.sujal.sleeptimer.core.designsystem.SleepTimerTheme
import com.sujal.sleeptimer.core.model.AppSettings
import com.sujal.sleeptimer.core.model.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w390dp-h844dp")
class SettingsUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun switchesAndFadeCommitRealUserValues() {
        var preferences by mutableStateOf(AppSettings())
        rule.setContent {
            SleepTimerTheme(dynamicColor = false) {
                SettingsScreen(
                    SettingsUiState(preferences, hasAccelerometer = true),
                    settingsActions(change = { preferences = it(preferences) }),
                    {},
                )
            }
        }
        rule.onNodeWithText("Restore volume").performClick()
        assertThat(preferences.restoreVolume).isFalse()
        rule.onNodeWithContentDescription("Fade-out").performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        rule.waitForIdle()
        assertThat(preferences.fadeMillis).isEqualTo(0)
        rule.onNodeWithContentDescription("Fade-out").performSemanticsAction(SemanticsActions.SetProgress) { it(60f) }
        rule.waitForIdle()
        assertThat(preferences.fadeMillis).isEqualTo(60_000)
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Shake to extend"))
        rule.onNodeWithText("Shake to extend").performClick()
        assertThat(preferences.shakeToExtend).isFalse()
    }

    @Test fun themeAndReliabilityRemainReachableAtTwoHundredPercentAndRtl() {
        var preferences by mutableStateOf(AppSettings())
        var sessions = 0
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(density, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                SleepTimerTheme(dynamicColor = false, darkTheme = true) {
                    SettingsScreen(
                        SettingsUiState(preferences),
                        settingsActions(change = {
                            preferences = it(preferences)
                        }, sessions = { sessions++ }),
                        {},
                    )
                }
            }
        }
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Light"))
        rule.onNodeWithText("Light").performClick()
        assertThat(preferences.theme).isEqualTo(ThemeMode.LIGHT)
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Notification access"))
        rule.onNodeWithText("Not required to stop playback").assertIsDisplayed()
        rule.onNodeWithText("Notification access").performClick()
        assertThat(sessions).isEqualTo(1)
    }

    @Test fun noSensorHidesShakeAndRunningDisablesTheTest() {
        var tests = 0
        rule.setContent {
            SleepTimerTheme(dynamicColor = false) {
                SettingsScreen(
                    SettingsUiState(running = true, hasAccelerometer = false),
                    settingsActions(test = { tests++ }),
                    {},
                )
            }
        }
        rule.onNodeWithText("Shake to extend").assertDoesNotExist()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Run a 10-second test"))
        rule.onNodeWithText("Run a 10-second test").performClick()
        assertThat(tests).isEqualTo(0)
    }

    @Test fun diagnosticsIsReachableAndInvokesTheUserInitiatedShareAction() {
        var shares = 0
        rule.setContent {
            SleepTimerTheme(dynamicColor = false) {
                SettingsScreen(SettingsUiState(), settingsActions(diagnostics = { shares++ }), {})
            }
        }
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Share diagnostics"))
        rule.onNodeWithText("Share diagnostics").assertIsDisplayed().performClick()
        assertThat(shares).isEqualTo(1)
    }

    @Test fun extensionPickerExposesAllSupportedDurations() {
        var selected = 0
        rule.setContent {
            SleepTimerTheme(dynamicColor = false) {
                SettingsPicker(SettingsDialog.EXTEND, SettingsUiState(), { selected = it }, {})
            }
        }
        rule.onNodeWithText("30 minutes").performClick()
        assertThat(selected).isEqualTo(30)
        rule.onNodeWithText("5 minutes").performClick()
        assertThat(selected).isEqualTo(5)
    }
}

internal fun settingsActions(
    change: ((AppSettings) -> AppSettings) -> Unit = {},
    sessions: () -> Unit = {},
    test: () -> Unit = {},
    diagnostics: () -> Unit = {},
): SettingsActions = SettingsActions(change, {}, {}, test, {}, {}, {}, {}, {}, sessions, diagnostics)
