package com.sujal.sleeptimer.core.system

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocalDiagnosticsTest {
    @Test
    fun ringBufferBoundsMemoryAndRetainsNewestEvents() {
        val diagnostics = LocalDiagnostics(capacity = 2)
        diagnostics.record("first")
        diagnostics.record("second")
        diagnostics.record("third")

        assertThat(diagnostics.snapshot()).containsExactly("second", "third").inOrder()
    }

    @Test
    fun eventsCannotGrowWithoutBound() {
        val diagnostics = LocalDiagnostics()
        diagnostics.record("x".repeat(1_000))

        assertThat(diagnostics.snapshot().single().length).isAtMost(160)
    }
}
