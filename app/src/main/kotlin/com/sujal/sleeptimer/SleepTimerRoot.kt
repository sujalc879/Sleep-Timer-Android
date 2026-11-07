package com.sujal.sleeptimer

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sujal.sleeptimer.core.model.ThemeMode
import com.sujal.sleeptimer.feature.home.HomeRequest
import com.sujal.sleeptimer.feature.settings.SettingsUiState

@Composable
fun SleepTimerRoot(
    state: SettingsUiState,
    onRefresh: () -> Unit,
    homeContent: @Composable (onSettings: () -> Unit, request: HomeRequest, onHandled: () -> Unit) -> Unit,
    settingsContent: @Composable (onBack: () -> Unit, onNightly: () -> Unit, onTest: () -> Unit) -> Unit,
    appearance: WindowAppearance,
    onReady: () -> Unit,
    externalNavigation: Long = 0L,
) {
    var homeVisible by remember { mutableStateOf(true) }
    var request by rememberSaveable { mutableStateOf(HomeRequest.NONE) }
    val owner = LocalLifecycleOwner.current
    androidx.compose.runtime.LaunchedEffect(state.initialized) { if (state.initialized) onRefresh() }
    val dim = state.initialized && state.running && homeVisible
    SideEffect {
        if (state.initialized) {
            appearance.apply(state.settings, dim)
            onReady()
        }
    }
    DisposableEffect(owner, appearance, state.settings, dim) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) appearance.apply(state.settings, dim)
            if (event == Lifecycle.Event.ON_PAUSE) appearance.restoreBrightness()
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            appearance.restoreBrightness()
        }
    }
    val dark = when (state.settings.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    SleepTimerApp(
        homeContent = { onSettings ->
            homeContent(onSettings, request) { request = HomeRequest.NONE }
        },
        settingsContent = { back ->
            settingsContent(back, {
                request = HomeRequest.NIGHTLY
                back()
            }, {
                request = HomeRequest.TEST
                back()
            })
        },
        onHomeVisible = { homeVisible = it },
        externalNavigation = externalNavigation,
        darkTheme = dark,
        dynamicColor = state.settings.dynamicColor,
    )
}
