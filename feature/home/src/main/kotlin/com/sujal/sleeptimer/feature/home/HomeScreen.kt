package com.sujal.sleeptimer.feature.home

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sujal.sleeptimer.core.designsystem.SleepTimerHomeTopAppBar

@Composable
fun HomeScreen(onSettingsClick: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { SleepTimerHomeTopAppBar(onSettingsClick = onSettingsClick) },
    ) { contentPadding ->
        Spacer(Modifier.fillMaxSize().padding(contentPadding))
    }
}
