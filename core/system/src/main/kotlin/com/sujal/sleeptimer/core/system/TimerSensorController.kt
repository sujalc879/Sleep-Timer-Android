package com.sujal.sleeptimer.core.system

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import com.sujal.sleeptimer.core.model.AppSettings
import com.sujal.sleeptimer.core.model.Clock
import com.sujal.sleeptimer.core.model.Diagnostics
import com.sujal.sleeptimer.core.model.RuntimeCapabilities
import com.sujal.sleeptimer.core.model.TimerRun
import com.sujal.sleeptimer.core.model.WakeLease
import com.sujal.sleeptimer.core.model.WakeLocks

/** Main-thread owner, created/destroyed with the foreground service. */
class TimerSensorController(
    context: Context,
    private val clock: Clock,
    private val wakes: WakeLocks,
    private val capabilities: RuntimeCapabilities,
    private val diagnostics: Diagnostics,
    private val onShake: (String, Long) -> Unit,
) : SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val sensor = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
        ?: sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var detector = ShakeDetector()
    private var runId: String? = null
    private var extend = 0L
    private var registered = false
    private var wake: WakeLease? = null
    private var wakeEnd = 0L

    fun update(run: TimerRun?, settings: AppSettings) {
        val remaining = (run?.endElapsed ?: 0) - clock.elapsedRealtime()
        val monitor = sensor != null && capabilities.foregroundServiceActive && run != null && !run.isTest &&
            shouldMonitorShake(settings.shakeToExtend, sensor.isWakeUpSensor, power?.isInteractive == true, remaining)
        if (runId != run?.id) detector = ShakeDetector()
        runId = run?.id
        extend = settings.extendMillis
        val canMonitor = updateWake(monitor, run?.endElapsed ?: 0, remaining)
        updateRegistration(canMonitor)
        capabilities.shakeMonitoringActive = registered
        if (!registered) releaseWake()
    }

    private fun updateWake(monitor: Boolean, end: Long, remaining: Long): Boolean {
        val needsWake = monitor && sensor?.isWakeUpSensor == false && power?.isInteractive == false
        if (!needsWake) {
            releaseWake()
            return monitor
        }
        if (wake == null || wakeEnd != end) {
            releaseWake()
            wakeEnd = end
            try {
                wake = wakes.acquire("shake-final-window", remaining.coerceAtLeast(1) + WAKE_GRACE)
            } catch (_: RuntimeException) {
                diagnostics.record("shake-wake-denied")
            }
        }
        return monitor && wake != null
    }

    private fun updateRegistration(monitor: Boolean) {
        if (monitor && !registered) {
            try {
                registered = sensors?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME) == true
            } catch (_: RuntimeException) {
                diagnostics.record("shake-registration-failed")
            }
        } else if (!monitor && registered) {
            unregister()
            registered = false
        }
    }

    fun close() {
        unregister()
        registered = false
        capabilities.shakeMonitoringActive = false
        releaseWake()
    }

    private fun unregister() {
        try {
            sensors?.unregisterListener(this)
        } catch (_: RuntimeException) {
            diagnostics.record("shake-unregister-failed")
        }
    }

    private fun releaseWake() {
        wake?.release()
        wake = null
        wakeEnd = 0
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (registered &&
            detector.sample(event.timestamp / NANOS_PER_MILLI, event.values[0], event.values[1], event.values[2])
        ) {
            runId?.let { onShake(it, extend) }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val WAKE_GRACE = 120_000L
    }
}
