package com.sujal.sleeptimer.surface.widget

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.material3.ColorProviders
import com.sujal.sleeptimer.core.designsystem.sleepTimerColors
import com.sujal.sleeptimer.core.model.ThemeMode

@Composable
internal fun WidgetTheme(model: WidgetPresentation, content: @Composable (WidgetPresentation) -> Unit) {
    val context = LocalContext.current
    val light = sleepTimerColors(context, model.dynamic, model.theme == ThemeMode.DARK)
    val dark = sleepTimerColors(context, model.dynamic, model.theme != ThemeMode.LIGHT)
    GlanceTheme(colors = ColorProviders(light, dark)) { content(model) }
}

internal fun widgetIsDark(context: android.content.Context, model: WidgetPresentation): Boolean = when (model.theme) {
    ThemeMode.DARK -> true

    ThemeMode.LIGHT -> false

    ThemeMode.SYSTEM ->
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
}
