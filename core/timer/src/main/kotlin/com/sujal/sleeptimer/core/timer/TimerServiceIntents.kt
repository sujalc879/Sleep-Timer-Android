package com.sujal.sleeptimer.core.timer

import android.content.Context
import android.content.Intent
import com.sujal.sleeptimer.core.model.StartSource
import com.sujal.sleeptimer.core.model.TimerCommand
import com.sujal.sleeptimer.core.model.TimerLimits

object TimerServiceIntents {
    const val START = "com.sujal.sleeptimer.action.START"
    const val TEST = "com.sujal.sleeptimer.action.TEST"
    const val CANCEL = "com.sujal.sleeptimer.action.CANCEL"
    const val EXTEND = "com.sujal.sleeptimer.action.EXTEND"
    const val REDUCE = "com.sujal.sleeptimer.action.REDUCE"
    const val RECONCILE = "com.sujal.sleeptimer.action.RECONCILE"
    const val DEBUG_START = "com.sujal.sleeptimer.debug.START"
    const val DEBUG_TEST = "com.sujal.sleeptimer.debug.TEST"
    const val DEBUG_STATUS = "com.sujal.sleeptimer.debug.STATUS"
    const val DURATION = "duration_millis"
    const val COMMAND_ID = "command_id"
    const val SOURCE = "source"
    const val WHILE_IN_USE = "while_in_use"
    const val EXPECTED_RUN = "expected_run"
    const val ONLY_IF_IDLE = "only_if_idle"
    const val DELTA = "delta_millis"

    fun base(context: Context, action: String): Intent = Intent(context, TimerService::class.java).setAction(action)

    fun decode(intent: Intent?, deliveryId: String): TimerCommand? = try {
        parse(intent, deliveryId)
    } catch (_: RuntimeException) {
        null
    }

    @Suppress("ReturnCount") // Reject invalid component inputs before constructing a command.
    private fun parse(intent: Intent?, deliveryId: String): TimerCommand? {
        if (intent == null) return TimerCommand.Reconcile("sticky-restart", serviceRestarted = true)
        val id = intent.getStringExtra(COMMAND_ID) ?: deliveryId
        if (id.isBlank() || id.length > MAX_ID_LENGTH) return null
        val expected = intent.getStringExtra(EXPECTED_RUN)
        if (expected != null && !validId(expected)) return null
        return command(intent, id, expected)
    }

    private fun validId(id: String): Boolean = id.isNotBlank() && id.length <= MAX_ID_LENGTH

    private fun command(intent: Intent, id: String, expected: String?): TimerCommand? = when (intent.action) {
        START -> decodeStart(intent, id)
        TEST -> TimerCommand.StartTest(intent.getBooleanExtra(WHILE_IN_USE, false), id)
        CANCEL -> TimerCommand.Stop(id, expected)
        EXTEND -> validDelta(intent)?.let { TimerCommand.Extend(it, id, expected) }
        REDUCE -> validDelta(intent)?.let { TimerCommand.Reduce(it, id, expected) }
        RECONCILE -> TimerCommand.Reconcile("service-signal")
        else -> null
    }

    @Suppress("ReturnCount") // Invalid duration and unknown source have independent guards.
    private fun decodeStart(intent: Intent, id: String): TimerCommand? {
        val duration = intent.getLongExtra(DURATION, -1)
        if (duration !in TimerLimits.MINUTE..TimerLimits.MAX_DURATION) return null
        val source = StartSource.entries.firstOrNull { it.name == intent.getStringExtra(SOURCE) } ?: return null
        return TimerCommand.Start(
            duration,
            source,
            intent.getBooleanExtra(WHILE_IN_USE, false),
            id,
            intent.getBooleanExtra(ONLY_IF_IDLE, false),
        )
    }

    private fun validDelta(intent: Intent): Long? =
        intent.getLongExtra(DELTA, -1).takeIf { it in 1..TimerLimits.MAX_DURATION }

    private const val MAX_ID_LENGTH = 128
}
