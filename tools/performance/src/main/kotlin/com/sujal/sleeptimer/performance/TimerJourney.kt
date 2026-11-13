package com.sujal.sleeptimer.performance

import android.os.Build
import android.os.SystemClock
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/** Shared instrumentation source; never a production runtime dependency. Requires English UI. */
@Suppress("TooManyFunctions") // One shared fixture owns instrumentation-only UI steps and cleanup.
class TimerJourney(private val device: UiDevice, private val target: String) {
    fun prepareAccess() {
        check(target.endsWith(".benchmark")) { "Performance tests must use the isolated benchmark package" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            device.executeShellCommand("cmd appops set $target SCHEDULE_EXACT_ALARM allow")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            device.executeShellCommand("pm grant $target android.permission.POST_NOTIFICATIONS")
        }
    }

    fun awaitHome() {
        node(By.desc("Start"))
        node(By.desc("Open settings"))
    }

    fun settingsAndBack() {
        node(By.desc("Open settings")).click()
        node(By.text("Settings"))
        scrollSettings()
        device.pressBack()
        awaitHome()
    }

    fun scrollSettings() {
        repeat(SCROLLS) {
            node(By.scrollable(true)).scroll(Direction.DOWN, SCROLL_FRACTION)
        }
        repeat(SCROLLS) {
            node(By.scrollable(true)).scroll(Direction.UP, SCROLL_FRACTION)
        }
    }

    fun openSettings() {
        node(By.desc("Open settings")).click()
        node(By.text("Settings"))
    }

    fun startLongTimer() {
        clickStartControl(By.text("60 min"))
        clickStartControl(By.desc("Start"))
        device.wait(Until.findObject(By.text("Keep running")), RATIONALE_WAIT)?.click()
        node(By.desc("Stop"))
    }

    fun extendAndReduce() {
        node(By.text("+ 10 min")).click()
        node(By.text("− 5 min")).click()
    }

    fun dragDial() {
        repeat(DRAGS) {
            node(By.text("30 min")).click()
            val bounds = node(By.desc("Timer duration")).visibleBounds
            val inset = (bounds.width() * THUMB_INSET).toInt()
            device.swipe(bounds.centerX(), bounds.bottom - inset, bounds.left + inset, bounds.centerY(), SWIPE_STEPS)
        }
    }

    fun observeWave() {
        SystemClock.sleep(WAVE_MILLIS)
    }

    fun cancelIfRunning() {
        device.findObject(By.desc("Stop"))?.let {
            it.click()
            node(By.desc("Start"))
        }
    }

    fun stopTestPackage() {
        check(target.endsWith(".benchmark"))
        device.executeShellCommand("am force-stop $target")
    }

    private fun node(selector: BySelector): UiObject2 = checkNotNull(device.wait(Until.findObject(selector), WAIT)) {
        "Performance journey target missing; use an unlocked English-language phone with no permission dialogs."
    }

    private fun clickStartControl(selector: BySelector) {
        repeat(START_ATTEMPTS) { attempt ->
            try {
                node(selector).click()
                return
            } catch (stale: StaleObjectException) {
                if (attempt == START_ATTEMPTS - 1) throw stale
            }
        }
    }

    private companion object {
        const val WAIT = 5_000L
        const val START_ATTEMPTS = 3
        const val RATIONALE_WAIT = 500L
        const val SCROLLS = 2
        const val SCROLL_FRACTION = 0.8f
        const val DRAGS = 4
        const val THUMB_INSET = 0.046f
        const val SWIPE_STEPS = 40
        const val WAVE_MILLIS = 8_000L
    }
}
