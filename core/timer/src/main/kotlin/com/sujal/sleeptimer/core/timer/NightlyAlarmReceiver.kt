package com.sujal.sleeptimer.core.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sujal.sleeptimer.core.model.WakeLocks
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@AndroidEntryPoint
class NightlyAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var controller: NightlyController

    @Inject lateinit var wakes: WakeLocks

    @Inject @TimerAppScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NightlyController.ACTION) return
        val lease = wakes.acquire("nightly-handoff", HANDOFF_TIMEOUT)
        val pending = goAsync()
        scope.launch {
            try {
                withTimeout(RECEIVER_TIMEOUT) { controller.fired(intent) }
            } finally {
                lease.release()
                pending.finish()
            }
        }
    }

    private companion object {
        const val HANDOFF_TIMEOUT = 15_000L
        const val RECEIVER_TIMEOUT = 8_000L
    }
}
