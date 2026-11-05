package com.sujal.sleeptimer.surface.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.sujal.sleeptimer.core.designsystem.sleepTimerColors
import com.sujal.sleeptimer.core.model.SettingsStore
import com.sujal.sleeptimer.core.model.ThemeMode
import com.sujal.sleeptimer.core.model.TimerLimits
import com.sujal.sleeptimer.core.model.isActive
import com.sujal.sleeptimer.core.timer.TimerEngine
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetRuntime {
    fun engine(): TimerEngine
    fun settings(): SettingsStore
}

class DialWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DialWidget()
}

class DialWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ports = EntryPointAccessors.fromApplication(context.applicationContext, WidgetRuntime::class.java)
        val snapshot = ports.engine().state.first { it.initialized }.snapshot
        val settings = ports.settings().data.first()
        val light = sleepTimerColors(context, settings.dynamicColor, settings.theme == ThemeMode.DARK)
        val dark = sleepTimerColors(context, settings.dynamicColor, settings.theme != ThemeMode.LIGHT)
        val systemDark =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        val current = if (systemDark) dark else light
        val running = snapshot.isActive
        val open = Intent().setClassName(context.packageName, "com.sujal.sleeptimer.MainActivity")
            .setAction(if (running) Intent.ACTION_MAIN else ACTION_START_LAST)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val countdown = snapshot.run?.let { run ->
            RemoteViews(context.packageName, R.layout.widget_countdown).apply {
                setTextColor(R.id.countdown, current.onSurface.toArgb())
                setChronometerCountDown(R.id.countdown, true)
                setChronometer(R.id.countdown, run.endElapsed, null, true)
            }
        }
        provideContent {
            GlanceTheme(colors = ColorProviders(light, dark)) {
                WidgetContent(context, open, countdown, snapshot.selectedDurationMillis)
            }
        }
    }

    companion object {
        const val ACTION_START_LAST = "com.sujal.sleeptimer.action.WIDGET_START_LAST"
    }
}

@Composable
private fun WidgetContent(context: Context, open: Intent, countdown: RemoteViews?, duration: Long) {
    Column(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(24.dp)
            .clickable(actionStartActivity(open)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (countdown != null) {
            AndroidRemoteViews(countdown, GlanceModifier.fillMaxWidth())
        } else {
            Text(
                (duration / TimerLimits.MINUTE).toString(),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 36.sp),
            )
        }
        Text(
            context.getString(if (countdown != null) R.string.widget_running else R.string.widget_start),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
        )
    }
}

suspend fun refreshTimerWidget(context: Context) {
    DialWidget().updateAll(context)
}
