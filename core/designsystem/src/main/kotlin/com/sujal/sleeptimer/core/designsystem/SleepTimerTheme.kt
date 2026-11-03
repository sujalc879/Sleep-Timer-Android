package com.sujal.sleeptimer.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Platform colors on Android 12+, with generated Tonal Spot fallback colors on older devices. */
@Composable
fun SleepTimerTheme(
    dynamicColor: Boolean = true,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> SleepTimerDarkColorScheme

            else -> SleepTimerLightColorScheme
        }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = remember { MotionScheme.expressive() },
        typography = remember { Typography() },
        content = content,
    )
}
