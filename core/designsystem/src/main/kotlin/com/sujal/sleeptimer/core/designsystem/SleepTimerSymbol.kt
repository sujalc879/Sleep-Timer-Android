package com.sujal.sleeptimer.core.designsystem

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource

enum class SleepTimerSymbol(internal val drawable: Int) {
    PLAY(R.drawable.ic_play_arrow),
    STOP(R.drawable.ic_stop),
    CLOCK(R.drawable.ic_schedule),
    MOON(R.drawable.ic_bedtime),
    CHECK(R.drawable.ic_check_circle),
    WARNING(R.drawable.ic_warning),
    TIMER(R.drawable.ic_timer),
    VIBRATION(R.drawable.ic_vibration),
    MORE(R.drawable.ic_more_horiz),
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
