package com.sujal.sleeptimer.surface.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.PreviewSizeMode
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState

class DialWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DialWidget()
}

class DialWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(DpSize(110.dp, 110.dp), DpSize(180.dp, 180.dp), DpSize(260.dp, 260.dp)),
    )
    override val previewSizeMode: PreviewSizeMode = SizeMode.Responsive(setOf(DpSize(180.dp, 180.dp)))
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = currentWidgetPresentation(context)
        provideContent {
            WidgetTheme(currentState<Preferences>().readPresentation(initial)) { model -> DialContent(model) }
        }
    }
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { WidgetTheme(WidgetPresentation(dynamic = false)) { DialContent(it) } }
    }
    companion object {
        const val ACTION_START_LAST = "com.sujal.sleeptimer.action.WIDGET_START_LAST"
    }
}

class PresetsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PresetsWidget()
}

class PresetsWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(DpSize(240.dp, 80.dp), DpSize(320.dp, 80.dp), DpSize(440.dp, 110.dp)),
    )
    override val previewSizeMode: PreviewSizeMode = SizeMode.Responsive(setOf(DpSize(320.dp, 80.dp)))
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = currentWidgetPresentation(context)
        provideContent { WidgetTheme(currentState<Preferences>().readPresentation(initial)) { PresetsContent(it) } }
    }
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { WidgetTheme(WidgetPresentation(dynamic = false)) { PresetsContent(it) } }
    }
}
