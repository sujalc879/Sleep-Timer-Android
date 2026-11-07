package com.sujal.sleeptimer.surface.widget

import android.content.Context
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState

suspend fun refreshTimerWidget(context: Context) {
    val manager = GlanceAppWidgetManager(context)
    val initial = currentWidgetPresentation(context)
    for (widget in listOf<GlanceAppWidget>(DialWidget(), PresetsWidget())) {
        manager.getGlanceIds(widget.javaClass).forEach { id ->
            updateAppWidgetState(context, id) { it.writePresentation(initial) }
            widget.update(context, id)
        }
    }
}

suspend fun publishWidgetPreviews(context: Context): Pair<Int, Int>? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        val manager = GlanceAppWidgetManager(context)
        val dialResult = manager.setWidgetPreviews(DialWidgetReceiver::class)
        val presetResult = manager.setWidgetPreviews(PresetsWidgetReceiver::class)
        return dialResult to presetResult
    }
    return null
}

fun widgetProviderComponent(context: Context, presets: Boolean): android.content.ComponentName =
    android.content.ComponentName(
        context,
        if (presets) PresetsWidgetReceiver::class.java else DialWidgetReceiver::class.java,
    )
