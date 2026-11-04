package com.sujal.sleeptimer.core.system

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import com.sujal.sleeptimer.core.model.Diagnostics
import com.sujal.sleeptimer.core.model.TimerFeedback
import com.sujal.sleeptimer.core.model.WarningMode

class AndroidTimerFeedback(context: Context, private val diagnostics: Diagnostics) : TimerFeedback {
    private val appContext = context.applicationContext
    private val power = appContext.getSystemService(PowerManager::class.java)
    private val audio = appContext.getSystemService(AudioManager::class.java)

    @Suppress("DEPRECATION")
    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        appContext.getSystemService(Vibrator::class.java)
    }

    @Suppress("DEPRECATION")
    override fun warning(mode: WarningMode) {
        if (mode == WarningMode.OFF || !canVibrate()) {
            return
        }
        try {
            if (audio?.activePlaybackConfigurations.orEmpty().any {
                    it.audioAttributes.usage == AudioAttributes.USAGE_ALARM ||
                        it.audioAttributes.usage == AudioAttributes.USAGE_NOTIFICATION_RINGTONE
                }
            ) {
                return
            }
            val duration = if (mode == WarningMode.SOFT) SOFT_PULSE_MILLIS else STRONG_PULSE_MILLIS
            val amplitude = if (mode == WarningMode.SOFT) SOFT_AMPLITUDE else STRONG_AMPLITUDE
            val effect = if (vibrator?.hasAmplitudeControl() == true) {
                VibrationEffect.createWaveform(
                    longArrayOf(0, duration, PULSE_GAP_MILLIS, duration),
                    intArrayOf(0, amplitude, 0, amplitude),
                    -1,
                )
            } else {
                VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator?.vibrate(
                    effect,
                    VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_ALARM).build(),
                )
            } else {
                vibrator?.vibrate(
                    effect,
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                )
            }
            diagnostics.record("warning_vibration_requested")
        } catch (_: SecurityException) {
            diagnostics.record("warning_vibration_rejected")
        }
    }

    override fun isInteractive(): Boolean = power?.isInteractive ?: true

    private fun canVibrate(): Boolean = vibrator?.hasVibrator() == true && audio?.mode == AudioManager.MODE_NORMAL &&
        Settings.System.getInt(appContext.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0

    private companion object {
        const val SOFT_PULSE_MILLIS = 35L
        const val STRONG_PULSE_MILLIS = 90L
        const val PULSE_GAP_MILLIS = 70L
        const val SOFT_AMPLITUDE = 40
        const val STRONG_AMPLITUDE = 180
    }
}
