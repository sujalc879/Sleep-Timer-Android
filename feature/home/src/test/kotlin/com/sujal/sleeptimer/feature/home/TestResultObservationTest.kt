package com.sujal.sleeptimer.feature.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.common.truth.Truth.assertThat
import com.sujal.sleeptimer.core.model.StopResult
import com.sujal.sleeptimer.core.model.StopStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TestResultObservationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun aNewRequestCannotDisplayThePreviousCompletedTestBeforeItsServiceStarts() {
        val old = StopResult(StopStatus.STOPPED, completedElapsed = 100, isTest = true)
        var state by mutableStateOf(HomeUiState(currentResult = old))
        val displayed = mutableListOf<Boolean>()
        rule.setContent { Box { ObserveTestResult(state, true, old.completedElapsed, { displayed.add(it) }, {}) } }
        rule.waitForIdle()
        assertThat(displayed).isEmpty()
        rule.runOnIdle { state = state.copy(runId = "new-test", currentResult = null) }
        rule.waitForIdle()
        assertThat(displayed).isEmpty()
        rule.runOnIdle { state = state.copy(runId = null, currentResult = old.copy(completedElapsed = 10_100)) }
        rule.waitForIdle()
        assertThat(displayed).containsExactly(true)
    }
}
