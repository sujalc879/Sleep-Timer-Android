package com.sujal.sleeptimer.core.system

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocalDiagnosticsTest {
    @Test
    fun ringBufferBoundsMemoryAndRetainsNewestEvents() {
        val diagnostics = LocalDiagnostics(capacity = 2, elapsedRealtime = { 0 })
        diagnostics.record("first")
        diagnostics.record("second")
        diagnostics.record("third")

        assertThat(diagnostics.snapshot()).containsExactly("second", "third").inOrder()
    }

    @Test
    fun eventsCannotGrowWithoutBound() {
        val diagnostics = LocalDiagnostics(elapsedRealtime = { 0 })
        diagnostics.record("x".repeat(1_000))

        assertThat(diagnostics.snapshot().single().length).isAtMost(160)
    }

    @Test
    fun reportUsesRelativeTimeAndCannotInjectLinesOrUnboundedPayloads() {
        var now = 9_000L
        val diagnostics = LocalDiagnostics(capacity = 2, elapsedRealtime = { now })
        diagnostics.record("first")
        now += 250
        diagnostics.record("wake_released:timer-runtime:250\n\r\u0000")
        now += 100
        diagnostics.record("x".repeat(1_000))
        val report = diagnostics.report()
        assertThat(report).contains("current app process only")
        assertThat(report).contains("+250 ms wake_released:timer-runtime:250\n")
        assertThat(report).contains("+350 ms " + "x".repeat(160))
        assertThat(report).doesNotContain("first")
        assertThat(report).doesNotContain("\r")
        assertThat(report).doesNotContain("\u0000")
        assertThat(report.length).isLessThan(600)
    }
}
