package com.sujal.sleeptimer.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class FallbackColorContrastTest {
    @Test
    fun lightThemeTextMeetsNormalTextContrast() {
        assertTextContrast(SleepTimerLightColorScheme)
    }

    @Test
    fun darkThemeTextMeetsNormalTextContrast() {
        assertTextContrast(SleepTimerDarkColorScheme)
    }

    private fun assertTextContrast(scheme: ColorScheme) {
        val textRoles =
            listOf(
                TextRole("screen text", scheme.onSurface, scheme.surface),
                TextRole("screen supporting text", scheme.onSurfaceVariant, scheme.surface),
                TextRole("section heading", scheme.primary, scheme.surface),
                TextRole("settings row text", scheme.onSurface, scheme.surfaceContainer),
                TextRole("settings supporting text", scheme.onSurfaceVariant, scheme.surfaceContainer),
                TextRole("shake supporting text", scheme.onSurfaceVariant, scheme.surfaceContainerLow),
                TextRole("selected preset text", scheme.onPrimary, scheme.primary),
                TextRole("start and stop content", scheme.onPrimaryContainer, scheme.primaryContainer),
                TextRole("tonal button text", scheme.onSecondaryContainer, scheme.secondaryContainer),
            )

        textRoles.forEach { role ->
            val foregroundLuminance = role.foreground.luminance()
            val backgroundLuminance = role.background.luminance()
            val lighter = maxOf(foregroundLuminance, backgroundLuminance)
            val darker = minOf(foregroundLuminance, backgroundLuminance)
            val contrast = (lighter + LUMINANCE_OFFSET) / (darker + LUMINANCE_OFFSET)

            assertWithMessage("%s contrast ratio", role.name)
                .that(contrast)
                .isAtLeast(MINIMUM_TEXT_CONTRAST)
        }
    }

    private data class TextRole(val name: String, val foreground: Color, val background: Color)

    private companion object {
        const val LUMINANCE_OFFSET = 0.05f
        const val MINIMUM_TEXT_CONTRAST = 4.5f
    }
}
