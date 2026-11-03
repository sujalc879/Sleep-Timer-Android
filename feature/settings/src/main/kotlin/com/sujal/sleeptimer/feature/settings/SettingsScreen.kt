package com.sujal.sleeptimer.feature.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sujal.sleeptimer.core.designsystem.SleepTimerSettingsScaffold

@Composable
fun SettingsScreen(onBackClick: () -> Unit, modifier: Modifier = Modifier) {
    SleepTimerSettingsScaffold(onBackClick = onBackClick, modifier = modifier) { contentPadding ->
        Spacer(Modifier.fillMaxSize().padding(contentPadding))
    }
}
