package com.sujal.sleeptimer.feature.home

import com.google.common.truth.Truth.assertThat
import com.sujal.sleeptimer.core.model.TimerLimits
import java.util.Locale
import org.junit.Test

class HomePresentationTest {
    @Test fun countdownCeilsRemainingSecondsAndDoesNotWrapHours() {
        assertThat(countdown(1, Locale.US)).isEqualTo("00:01")
        assertThat(countdown(30 * TimerLimits.MINUTE, Locale.US)).isEqualTo("30:00")
        assertThat(countdown(TimerLimits.MAX_DURATION, Locale.US)).isEqualTo("12:00:00")
        assertThat(countdown(-1, Locale.US)).isEqualTo("00:00")
    }

    @Test fun countdownUsesLocaleDigitsWithoutGrouping() {
        assertThat(countdown(30 * TimerLimits.MINUTE, Locale.forLanguageTag("ar"))).isEqualTo("٣٠:٠٠")
    }

    @Test fun customInputRejectsZeroOverflowMissingAndOutOfRangeValues() {
        for ((hour, minute) in listOf("0" to "0", "12" to "1", "13" to "0", "1" to "60", "" to "30")) {
            assertThat(customDuration(hour, minute)).isNull()
        }
        assertThat(customDuration("12", "0")).isEqualTo(TimerLimits.MAX_DURATION)
        assertThat(customDuration("0", "1")).isEqualTo(TimerLimits.MINUTE)
        assertThat(customDuration("٢", "٣٠")).isEqualTo(150 * TimerLimits.MINUTE)
    }
}
