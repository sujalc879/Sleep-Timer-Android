package com.sujal.sleeptimer

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.sujal.sleeptimer.core.designsystem.SleepTimerTheme
import kotlinx.serialization.Serializable

@Serializable
internal data object Home : NavKey

@Serializable
internal data object Settings : NavKey

@Composable
fun SleepTimerApp(
    homeContent: @Composable (onSettings: () -> Unit) -> Unit,
    settingsContent: @Composable (onBack: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    onHomeVisible: (Boolean) -> Unit = {},
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    externalNavigation: Long = 0L,
) {
    val backStack = rememberNavBackStack(Home)
    androidx.compose.runtime.LaunchedEffect(externalNavigation) {
        if (externalNavigation > 0) while (backStack.size > 1) backStack.removeLastOrNull()
    }
    SideEffect { onHomeVisible(backStack.lastOrNull() == Home) }
    val navigateBack: () -> Unit = {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
    }
    SleepTimerTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            NavDisplay(
                backStack = backStack,
                onBack = navigateBack,
                entryProvider = entryProvider {
                    entry<Home> {
                        homeContent {
                            if (backStack.lastOrNull() != Settings) backStack.add(Settings)
                        }
                    }
                    entry<Settings> { settingsContent(navigateBack) }
                },
            )
        }
    }
}
