package com.sujal.sleeptimer.core.system

import kotlin.math.sqrt

/** Local gravity-filtered alternating peaks. Sensor timestamps, never wall time. */
class ShakeDetector {
    private val gravity = FloatArray(AXES)
    private var initialized = false
    private var lastSample = 0L
    private var lastPeak = 0L
    private var firstPeak = 0L
    private var lastShake = Long.MIN_VALUE / COOLDOWN_DIVISOR
    private var peaks = 0
    private var direction = FloatArray(AXES)

    // Tunable physical thresholds; real-device trace tuning is a documented gate.
    @Suppress("MagicNumber", "ReturnCount")
    fun sample(timeMillis: Long, x: Float, y: Float, z: Float): Boolean {
        val values = floatArrayOf(x, y, z)
        if (!initialized || timeMillis <= lastSample || timeMillis - lastSample > 500) {
            values.copyInto(gravity)
            initialized = true
            lastSample = timeMillis
            peaks = 0
            return false
        }
        val alpha = 0.8f
        val linear = FloatArray(AXES) { axis ->
            gravity[axis] = alpha * gravity[axis] + (1 - alpha) * values[axis]
            values[axis] - gravity[axis]
        }
        lastSample = timeMillis
        val magnitude = sqrt(linear.sumOf { (it * it).toDouble() }).toFloat()
        if (magnitude < 11.5f || timeMillis - lastShake < 3_000 || timeMillis - lastPeak < 80) return false
        val current = FloatArray(AXES) { linear[it] / magnitude }
        val alternating = current.indices.sumOf { (current[it] * direction[it]).toDouble() } < -0.35
        if (timeMillis - firstPeak > 1_200 || peaks == 0 || !alternating) {
            firstPeak = timeMillis
            peaks = 1
        } else {
            peaks++
        }
        direction = current
        lastPeak = timeMillis
        return if (peaks >= 3) {
            lastShake = timeMillis
            peaks = 0
            true
        } else {
            false
        }
    }
}

fun shouldMonitorShake(enabled: Boolean, wakeUp: Boolean, interactive: Boolean, remaining: Long): Boolean =
    enabled && remaining > 0 && (wakeUp || interactive || remaining <= SHAKE_FINAL_WINDOW)

const val SHAKE_FINAL_WINDOW = 300_000L

private const val AXES = 3
private const val COOLDOWN_DIVISOR = 2
