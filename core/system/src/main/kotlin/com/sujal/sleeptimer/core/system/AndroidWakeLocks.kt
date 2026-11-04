package com.sujal.sleeptimer.core.system

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import com.sujal.sleeptimer.core.model.Diagnostics
import com.sujal.sleeptimer.core.model.TimerLimits
import com.sujal.sleeptimer.core.model.WakeLease
import com.sujal.sleeptimer.core.model.WakeLocks
import java.util.concurrent.atomic.AtomicBoolean

class AndroidWakeLocks(context: Context, private val diagnostics: Diagnostics) : WakeLocks {
    private val manager = context.applicationContext.getSystemService(PowerManager::class.java)

    override fun acquire(reason: String, timeoutMillis: Long): WakeLease {
        val boundedTimeout = timeoutMillis.coerceIn(1, MAXIMUM_TIMEOUT)
        val safeReason = reason.filter { it.isLetterOrDigit() || it in "_:.-" }.take(MAXIMUM_TAG_LENGTH)
        val lock = checkNotNull(manager) { "Power service unavailable" }
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SleepTimer:$safeReason")
        lock.setReferenceCounted(false)
        lock.acquire(boundedTimeout)
        val acquired = SystemClock.elapsedRealtime()
        val released = AtomicBoolean(false)
        diagnostics.record("wake_acquired:$safeReason:$boundedTimeout")
        return WakeLease {
            if (released.compareAndSet(false, true)) {
                try {
                    if (lock.isHeld) lock.release()
                } finally {
                    diagnostics.record("wake_released:$safeReason:${SystemClock.elapsedRealtime() - acquired}")
                }
            }
        }
    }

    private companion object {
        const val MAXIMUM_TIMEOUT = TimerLimits.MAX_DURATION + TimerLimits.RESTORE_WINDOW
        const val MAXIMUM_TAG_LENGTH = 80
    }
}
