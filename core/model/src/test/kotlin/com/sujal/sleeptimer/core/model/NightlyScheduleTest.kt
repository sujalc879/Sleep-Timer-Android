package com.sujal.sleeptimer.core.model

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import org.junit.Test

class NightlyScheduleTest {
    @Test fun disabledOrNoDaysHasNoOccurrence() {
        assertThat(nextNightlyOccurrence(AppSettings(), Instant.EPOCH, ZoneId.of("UTC"))).isNull()
        assertThat(
            nextNightlyOccurrence(
                AppSettings(nightlyEnabled = true, nightlyDays = emptySet()),
                Instant.EPOCH,
                ZoneId.of("UTC"),
            ),
        ).isNull()
    }

    @Test fun kolkataCrossesMidnightAndRepeatDayIsRespected() {
        val settings = AppSettings(nightlyEnabled = true, nightlyDays = setOf(5), nightlyHour = 0)
        assertThat(nextNightlyOccurrence(settings, Instant.parse("2026-11-05T18:31:00Z"), ZoneId.of("Asia/Kolkata")))
            .isEqualTo(Instant.parse("2026-11-12T18:30:00Z"))
    }

    @Test fun newYorkGapMovesForwardByGapAndOverlapRunsOnlyOnce() {
        val gap = AppSettings(nightlyEnabled = true, nightlyHour = 2, nightlyMinute = 30)
        assertThat(nextNightlyOccurrence(gap, Instant.parse("2026-03-08T00:00:00Z"), ZoneId.of("America/New_York")))
            .isEqualTo(Instant.parse("2026-03-08T07:30:00Z"))
        val overlap = gap.copy(nightlyHour = 1)
        assertThat(nextNightlyOccurrence(overlap, Instant.parse("2026-11-01T05:45:00Z"), ZoneId.of("America/New_York")))
            .isEqualTo(Instant.parse("2026-11-02T06:30:00Z"))
    }

    @Test fun londonGapAndLordHoweHalfHourGapUseZoneRules() {
        val settings = AppSettings(nightlyEnabled = true, nightlyHour = 1, nightlyMinute = 30)
        assertThat(nextNightlyOccurrence(settings, Instant.parse("2026-03-29T00:00:00Z"), ZoneId.of("Europe/London")))
            .isEqualTo(Instant.parse("2026-03-29T01:30:00Z"))
        assertThat(
            nextNightlyOccurrence(
                settings.copy(nightlyHour = 2, nightlyMinute = 15),
                Instant.parse("2026-10-03T12:00:00Z"),
                ZoneId.of("Australia/Lord_Howe"),
            ),
        )
            .isEqualTo(Instant.parse("2026-10-03T15:45:00Z"))
    }
}
