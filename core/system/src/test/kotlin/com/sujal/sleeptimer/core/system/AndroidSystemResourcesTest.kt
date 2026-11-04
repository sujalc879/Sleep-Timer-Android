package com.sujal.sleeptimer.core.system

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.sujal.sleeptimer.core.model.Diagnostics
import com.sujal.sleeptimer.core.model.WarningMode
import java.time.Duration
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = Application::class)
class AndroidSystemResourcesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun wakeLeaseReleaseIsIdempotentAndNeverReferenceCounted() {
        val events = mutableListOf<String>()
        val lease = AndroidWakeLocks(context, Diagnostics(events::add)).acquire("test", 1_000)
        val lock = ShadowPowerManager.getLatestWakeLock()
        assertThat(lock.isHeld).isTrue()
        assertThat(shadowOf(lock).isReferenceCounted).isFalse()

        lease.release()
        lease.release()

        assertThat(lock.isHeld).isFalse()
        assertThat(events.count { it.startsWith("wake_released:") }).isEqualTo(1)
    }

    @Test
    fun timedOutWakeLeaseCanStillBeSafelyClosed() {
        val lease = AndroidWakeLocks(context, Diagnostics { }).acquire("test", 100)
        val lock = ShadowPowerManager.getLatestWakeLock()

        ShadowSystemClock.advanceBy(Duration.ofMillis(101))

        assertThat(lock.isHeld).isFalse()
        lease.release()
        assertThat(lock.isHeld).isFalse()
    }

    @Test
    @Suppress("DEPRECATION")
    fun warningHonorsHapticPreferenceAndCallState() {
        val vibrator = vibrator()
        shadowOf(vibrator).setHasVibrator(true)
        val feedback = AndroidTimerFeedback(context, Diagnostics { })
        Settings.System.putInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 0)
        feedback.warning(WarningMode.SOFT)
        assertThat(shadowOf(vibrator).isVibrating).isFalse()

        Settings.System.putInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1)
        context.getSystemService(AudioManager::class.java)!!.mode = AudioManager.MODE_IN_COMMUNICATION
        feedback.warning(WarningMode.STRONG)
        assertThat(shadowOf(vibrator).isVibrating).isFalse()
    }

    @Test
    @Config(sdk = [35])
    @Suppress("DEPRECATION")
    fun warningUsesAlarmVibrationWithoutBypassFlags() {
        val vibrator = vibrator()
        shadowOf(vibrator).setHasVibrator(true)
        shadowOf(vibrator).setHasAmplitudeControl(false)
        Settings.System.putInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1)
        context.getSystemService(AudioManager::class.java)!!.mode = AudioManager.MODE_NORMAL

        AndroidTimerFeedback(context, Diagnostics { }).warning(WarningMode.SOFT)

        assertThat(shadowOf(vibrator).isVibrating).isTrue()
        val attributes = shadowOf(vibrator).vibrationAttributesFromLastVibration as VibrationAttributes
        assertThat(attributes.usage).isEqualTo(VibrationAttributes.USAGE_ALARM)
        assertThat(attributes.flags).isEqualTo(0)
    }

    @Test
    fun interactiveStateFollowsPowerManager() {
        val power = context.getSystemService(PowerManager::class.java)!!
        val feedback = AndroidTimerFeedback(context, Diagnostics { })
        shadowOf(power).setIsInteractive(false)
        assertThat(feedback.isInteractive()).isFalse()
        shadowOf(power).setIsInteractive(true)
        assertThat(feedback.isInteractive()).isTrue()
    }

    @Test
    fun elapsedClockIncludesDeepSleep() {
        val clock = AndroidClock(context)
        val before = clock.elapsedRealtime()

        ShadowSystemClock.simulateDeepSleep(Duration.ofSeconds(60))

        assertThat(clock.elapsedRealtime() - before).isEqualTo(60_000)
    }

    @Test
    fun bootIdentityComesFromTheSystemCounter() {
        Settings.Global.putInt(context.contentResolver, Settings.Global.BOOT_COUNT, 9)

        assertThat(AndroidClock(context).bootCount()).isEqualTo(9)
    }

    @Suppress("DEPRECATION")
    private fun vibrator(): Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)!!.defaultVibrator
    } else {
        context.getSystemService(Vibrator::class.java)!!
    }
}
