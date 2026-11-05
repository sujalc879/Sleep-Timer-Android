package com.sujal.sleeptimer.feature.settings

import com.sujal.sleeptimer.core.model.AppSettings

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val initialized: Boolean = false,
    val running: Boolean = false,
    val hasAccelerometer: Boolean = false,
    val adminActive: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val notificationAccess: Boolean = false,
    val tileAdded: Boolean = false,
)

data class SettingsActions(
    val change: ((AppSettings) -> AppSettings) -> Unit,
    val screenOff: (Boolean) -> Unit,
    val nightly: () -> Unit,
    val test: () -> Unit,
    val tile: () -> Unit,
    val widget: () -> Unit,
    val extend: () -> Unit,
    val warning: () -> Unit,
    val battery: () -> Unit,
    val sessions: () -> Unit,
)
