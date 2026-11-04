package com.sujal.sleeptimer.core.system

import com.sujal.sleeptimer.core.model.Diagnostics

/** Memory-only bounded event codes; never accepts package names or media metadata from adapters. */
class LocalDiagnostics(private val capacity: Int = DEFAULT_CAPACITY) : Diagnostics {
    private val events = ArrayDeque<String>()

    init {
        require(capacity in 1..MAXIMUM_CAPACITY)
    }

    @Synchronized
    override fun record(event: String) {
        if (events.size == capacity) events.removeFirst()
        events.addLast(event.take(MAXIMUM_EVENT_LENGTH))
    }

    @Synchronized
    fun snapshot(): List<String> = events.toList()

    private companion object {
        const val DEFAULT_CAPACITY = 128
        const val MAXIMUM_CAPACITY = 1_024
        const val MAXIMUM_EVENT_LENGTH = 160
    }
}
