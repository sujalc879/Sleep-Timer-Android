package com.sujal.sleeptimer.core.system

import android.os.SystemClock
import com.sujal.sleeptimer.core.model.DiagnosticReport
import com.sujal.sleeptimer.core.model.Diagnostics

/** Memory-only bounded event codes; never accepts package names or media metadata from adapters. */
class LocalDiagnostics(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) : Diagnostics,
    DiagnosticReport {
    private val started = elapsedRealtime()
    private val events = ArrayDeque<Event>()

    init {
        require(capacity in 1..MAXIMUM_CAPACITY)
    }

    @Synchronized
    override fun record(event: String) {
        if (events.size == capacity) events.removeFirst()
        val code = event.filterNot(Char::isISOControl).take(MAXIMUM_EVENT_LENGTH)
        events.addLast(Event((elapsedRealtime() - started).coerceAtLeast(0), code))
    }

    @Synchronized
    fun snapshot(): List<String> = events.map { it.code }

    @Synchronized
    override fun report(): String = buildString {
        appendLine("Sleep timer diagnostics — format 1")
        appendLine("Events from the current app process only.")
        appendLine("Times are milliseconds since diagnostics started.")
        appendLine()
        events.forEach { appendLine("+${it.elapsed} ms ${it.code}") }
    }

    private data class Event(val elapsed: Long, val code: String)

    private companion object {
        const val DEFAULT_CAPACITY = 128
        const val MAXIMUM_CAPACITY = 1_024
        const val MAXIMUM_EVENT_LENGTH = 160
    }
}
