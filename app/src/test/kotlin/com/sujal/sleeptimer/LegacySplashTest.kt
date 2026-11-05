package com.sujal.sleeptimer

import android.app.Application
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.sujal.sleeptimer.core.designsystem.sleepTimerColors
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], application = Application::class, qualifiers = "notnight")
class LegacySplashTest {
    @Test fun olderAndroidNeverStartsWithALightSplashBeforeDarkPreferencesLoad() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val attributes = context.obtainStyledAttributes(
            R.style.Theme_SleepTimer_Starting,
            intArrayOf(androidx.core.splashscreen.R.attr.windowSplashScreenBackground),
        )
        try {
            assertThat(
                attributes.getColor(0, 0),
            ).isEqualTo(sleepTimerColors(context, dynamic = false, dark = true).surface.toArgb())
        } finally {
            attributes.recycle()
        }
    }
}
