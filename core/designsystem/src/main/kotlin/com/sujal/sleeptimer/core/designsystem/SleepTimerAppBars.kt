package com.sujal.sleeptimer.core.designsystem

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun SleepTimerHomeTopAppBar(onSettingsClick: () -> Unit, modifier: Modifier = Modifier) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.sleeptimer_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        modifier = modifier,
        actions = {
            IconButton(onClick = onSettingsClick, modifier = Modifier.size(48.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_tune),
                    contentDescription = stringResource(R.string.sleeptimer_open_settings),
                )
            }
        },
    )
}

@Composable
fun SleepTimerSettingsTopAppBar(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    LargeFlexibleTopAppBar(
        title = { Text(stringResource(R.string.sleeptimer_settings_title)) },
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onBackClick, modifier = Modifier.size(48.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.sleeptimer_back),
                )
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
fun sleepTimerSettingsScrollBehavior(): TopAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

/** Shares the app bar's nested scroll connection with the destination's scrollable content. */
@Composable
fun SleepTimerSettingsScaffold(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = sleepTimerSettingsScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SleepTimerSettingsTopAppBar(
                onBackClick = onBackClick,
                scrollBehavior = scrollBehavior,
            )
        },
        content = content,
    )
}
