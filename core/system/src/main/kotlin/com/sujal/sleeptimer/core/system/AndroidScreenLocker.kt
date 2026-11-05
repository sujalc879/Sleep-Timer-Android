package com.sujal.sleeptimer.core.system

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.PowerManager
import com.sujal.sleeptimer.core.model.Diagnostics
import com.sujal.sleeptimer.core.model.ScreenLocker

class SleepTimerAdminReceiver : DeviceAdminReceiver()

class AndroidScreenLocker(context: Context, private val diagnostics: Diagnostics) : ScreenLocker {
    private val app = context.applicationContext
    val component = ComponentName(app, SleepTimerAdminReceiver::class.java)
    private val policy = app.getSystemService(DevicePolicyManager::class.java)
    val active: Boolean get() = policy?.isAdminActive(component) == true

    fun activationIntent(): Intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)

    fun remove(): Boolean = try {
        policy?.removeActiveAdmin(component)
        true
    } catch (_: SecurityException) {
        diagnostics.record("admin-remove-denied")
        false
    }

    override fun lockIfSafe() {
        try {
            val audio = app.getSystemService(AudioManager::class.java)
            val interactive = app.getSystemService(PowerManager::class.java)?.isInteractive == true
            val alarm = audio?.activePlaybackConfigurations.orEmpty().any {
                it.audioAttributes.usage in
                    setOf(AudioAttributes.USAGE_ALARM, AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            }
            val uninterrupted = audio?.mode == AudioManager.MODE_NORMAL && !alarm
            if (active && interactive && uninterrupted) {
                policy?.lockNow()
                diagnostics.record("screen-lock-requested")
            }
        } catch (_: SecurityException) {
            diagnostics.record("screen-lock-denied")
        }
    }
}
