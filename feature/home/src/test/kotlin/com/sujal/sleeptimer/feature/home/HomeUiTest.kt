package com.sujal.sleeptimer.feature.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.google.common.truth.Truth.assertThat
import com.sujal.sleeptimer.core.designsystem.SleepTimerTheme
import com.sujal.sleeptimer.core.model.TimerLimits
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w390dp-h844dp-mdpi")
class HomeUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun presetsKeyboardAndTalkBackRangeActionsChangeTheSameDuration() {
        var state by mutableStateOf(homeFixture())
        rule.setContent {
            SleepTimerTheme(dynamicColor = false) {
                HomeScreen(
                    state,
                    homeActions(
                        minutes = { state = state.copy(durationMillis = it * TimerLimits.MINUTE) },
                        preset = { state = state.copy(durationMillis = it * TimerLimits.MINUTE) },
                    ),
                    {},
                )
            }
        }
        rule.onNodeWithText("45 min").performClick().assertIsOn()
        val dial = rule.onNodeWithContentDescription("Timer duration")
        dial.performSemanticsAction(SemanticsActions.SetProgress) { assertThat(it(30f)).isTrue() }
        dial.performSemanticsAction(SemanticsActions.RequestFocus)
        dial.performKeyInput { pressKey(Key.DirectionRight) }
        rule.runOnIdle { assertThat(state.durationMillis).isEqualTo(31 * TimerLimits.MINUTE) }
        dial.performSemanticsAction(SemanticsActions.SetProgress) { assertThat(it(Float.NaN)).isFalse() }
        dial.performKeyInput { pressKey(Key.MoveEnd) }
        rule.runOnIdle { assertThat(state.durationMillis).isEqualTo(60 * TimerLimits.MINUTE) }
    }

    @Test fun draggingThumbChangesMinutesAndStartStopAndReduceHaveCorrectStates() {
        var state by mutableStateOf(homeFixture())
        var stops = 0
        rule.setContent {
            SleepTimerTheme(dynamicColor = false) {
                HomeScreen(
                    state,
                    homeActions(
                        minutes = { state = state.copy(durationMillis = it * TimerLimits.MINUTE) },
                        start = {
                            state = state.copy(runId = "run", remainingMillis = 5 * TimerLimits.MINUTE, progress = 1f)
                        },
                        stop = { stops += 1 },
                    ),
                    {},
                )
            }
        }
        rule.onNodeWithContentDescription("Timer duration").performTouchInput {
            down(Offset(width / 2f, height - 14f))
            moveTo(Offset(14f, height / 2f))
            up()
        }
        rule.runOnIdle { assertThat(state.durationMillis).isEqualTo(45 * TimerLimits.MINUTE) }
        rule.onNodeWithContentDescription("Start").performClick()
        rule.onNodeWithContentDescription("Open settings").assertDoesNotExist()
        rule.onNodeWithText("− 5 min").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Stop").performClick()
        rule.runOnIdle { assertThat(stops).isEqualTo(1) }
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun largeFontRtlCanReachTheDialAndBottomActions() {
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                SleepTimerTheme(dynamicColor = false) { HomeScreen(homeFixture(), homeActions(), {}) }
            }
        }
        rule.onNodeWithContentDescription("Timer duration").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Start").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Nightly").performScrollTo().assertIsDisplayed()
    }

    @Test fun customValidatesUpperBoundAndStartsTheExactChosenDuration() {
        var started: Long? = null
        rule.setContent {
            SleepTimerTheme(dynamicColor = false) { DurationSheet(30 * TimerLimits.MINUTE, {}, { started = it }) }
        }
        rule.onNodeWithText("Hours").performTextReplacement("12")
        rule.onNodeWithText("Minutes").performTextReplacement("1")
        rule.onNodeWithText("Start").assertIsNotEnabled()
        rule.onNodeWithText("Minutes").performTextReplacement("0")
        rule.onNodeWithText("Start").performClick()
        rule.runOnIdle { assertThat(started).isEqualTo(TimerLimits.MAX_DURATION) }
    }

    @Test
    @Config(qualifiers = "w840dp-h640dp-mdpi")
    fun expandedWindowKeepsDialAndActionsVisibleTogether() {
        rule.setContent { SleepTimerTheme(dynamicColor = false) { HomeScreen(homeFixture(), homeActions(), {}) } }
        rule.onNodeWithContentDescription("Timer duration").assertIsDisplayed()
        rule.onNodeWithContentDescription("Start").assertIsDisplayed()
        rule.onNodeWithContentDescription("Nightly").assertIsDisplayed()
    }
}

internal fun homeFixture(): HomeUiState = HomeUiState(
    initialized = true,
    exactAlarmsAllowed = true,
    notificationsAllowed = true,
    endWallMillis = java.time.Instant.parse("2026-11-04T23:42:00Z").toEpochMilli(),
)

internal fun homeActions(
    minutes: (Int) -> Unit = {},
    preset: (Int) -> Unit = {},
    start: () -> Unit = {},
    stop: () -> Unit = {},
): HomeActions = HomeActions(start, stop, minutes, {}, preset, {}, {}, {}, {}, {}, {})
