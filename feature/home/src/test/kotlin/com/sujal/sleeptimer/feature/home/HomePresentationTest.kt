package com.sujal.sleeptimer.feature.home

import com.google.common.truth.Truth.assertThat
import com.sujal.sleeptimer.core.model.TimerLimits
import java.util.Locale
import org.junit.Test

class HomePresentationTest {
    @Test fun countdownCeilsRemainingSecondsAndDoesNotWrapHours() {
        val formatter = CountdownFormatter(Locale.US)
        assertThat(formatter.format(1)).isEqualTo("00:01")
        assertThat(formatter.format(30 * TimerLimits.MINUTE)).isEqualTo("30:00")
        assertThat(formatter.format(TimerLimits.MAX_DURATION)).isEqualTo("12:00:00")
        assertThat(formatter.format(-1)).isEqualTo("00:00")
    }

    @Test fun countdownUsesLocaleDigitsWithoutGrouping() {
        assertThat(CountdownFormatter(Locale.forLanguageTag("ar")).format(30 * TimerLimits.MINUTE))
            .isEqualTo("٣٠:٠٠")
    }

    @Test fun repeatedHourBoundaryFormattingKeepsPaddingAndLocaleDigits() {
        val formatter = CountdownFormatter(Locale.forLanguageTag("ar"))
        val hour = 60 * TimerLimits.MINUTE
        repeat(2) {
            assertThat(formatter.format(hour)).isEqualTo("١:٠٠:٠٠")
            assertThat(formatter.format(hour - TimerLimits.SECOND)).isEqualTo("٥٩:٥٩")
            assertThat(formatter.format(TimerLimits.MINUTE)).isEqualTo("٠١:٠٠")
            assertThat(formatter.format(0)).isEqualTo("٠٠:٠٠")
        }
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
