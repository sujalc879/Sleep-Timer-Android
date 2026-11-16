package com.sujal.sleeptimer.feature.home

import com.sujal.sleeptimer.core.model.TimerLimits
import java.text.NumberFormat
import java.util.Locale

/** Own one per dial/locale on the UI thread; NumberFormat instances are mutable. */
internal class CountdownFormatter(locale: Locale) {
    private val fields = NumberFormat.getIntegerInstance(locale).apply {
        isGroupingUsed = false
        minimumIntegerDigits = 2
    }
    private val hours by lazy(LazyThreadSafetyMode.NONE) {
        NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = false }
    }

    fun format(millis: Long): String {
        val seconds = countdownSeconds(millis)
        val second = fields.format(seconds % SECONDS_PER_MINUTE)
        return if (seconds >= SECONDS_PER_HOUR) {
            val hour = hours.format(seconds / SECONDS_PER_HOUR)
            "$hour:${fields.format(seconds / SECONDS_PER_MINUTE % SECONDS_PER_MINUTE)}:$second"
        } else {
            "${fields.format(seconds / SECONDS_PER_MINUTE)}:$second"
        }
    }
}

internal fun countdownSeconds(millis: Long): Long =
    (millis.coerceAtLeast(0) + TimerLimits.SECOND - 1) / TimerLimits.SECOND

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3_600
