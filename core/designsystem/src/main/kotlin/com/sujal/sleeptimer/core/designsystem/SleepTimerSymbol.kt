package com.sujal.sleeptimer.core.designsystem

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource

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

@Composable
fun SleepTimerIcon(
    symbol: SleepTimerSymbol,
    description: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    Icon(
        painterResource(symbol.drawable),
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
