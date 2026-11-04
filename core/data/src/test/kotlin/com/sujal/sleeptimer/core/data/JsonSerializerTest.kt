package com.sujal.sleeptimer.core.data

import androidx.datastore.core.CorruptionException
import com.google.common.truth.Truth.assertThat
import com.sujal.sleeptimer.core.model.AppSettings
import com.sujal.sleeptimer.core.model.TimerPhase
import com.sujal.sleeptimer.core.model.TimerSnapshot
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test

class JsonSerializerTest {
    private val settings = ValidatedJsonSerializer(AppSettings(), AppSettings.serializer(), AppSettings::isValid)

    @Test
    fun omittedOptionalFieldsUseVersionOneDefaults() = runBlocking {
        val restored = settings.readFrom(input("""{"schemaVersion":1}"""))

        assertThat(restored).isEqualTo(AppSettings())
    }

    @Test
    fun addedUnknownFieldsRemainForwardCompatibleWithinTheSameSchema() = runBlocking {
        val restored = settings.readFrom(input("""{"schemaVersion":1,"futureOptionalField":true}"""))

        assertThat(restored).isEqualTo(AppSettings())
    }

    @Test
    fun invalidSemanticValueIsCorruptionRatherThanAnUnsafeTimerSetting() {
        assertThrows(CorruptionException::class.java) {
            runBlocking { settings.readFrom(input("""{"schemaVersion":1,"fadeMillis":60001}""")) }
        }
    }

    @Test
    fun unsupportedVersionIsNotSilentlyInterpretedAsCurrent() {
        assertThrows(CorruptionException::class.java) {
            runBlocking { settings.readFrom(input("""{"schemaVersion":2}""")) }
        }
    }

    @Test
    fun oversizedInputIsRejectedBeforeUnboundedAllocation() {
        assertThrows(CorruptionException::class.java) {
            runBlocking { settings.readFrom(ByteArrayInputStream(ByteArray(65_537))) }
        }
    }

    @Test
    fun activePhaseWithoutRunIsRejected() {
        assertThat(SnapshotValidation.isValid(TimerSnapshot(phase = TimerPhase.RUNNING))).isFalse()
    }

    private fun input(value: String): ByteArrayInputStream = ByteArrayInputStream(value.toByteArray(Charsets.UTF_8))
}
