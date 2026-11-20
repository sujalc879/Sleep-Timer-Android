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
import java.util.regex.Pattern

/** Shared instrumentation source; never a production runtime dependency. Requires English UI. */
@Suppress("TooManyFunctions") // One shared fixture owns instrumentation-only UI steps and cleanup.
class TimerJourney(private val device: UiDevice, private val target: String) {
    init {
        check(target == ISOLATED_PACKAGE) { "Performance tests must use the isolated benchmark package" }
    }

    fun prepareAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            device.executeShellCommand("cmd appops set $target SCHEDULE_EXACT_ALARM allow")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            device.executeShellCommand("pm grant $target android.permission.POST_NOTIFICATIONS")
        }
    }

    fun awaitHome() {
        node(By.desc("Start").enabled(true))
        node(By.desc("Open settings"))
    }

    /** Recover the persisted timer even when failure cleanup left the launcher in front. */
    fun recoverToIdle() {
        device.executeShellCommand("am start -W -n $target/com.sujal.sleeptimer.MainActivity")
        if (node(By.desc(HOME_OR_BACK).enabled(true)).contentDescription == "Back") device.pressBack()
        if (node(By.desc(HOME_CONTROLS).enabled(true)).contentDescription == "Stop") {
            clickControl(By.desc("Stop"))
        }
        awaitHome()
    }

    fun cleanup() {
        try {
            recoverToIdle()
        } finally {
            stopTestPackage()
        }
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
        clickControl(By.text("60 min"))
        clickControl(By.desc("Start"))
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

    fun settleHomeTransition() {
        SystemClock.sleep(TRANSITION_SETTLE_MILLIS)
    }

    fun cancelIfRunning() {
        device.findObject(By.desc("Stop").pkg(target))?.let {
            it.click()
            node(By.desc("Start"))
        }
    }

    fun stopTestPackage() {
        device.executeShellCommand("am force-stop $target")
    }

    private fun node(selector: BySelector): UiObject2 =
        checkNotNull(device.wait(Until.findObject(By.copy(selector).pkg(target)), WAIT)) {
            "Performance journey target missing ($selector); use an unlocked English-language phone with no dialogs."
        }

    private fun clickControl(selector: BySelector) {
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
        const val ISOLATED_PACKAGE = "com.sujal.sleeptimer.benchmark"
        val HOME_CONTROLS: Pattern = Pattern.compile("Start|Stop")
        val HOME_OR_BACK: Pattern = Pattern.compile("Start|Stop|Back")
        const val WAIT = 5_000L
        const val START_ATTEMPTS = 3
        const val RATIONALE_WAIT = 500L
        const val SCROLLS = 2
        const val SCROLL_FRACTION = 0.8f
        const val DRAGS = 4
        const val THUMB_INSET = 0.046f
        const val SWIPE_STEPS = 40
        const val WAVE_MILLIS = 8_000L
        const val TRANSITION_SETTLE_MILLIS = 1_000L
    }
}
