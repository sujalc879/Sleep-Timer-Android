package com.sujal.sleeptimer.feature.home

import android.app.Application
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.material3.adaptive.Posture
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
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
import com.sujal.sleeptimer.core.model.TimerPhase
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w390dp-h844dp-mdpi")
class HomeUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun cancellationAndExpiryHaveDifferentStatusText() {
        var state by mutableStateOf(homeFixture().copy(runId = "run", phase = TimerPhase.STOPPING, cancelling = true))
        rule.setContent {
            SleepTimerTheme(dynamicColor = false) { HomeScreen(state, homeActions(), {}) }
        }
        rule.onNodeWithText("Canceling timer", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Stopping playback", useUnmergedTree = true).assertDoesNotExist()
        rule.runOnIdle { state = state.copy(cancelling = false) }
        rule.onNodeWithText("Stopping playback", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Canceling timer", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun aLocaleChangeUpdatesTheRememberedCountdownDigits() {
        val base = Configuration(rule.activity.resources.configuration)
        var locale by mutableStateOf(Locale.US)
        rule.setContent {
            val configuration = remember(locale) { Configuration(base).apply { setLocale(locale) } }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                SleepTimerTheme(dynamicColor = false) {
                    HomeScreen(homeFixture().copy(durationMillis = 30 * TimerLimits.MINUTE), homeActions(), {})
                }
            }
        }
        rule.onNodeWithText("30:00", useUnmergedTree = true).assertExists()
        rule.runOnIdle { locale = Locale.forLanguageTag("ar") }
        rule.onNodeWithText("٣٠:٠٠", useUnmergedTree = true).assertExists()
        rule.runOnIdle { locale = Locale.US }
        rule.onNodeWithText("30:00", useUnmergedTree = true).assertExists()
    }

