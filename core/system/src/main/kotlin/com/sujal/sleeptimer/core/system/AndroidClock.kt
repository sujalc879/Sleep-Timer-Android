package com.sujal.sleeptimer.core.system

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.sujal.sleeptimer.core.model.Clock

class AndroidClock(context: Context) : Clock {
    private val resolver = context.applicationContext.contentResolver

    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
    override fun wallTimeMillis(): Long = System.currentTimeMillis()
    override fun bootCount(): Int = try {
        Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, -1)
    } catch (_: SecurityException) {
        -1
    }
}
