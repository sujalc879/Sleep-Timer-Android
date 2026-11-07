package com.sujal.sleeptimer.surface.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.sujal.sleeptimer.core.designsystem.sleepTimerColors
import com.sujal.sleeptimer.core.model.TimerLimits
import com.sujal.sleeptimer.core.timer.SurfaceIntents
import java.text.NumberFormat
import kotlin.math.min

@Composable
internal fun DialContent(model: WidgetPresentation) {
    val context = LocalContext.current
    val size = LocalSize.current
    val scheme = sleepTimerColors(context, model.dynamic, widgetIsDark(context, model))
    val reserve = if (context.resources.configuration.fontScale > LARGE_FONT) LARGE_LABEL else LABEL_HEIGHT
    val diameter = min(size.width.value - OUTER_PADDING, size.height.value - reserve).coerceAtLeast(MIN_DIAMETER)
    val pixels = (diameter * context.resources.displayMetrics.density).toInt()
    val ring =
        remember(pixels, model.ringFraction, scheme) {
            widgetRing(pixels, model.ringFraction, scheme.primary.toArgb(), scheme.secondaryContainer.toArgb())
        }
    val open = activityIntent(context, if (model.active) Intent.ACTION_MAIN else SurfaceIntents.WIDGET_LAST)
    Column(
        widgetContainer(model).clickable(actionStartActivity(open)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(GlanceModifier.size(diameter.dp), contentAlignment = Alignment.Center) {
            Image(ImageProvider(ring), null, GlanceModifier.fillMaxSize())
            AndroidRemoteViews(
                widgetCounter(context, model, scheme.onSurface.toArgb()),
                GlanceModifier.size(
                    (
                        diameter *
                            HOLE_FRACTION
                        ).dp,
                ),
            )
        }
        Text(
            context.getString(if (model.active) R.string.widget_running else R.string.widget_start),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            maxLines = 2,
        )
    }
}

@Composable
internal fun PresetsContent(model: WidgetPresentation) {
    val context = LocalContext.current
    val number = NumberFormat.getIntegerInstance(context.resources.configuration.locales[0])
    val selected = if (model.active) model.totalDuration else model.selected
    Row(widgetContainer(model).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        val rtl = context.resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL
        PRESETS.forEachIndexed { index, minutes ->
            val checked = selected == minutes * TimerLimits.MINUTE
            val background = when {
                checked -> R.drawable.widget_pill
                index == 0 -> if (rtl) R.drawable.widget_trailing else R.drawable.widget_leading
                index == PRESETS.lastIndex -> if (rtl) R.drawable.widget_leading else R.drawable.widget_trailing
                else -> R.drawable.widget_middle
            }
            val open = activityIntent(context, SurfaceIntents.WIDGET_PRESET).putExtra(SurfaceIntents.MINUTES, minutes)
            Box(
                GlanceModifier.defaultWeight().height(56.dp).background(
                    ImageProvider(background),
                    colorFilter = ColorFilter.tint(
                        if (checked) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer,
                    ),
                )
                    .clickable(actionStartActivity(open)).semantics {
                        contentDescription =
                            context.resources.getQuantityString(
                                if (checked) R.plurals.widget_selected_minutes else R.plurals.widget_start_minutes,
                                minutes,
                                minutes,
                            )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    number.format(minutes),
                    style = TextStyle(
                        fontSize = 18.sp,
                        color = if (checked) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSecondaryContainer,
                    ),
                )
            }
            if (index != PRESETS.lastIndex) Spacer(GlanceModifier.width(2.dp))
        }
    }
}

@Composable
private fun widgetContainer(model: WidgetPresentation): GlanceModifier {
    val context = LocalContext.current
    val light = sleepTimerColors(context, model.dynamic, model.theme == com.sujal.sleeptimer.core.model.ThemeMode.DARK)
    val dark = sleepTimerColors(context, model.dynamic, model.theme != com.sujal.sleeptimer.core.model.ThemeMode.LIGHT)
    return GlanceModifier.fillMaxSize().appWidgetBackground()
        .background(
            ImageProvider(R.drawable.widget_container),
            colorFilter = ColorFilter.tint(ColorProvider(light.surfaceContainer, dark.surfaceContainer)),
        )
        .cornerRadius(R.dimen.widget_corner_radius)
}

internal fun activityIntent(context: Context, action: String): Intent =
    Intent().setClassName(context.packageName, "com.sujal.sleeptimer.MainActivity")
        .setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

internal fun widgetCounter(context: Context, model: WidgetPresentation, color: Int): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_countdown).apply {
        setTextColor(R.id.countdown, color)
        setContentDescription(
            R.id.countdown,
            if (model.active) {
                null
            } else {
                context.resources.getQuantityString(
                    R.plurals.widget_minutes,
                    (model.selected / TimerLimits.MINUTE).toInt(),
                    model.selected / TimerLimits.MINUTE,
                )
            },
        )
        setChronometerCountDown(R.id.countdown, true)
        val counting = model.active && model.endElapsed > model.renderElapsed
        setChronometer(R.id.countdown, model.endElapsed, null, counting)
        if (!counting) {
            setTextViewText(
                R.id.countdown,
                if (model.active) {
                    "0:00"
                } else {
                    NumberFormat.getIntegerInstance(
                        context.resources.configuration.locales[0],
                    ).format(model.selected / TimerLimits.MINUTE)
                },
            )
        }
    }

@Suppress("MagicNumber") // Product's widget presets.
internal val PRESETS = listOf(15, 30, 45, 60)
private const val LARGE_FONT = 1.5f
private const val LARGE_LABEL = 72f
private const val LABEL_HEIGHT = 48f
private const val OUTER_PADDING = 24f
private const val MIN_DIAMETER = 28f
private const val HOLE_FRACTION = 0.7f
