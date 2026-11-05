package com.sujal.sleeptimer.core.system

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShakeDetectorTest {
    @Test fun syntheticAlternatingPeaksTriggerOnceAndRespectCooldown() {
        val detector = ShakeDetector()
        assertThat(detector.sample(0, 0f, 0f, GRAVITY)).isFalse()
        assertThat(detector.sample(100, 25f, 0f, GRAVITY)).isFalse()
        assertThat(detector.sample(200, -25f, 0f, GRAVITY)).isFalse()
        assertThat(detector.sample(300, 25f, 0f, GRAVITY)).isTrue()
        for (time in 400L..2_900L step 100) {
            val x = if (time % 200 == 0L) -25f else 25f
            assertThat(detector.sample(time, x, 0f, GRAVITY)).isFalse()
        }
        assertThat(detector.sample(3_300, -25f, 0f, GRAVITY)).isFalse()
        assertThat(detector.sample(3_400, 25f, 0f, GRAVITY)).isFalse()
        assertThat(detector.sample(3_500, -25f, 0f, GRAVITY)).isTrue()
    }

    @Test fun syntheticBedRollGravityAndUnidirectionalMovementDoNotTrigger() {
        val detector = ShakeDetector()
        for (time in 0L..10_000L step 20) {
            val x = kotlin.math.sin(time / 1_000.0).toFloat() * 3f
            assertThat(detector.sample(time, x, 0f, GRAVITY)).isFalse()
        }
        assertThat(detector.sample(10_100, 25f, 0f, GRAVITY)).isFalse()
        assertThat(detector.sample(10_200, 28f, 0f, GRAVITY)).isFalse()
        assertThat(detector.sample(10_300, 31f, 0f, GRAVITY)).isFalse()
    }

    @Test fun oldOrSparsePeaksCannotAccumulate() {
        val detector = ShakeDetector()
        detector.sample(0, 0f, 0f, GRAVITY)
        assertThat(detector.sample(100, 25f, 0f, GRAVITY)).isFalse()
        assertThat(detector.sample(200, -25f, 0f, GRAVITY)).isFalse()
        assertThat(detector.sample(1_500, 25f, 0f, GRAVITY)).isFalse()
        assertThat(detector.sample(1_400, -25f, 0f, GRAVITY)).isFalse()
    }

    @Test fun ordinarySensorNeedsInteractiveScreenOrFinalWindow() {
        assertThat(shouldMonitorShake(true, false, false, SHAKE_FINAL_WINDOW + 1)).isFalse()
        assertThat(shouldMonitorShake(true, false, false, SHAKE_FINAL_WINDOW)).isTrue()
        assertThat(shouldMonitorShake(true, false, true, SHAKE_FINAL_WINDOW + 1)).isTrue()
        assertThat(shouldMonitorShake(true, true, false, SHAKE_FINAL_WINDOW + 1)).isTrue()
        assertThat(shouldMonitorShake(false, true, true, SHAKE_FINAL_WINDOW)).isFalse()
        assertThat(shouldMonitorShake(true, true, true, 0)).isFalse()
    }

    private companion object {
        const val GRAVITY = 9.81f
    }
}
