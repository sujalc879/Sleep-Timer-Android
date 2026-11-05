package com.sujal.sleeptimer.core.designsystem

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme

fun sleepTimerColors(context: Context, dynamic: Boolean, dark: Boolean): ColorScheme = when {
    dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }

    dark -> SleepTimerDarkColorScheme

    else -> SleepTimerLightColorScheme
}
