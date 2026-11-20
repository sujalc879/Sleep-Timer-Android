package com.sujal.sleeptimer.core.designsystem

import android.content.res.Resources
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import java.util.EnumMap

enum class SleepTimerSymbol(internal val drawable: Int) {
    VOLUME_DOWN(R.drawable.ic_volume_down),
    VOLUME_UP(R.drawable.ic_volume_up),
    PHONE(R.drawable.ic_smartphone),
    ADD(R.drawable.ic_add),
    NOTIFICATIONS(R.drawable.ic_notifications),
    WIDGETS(R.drawable.ic_widgets),
    PALETTE(R.drawable.ic_palette),
    CONTRAST(R.drawable.ic_contrast),
    BATTERY(R.drawable.ic_battery_full),
    CHEVRON(R.drawable.ic_chevron_right),
    PLAY(R.drawable.ic_play_arrow),
    STOP(R.drawable.ic_stop),
    CLOCK(R.drawable.ic_schedule),
    MOON(R.drawable.ic_bedtime),
    CHECK(R.drawable.ic_check_circle),
    WARNING(R.drawable.ic_warning),
    TIMER(R.drawable.ic_timer),
    VIBRATION(R.drawable.ic_vibration),
    MORE(R.drawable.ic_more_horiz),
    SHARE(R.drawable.ic_share),
}

private class SymbolVectors {
    private val vectors = EnumMap<SleepTimerSymbol, ImageVector>(SleepTimerSymbol::class.java)

    @Synchronized
    fun get(symbol: SleepTimerSymbol, resources: Resources, theme: Resources.Theme): ImageVector =
        vectors.getOrPut(symbol) { ImageVector.vectorResource(theme, resources, symbol.drawable) }
}

private val LocalSymbolVectors = staticCompositionLocalOf<SymbolVectors?> { null }

@Composable
internal fun ProvideSleepTimerSymbols(content: @Composable () -> Unit) {
    val resources = LocalResources.current
    val theme = LocalContext.current.theme
    val configuration = LocalConfiguration.current
    // Only used symbols survive short-lived screen branches; old configurations are released.
    val vectors = remember(resources, theme, configuration) { SymbolVectors() }
    CompositionLocalProvider(LocalSymbolVectors provides vectors, content = content)
}

@Composable
fun SleepTimerIcon(
    symbol: SleepTimerSymbol,
    description: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    val vectors = LocalSymbolVectors.current
    val painter = if (vectors == null) {
        painterResource(symbol.drawable)
    } else {
        rememberVectorPainter(vectors.get(symbol, LocalResources.current, LocalContext.current.theme))
    }
    Icon(
        painter,
        description,
        modifier,
        tint = if (tint ==
            Color.Unspecified
        ) {
            androidx.compose.material3.LocalContentColor.current
        } else {
            tint
        },
    )
}