    @Test fun aClockTickUpdatesDialAndReduceBoundaryWithoutRecomposingTheScreen() {
        val live = mutableStateOf(homeFixture().copy(runId = "run", remainingMillis = 301 * TimerLimits.SECOND))
        val layout = live.value.forLayout()
        var screenCompositions = 0
        rule.setContent {
            val currentLayout by rememberHomeLayoutState(live)
            SideEffect { screenCompositions++ }
            SleepTimerTheme(dynamicColor = false) { HomeScreen(currentLayout, homeActions(), {}, liveState = live) }
        }
        rule.onNodeWithText("− 5 min").assertIsEnabled()
        val before = screenCompositions
        rule.runOnIdle { live.value = live.value.copy(remainingMillis = 300 * TimerLimits.SECOND, progress = 0.5f) }
        rule.onNodeWithText("05:00", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("− 5 min").assertIsNotEnabled()
        rule.runOnIdle {
            assertThat(screenCompositions).isEqualTo(before)
            assertThat(live.value.forLayout()).isEqualTo(layout)
        }
    }

    @Test fun anExitingRunKeepsItsLastRenderedCountdownUntilTheTransitionFinishes() {
        rule.mainClock.autoAdvance = false
        val live = mutableStateOf(
            homeFixture().copy(
                runId = "run",
                phase = TimerPhase.RUNNING,
                remainingMillis = (24 * 60 + 18) * TimerLimits.SECOND,
                progress = 0.8f,
            ),
        )
        rule.setContent {
            val layout by rememberHomeLayoutState(live)
            SleepTimerTheme(dynamicColor = false) { HomeScreen(layout, homeActions(), {}, liveState = live) }
        }
        rule.onNodeWithText("24:18", useUnmergedTree = true).assertExists()
        rule.runOnIdle {
            live.value = live.value.copy(remainingMillis = live.value.remainingMillis - TimerLimits.SECOND)
        }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(48)
        rule.onNodeWithText("24:17", useUnmergedTree = true).assertExists()
        rule.runOnIdle { live.value = homeFixture() }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(48)
        rule.onNodeWithText("24:17", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("30:00", useUnmergedTree = true).assertExists()
        rule.onNodeWithContentDescription("Stop").assertDoesNotExist()
        rule.onNodeWithContentDescription("Start").assertExists()
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithText("24:17", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithText("30:00", useUnmergedTree = true).assertExists()
    }

    @Test fun aQuickStartStopStartKeepsTheIdleExitAndReplacesTheOldRun() {
        rule.mainClock.autoAdvance = false
        val live = mutableStateOf(homeFixture())
        rule.setContent {
            val layout by rememberHomeLayoutState(live)
            SleepTimerTheme(dynamicColor = false) { HomeScreen(layout, homeActions(), {}, liveState = live) }
        }
        rule.onNodeWithText("30:00", useUnmergedTree = true).assertExists()
        rule.runOnIdle { live.value = live.value.copy(runId = "first", remainingMillis = 24 * TimerLimits.MINUTE) }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(48)
        rule.onNodeWithText("30:00", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("24:00", useUnmergedTree = true).assertExists()
        rule.onNodeWithContentDescription("Start").assertDoesNotExist()
        rule.runOnIdle { live.value = homeFixture() }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(48)
        rule.runOnIdle { live.value = live.value.copy(runId = "second", remainingMillis = 45 * TimerLimits.MINUTE) }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(48)
        rule.onNodeWithText("30:00", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("45:00", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("24:00", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithContentDescription("Stop").assertExists()
        rule.onNodeWithContentDescription("Start").assertDoesNotExist()
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithText("30:00", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithText("45:00", useUnmergedTree = true).assertExists()
    }

    @Test fun aRestartWhileRunningUsesTheNewRunAndKeepsTicking() {
        val live = mutableStateOf(homeFixture().copy(runId = "first", remainingMillis = 24 * TimerLimits.MINUTE))
        rule.setContent {
            val layout by rememberHomeLayoutState(live)
            SleepTimerTheme(dynamicColor = false) { HomeScreen(layout, homeActions(), {}, liveState = live) }
        }
        rule.onNodeWithText("24:00", useUnmergedTree = true).assertExists()
        rule.runOnIdle { live.value = live.value.copy(runId = "second", remainingMillis = 45 * TimerLimits.MINUTE) }
        rule.onNodeWithText("45:00", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("24:00", useUnmergedTree = true).assertDoesNotExist()
        rule.runOnIdle {
            live.value = live.value.copy(remainingMillis = live.value.remainingMillis - TimerLimits.SECOND)
        }
        rule.onNodeWithText("44:59", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("− 5 min").assertIsEnabled()
    }

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
        dial.performKeyInput { pressKey(Key.PageUp) }
        rule.runOnIdle { assertThat(state.durationMillis).isEqualTo(36 * TimerLimits.MINUTE) }
        dial.performKeyInput { pressKey(Key.PageDown) }
        rule.runOnIdle { assertThat(state.durationMillis).isEqualTo(31 * TimerLimits.MINUTE) }
        val actions = dial.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertThat(actions.map { it.label }).containsExactly(
            "Add one minute",
            "Remove one minute",
            "Add five minutes",
            "Remove five minutes",
        )
        rule.runOnIdle { assertThat(actions.single { it.label == "Add five minutes" }.action()).isTrue() }
        rule.runOnIdle { assertThat(state.durationMillis).isEqualTo(36 * TimerLimits.MINUTE) }
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

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun twelveHourDialStaysWithinTheNarrowScreen() {
        rule.setContent {
            SleepTimerTheme(dynamicColor = false) {
                HomeScreen(homeFixture().copy(durationMillis = TimerLimits.MAX_DURATION), homeActions(), {})
            }
        }
        val bounds = rule.onNodeWithContentDescription("Timer duration").performScrollTo()
            .fetchSemanticsNode().boundsInWindow
        assertThat(bounds.left).isAtLeast(0f)
        assertThat(bounds.right).isAtMost(320f)
        val description = rule.onNodeWithContentDescription("Timer duration").fetchSemanticsNode()
            .config[SemanticsProperties.StateDescription]
        assertThat(description).startsWith("720 minutes.")
    }

    @Test
    @Config(qualifiers = "w600dp-h900dp-mdpi")
    fun tabletopKeepsDialAboveTheHingeAndControlsBelow() {
        val posture = Posture(true, listOf(HingeInfo(Rect(0f, 440f, 600f, 460f), false, false, true, true)))
        rule.setContent {
            SleepTimerTheme(dynamicColor = false) { HomeScreen(homeFixture(), homeActions(), {}, posture = posture) }
        }
        val dial = rule.onNodeWithContentDescription("Timer duration").performScrollTo().fetchSemanticsNode()
        val start = rule.onNodeWithContentDescription("Start").performScrollTo().fetchSemanticsNode()
        assertThat(dial.boundsInWindow.bottom).isAtMost(440f)
        assertThat(start.boundsInWindow.top).isAtLeast(460f)
    }

    @Test
    @Config(qualifiers = "w840dp-h900dp-mdpi")
    fun separatingVerticalHingeIsAvoidedEvenWithRtl() {
        val posture = Posture(false, listOf(HingeInfo(Rect(412f, 0f, 428f, 900f), true, true, true, true)))
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                SleepTimerTheme(dynamicColor = false) {
                    HomeScreen(homeFixture(), homeActions(), {}, posture = posture)
                }
            }
        }
        val dial = rule.onNodeWithContentDescription("Timer duration").fetchSemanticsNode()
        val start = rule.onNodeWithContentDescription("Start").fetchSemanticsNode()
        assertThat(dial.boundsInWindow.right).isAtMost(412f)
        assertThat(start.boundsInWindow.left).isAtLeast(428f)
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
