package com.sujal.sleeptimer

import android.app.UiModeManager
import android.os.Build
import android.view.Window
import android.view.WindowManager
import com.sujal.sleeptimer.core.model.AppSettings
import com.sujal.sleeptimer.core.model.ThemeMode

class WindowAppearance(private val window: Window) {
    private val original = window.attributes.screenBrightness
    private var lastMode: ThemeMode? = null

    fun apply(settings: AppSettings, dim: Boolean) {
        val brightness = if (dim &&
            settings.dimWhileRunning
        ) {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
        } else {
            original
        }
        if (window.attributes.screenBrightness != brightness) {
            window.attributes = window.attributes.apply { screenBrightness = brightness }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && settings.theme != lastMode) {
            lastMode = settings.theme
            val mode = when (settings.theme) {
                ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO

                ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES

                // AOSP maps AUTO to UI_MODE_NIGHT_UNDEFINED for the per-app override (D038).
                ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            }
            window.context.getSystemService(UiModeManager::class.java)?.setApplicationNightMode(mode)
        }
    }

    fun restoreBrightness() {
        window.attributes = window.attributes.apply { screenBrightness = original }
    }
}
