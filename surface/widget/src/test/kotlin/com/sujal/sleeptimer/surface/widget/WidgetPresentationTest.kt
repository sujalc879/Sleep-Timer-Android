package com.sujal.sleeptimer.surface.widget

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import com.sujal.sleeptimer.core.model.ThemeMode
import com.sujal.sleeptimer.core.model.TimerLimits
import com.sujal.sleeptimer.core.model.TimerPhase
import org.junit.Test

class WidgetPresentationTest {
    @Test fun newerGlanceStateCarriesTheRealRunAndAppearanceIntoAnExistingSession() {
        val initial = WidgetPresentation(boot = 7, renderElapsed = 1_000, sessionEpoch = "process")
        val newer = initial.copy(
            active = true,
            runId = "run",
            phase = TimerPhase.RUNNING,
            endElapsed = 62_000,
            totalDuration = 2 * TimerLimits.MINUTE,
            renderElapsed = 2_000,
            theme = ThemeMode.DARK,
        )
        val preferences = mutablePreferencesOf().apply { writePresentation(newer) }
        val rendered = preferences.readPresentation(initial)
        assertThat(rendered).isEqualTo(newer)
        assertThat(rendered.ringFraction).isEqualTo(0.5f)
    }

    @Test fun oldProcessOldBootAndCorruptPresentationCannotOverrideFreshEngineState() {
        val initial = WidgetPresentation(boot = 7, renderElapsed = 1_000, sessionEpoch = "new")
        val preferences = mutablePreferencesOf()
        preferences.writePresentation(initial.copy(renderElapsed = 100_000, boot = 6))
        assertThat(preferences.readPresentation(initial)).isEqualTo(initial)
        preferences.writePresentation(initial.copy(renderElapsed = 100_000, sessionEpoch = "old"))
        assertThat(preferences.readPresentation(initial)).isEqualTo(initial)
        preferences[stringPreferencesKey("presentation_v1")] = "broken"
        assertThat(preferences.readPresentation(initial)).isEqualTo(initial)
    }
}
