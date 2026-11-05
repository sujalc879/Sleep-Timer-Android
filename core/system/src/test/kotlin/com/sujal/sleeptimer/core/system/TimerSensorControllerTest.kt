package com.sujal.sleeptimer.core.system

import android.app.Application
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.sujal.sleeptimer.core.model.AppSettings
import com.sujal.sleeptimer.core.model.Clock
import com.sujal.sleeptimer.core.model.Diagnostics
import com.sujal.sleeptimer.core.model.RuntimeCapabilities
import com.sujal.sleeptimer.core.model.StartSource
import com.sujal.sleeptimer.core.model.TimerRun
import com.sujal.sleeptimer.core.model.WakeLease
import com.sujal.sleeptimer.core.model.WakeLocks
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSensor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TimerSensorControllerTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val sensors = shadowOf(context.getSystemService(SensorManager::class.java))
    private val capabilities = RuntimeCapabilities().apply { foregroundServiceActive = true }
    private val clock = SensorClock()
    private val wakes = SensorWakes()

    @Test fun ordinarySensorStartsInFinalWindowAndAlwaysCleansUpOnExtensionAndClose() {
        sensors.addSensor(ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER))
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(false)
        val controller = controller()
        val longRun = run(600_000)
        controller.update(longRun, AppSettings())
        assertThat(sensors.hasListener(controller)).isFalse()
        clock.now = 400_000
        controller.update(longRun, AppSettings())
        assertThat(sensors.hasListener(controller)).isTrue()
        assertThat(capabilities.shakeMonitoringActive).isTrue()
        assertThat(wakes.active).isEqualTo(1)
        assertThat(wakes.lastTimeout).isEqualTo(320_000)
        controller.update(longRun.copy(endElapsed = 1_000_000), AppSettings())
        assertThat(sensors.hasListener(controller)).isFalse()
        assertThat(wakes.active).isEqualTo(0)
        controller.update(run(650_000), AppSettings())
        assertThat(wakes.active).isEqualTo(1)
        controller.close()
        controller.close()
        assertThat(wakes.active).isEqualTo(0)
        assertThat(sensors.hasListener(controller)).isFalse()
        assertThat(capabilities.shakeMonitoringActive).isFalse()
    }

    @Test fun missingSensorOrFailedRegistrationNeverClaimsMonitoringOrRetainsWakeLease() {
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(false)
        val missing = controller()
        missing.update(run(60_000), AppSettings())
        assertThat(capabilities.shakeMonitoringActive).isFalse()
        missing.close()
        sensors.addSensor(ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER))
        sensors.setForceListenersToFail(true)
        val rejected = controller()
        rejected.update(run(60_000), AppSettings())
        assertThat(capabilities.shakeMonitoringActive).isFalse()
        assertThat(wakes.active).isEqualTo(0)
        rejected.close()
    }

    private fun controller() = TimerSensorController(context, clock, wakes, capabilities, Diagnostics {}, { _, _ -> })
    private fun run(end: Long) = TimerRun("sensor-run", 0, end, end, StartSource.ACTIVITY, true)
}

private class SensorClock : Clock {
    var now = 0L
    override fun elapsedRealtime() = now
    override fun wallTimeMillis() = now
    override fun bootCount() = 1
}

private class SensorWakes : WakeLocks {
    var active = 0
    var lastTimeout = 0L
    override fun acquire(reason: String, timeoutMillis: Long): WakeLease {
        active++
        lastTimeout = timeoutMillis
        var released = false
        return WakeLease {
            check(!released)
            released = true
            active--
        }
    }
}
