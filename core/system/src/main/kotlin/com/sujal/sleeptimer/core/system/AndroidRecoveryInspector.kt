package com.sujal.sleeptimer.core.system

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import com.sujal.sleeptimer.core.model.Diagnostics
import com.sujal.sleeptimer.core.model.RecoveryInspector

/** Queried on the engine's IO scope. No package names, traces or media metadata are retained. */
class AndroidRecoveryInspector(context: Context, private val diagnostics: Diagnostics) : RecoveryInspector {
    private val manager = context.getSystemService(ActivityManager::class.java)

    override fun wasForceStopped(): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager?.getHistoricalProcessExitReasons(null, 0, 1)?.firstOrNull()?.let {
                diagnostics.record("previous-process-exit-${it.reason}")
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            manager?.getHistoricalProcessStartReasons(START_HISTORY_LIMIT)
                ?.firstOrNull { it.pid == Process.myPid() }?.wasForceStopped() == true
        } else {
            // Older platforms have no unambiguous stopped-package startup signal.
            false
        }
    } catch (_: SecurityException) {
        diagnostics.record("process-history-unavailable")
        false
    }

    private companion object {
        const val START_HISTORY_LIMIT = 8
    }
}
