package com.sujal.sleeptimer

import android.app.Activity
import android.app.Application
import android.view.WindowManager
import com.google.common.truth.Truth.assertThat
import com.sujal.sleeptimer.core.model.AppSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = Application::class)
class WindowAppearanceTest {
    @Test fun runningHomeDimsOnlyItsWindowAndRestoresTheOriginalOverride() {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val window = controller.get().window
            window.attributes = window.attributes.apply { screenBrightness = ORIGINAL_BRIGHTNESS }
            val appearance = WindowAppearance(window)
            appearance.apply(AppSettings(), dim = true)
            assertThat(window.attributes.screenBrightness).isEqualTo(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF)
            assertThat(window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON).isEqualTo(0)
            appearance.apply(AppSettings(), dim = false)
            assertThat(window.attributes.screenBrightness).isEqualTo(ORIGINAL_BRIGHTNESS)
            appearance.apply(AppSettings(dimWhileRunning = false), dim = true)
            assertThat(window.attributes.screenBrightness).isEqualTo(ORIGINAL_BRIGHTNESS)
            appearance.restoreBrightness()
            assertThat(window.attributes.screenBrightness).isEqualTo(ORIGINAL_BRIGHTNESS)
        }
    }
    private companion object {
        const val ORIGINAL_BRIGHTNESS = 0.4f
    }
}
