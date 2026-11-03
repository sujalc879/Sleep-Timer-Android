package com.sujal.sleeptimer

import android.app.Application
import android.graphics.Color
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertWithMessage
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 35], application = Application::class)
class LaunchThemeTest {
    @Test
    @Config(qualifiers = "notnight")
    fun launchBackgroundMatchesDynamicLightSurface() {
        val context = ApplicationProvider.getApplicationContext<Application>()

        assertSurfaceMatches(
            dynamicLightColorScheme(context).surface.toArgb(),
            context.getColor(R.color.sleeptimer_launch_surface),
        )
    }

    @Test
    @Config(qualifiers = "night")
    fun launchBackgroundMatchesDynamicDarkSurface() {
        val context = ApplicationProvider.getApplicationContext<Application>()

        assertSurfaceMatches(
            dynamicDarkColorScheme(context).surface.toArgb(),
            context.getColor(R.color.sleeptimer_launch_surface),
        )
    }

    private fun assertSurfaceMatches(expected: Int, actual: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            assertEquals(expected, actual)
            return
        }

        // Framework and Compose CAM implementations can quantize one RGB channel differently.
        assertEquals(Color.alpha(expected), Color.alpha(actual))
        listOf(
            Color.red(expected) to Color.red(actual),
            Color.green(expected) to Color.green(actual),
            Color.blue(expected) to Color.blue(actual),
        ).forEach { (expectedChannel, actualChannel) ->
            assertWithMessage("API 31-33 CAM surface channel difference")
                .that(abs(expectedChannel - actualChannel))
                .isAtMost(1)
        }
    }
}
