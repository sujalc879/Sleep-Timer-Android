package com.sujal.sleeptimer

import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import com.sujal.sleeptimer.surface.tile.SleepTimerTile
import com.sujal.sleeptimer.surface.widget.DialWidgetReceiver

fun MainActivity.addTimerTile(onHelp: () -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        onHelp()
        return
    }
    try {
        getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(this, SleepTimerTile::class.java),
            getString(R.string.app_name),
            Icon.createWithResource(this, com.sujal.sleeptimer.surface.tile.R.drawable.ic_tile_moon),
            mainExecutor,
        ) { result ->
            settingsRefresh()
            if (result != StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED &&
                result != StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
            ) {
                onHelp()
            }
        }
    } catch (_: RuntimeException) {
        onHelp()
    }
}

fun MainActivity.addTimerWidget(onHelp: () -> Unit) {
    val manager = getSystemService(AppWidgetManager::class.java)
    try {
        if (!manager.isRequestPinAppWidgetSupported ||
            !manager.requestPinAppWidget(ComponentName(this, DialWidgetReceiver::class.java), null, null)
        ) {
            onHelp()
        }
    } catch (_: RuntimeException) {
        onHelp()
    }
}
