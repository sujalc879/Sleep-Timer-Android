package com.sujal.sleeptimer.core.timer

import com.google.common.truth.Truth.assertThat
import com.sujal.sleeptimer.core.model.TimerLimits
import org.junit.Test

class SurfaceIntentsTest {
    @Test fun shortcutsUseFixedDurationsAndWidgetInputsAreBounded() {
        assertThat(
            (SurfaceIntents.parse(SurfaceIntents.START_30) as SurfaceRequest.Start).duration,
        ).isEqualTo(30 * TimerLimits.MINUTE)
        assertThat(
            (SurfaceIntents.parse(SurfaceIntents.START_60) as SurfaceRequest.Start).duration,
        ).isEqualTo(60 * TimerLimits.MINUTE)
        assertThat(SurfaceIntents.parse(SurfaceIntents.CUSTOM)).isEqualTo(SurfaceRequest.Custom)
        assertThat(SurfaceIntents.parse(SurfaceIntents.WIDGET_PRESET, 16)).isNull()
        assertThat(SurfaceIntents.parse(SurfaceIntents.WIDGET_PRESET, Int.MAX_VALUE)).isNull()
        assertThat(SurfaceIntents.parse("other.action", 30)).isNull()
        assertThat((SurfaceIntents.parse(SurfaceIntents.WIDGET_LAST) as SurfaceRequest.Start).onlyIfIdle).isTrue()
        assertThat(
            (SurfaceIntents.parse(SurfaceIntents.WIDGET_PRESET, 45) as SurfaceRequest.Start).onlyIfIdle,
        ).isFalse()
    }
}
