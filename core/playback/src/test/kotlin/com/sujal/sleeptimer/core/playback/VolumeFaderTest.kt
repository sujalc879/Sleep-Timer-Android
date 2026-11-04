package com.sujal.sleeptimer.core.playback

import com.google.common.truth.Truth.assertThat
import com.sujal.sleeptimer.core.model.AppSettings
import com.sujal.sleeptimer.core.model.Interruption
import com.sujal.sleeptimer.core.model.OutputRoute
import com.sujal.sleeptimer.core.model.StopStatus
import com.sujal.sleeptimer.core.model.TimerLimits
import com.sujal.sleeptimer.core.model.VolumeRecord
import kotlinx.coroutines.test.runTest
import org.junit.Test

class VolumeFaderTest {
    @Test
    fun fadePersistsBookkeepingBeforeWritingAndOnlyDecreasesToMinimum() = runTest {
        val fixture = PlaybackFixture(testScheduler)
        val records = mutableListOf<VolumeRecord?>()
        val outcome = fixture.coordinator.fade(
            fixture.run(duration = FADE_DURATION),
            AppSettings(fadeMillis = FADE_DURATION),
            fixture.token,
        ) { record ->
            if (records.isEmpty()) assertThat(fixture.backend.writes).isEmpty()
            records += record
        }

        assertThat(outcome.aborted).isFalse()
        assertThat(outcome.record?.originalIndex).isEqualTo(PlaybackFixture.ORIGINAL_VOLUME)
        assertThat(outcome.record?.lastWrittenIndex).isEqualTo(PlaybackFixture.MINIMUM_VOLUME)
        assertThat(fixture.backend.writes.map { it.index }).isInOrder(descendingOrder)
        assertThat(fixture.backend.writes.last().index).isEqualTo(PlaybackFixture.MINIMUM_VOLUME)
        assertThat(records.filterNotNull().last().lastWrittenIndex).isEqualTo(PlaybackFixture.MINIMUM_VOLUME)
    }

    @Test
    fun staleEffectCannotWriteEvenIfInvalidatedAfterPersistence() = runTest {
        val fixture = PlaybackFixture(testScheduler)
        fixture.coordinator.fade(fixture.run(FADE_DURATION), AppSettings(), fixture.token) { record ->
            if (record != null && record.lastWrittenIndex < record.originalIndex) fixture.gate.invalidate()
        }

        assertThat(fixture.backend.writes).isEmpty()
    }

    @Test
    fun mismatchedReadbackAbortsWithoutUpwardCorrection() = runTest {
        val fixture = PlaybackFixture(testScheduler)
        fixture.backend.volumeAction = { _, _ -> }
        val outcome = fixture.coordinator.fade(fixture.run(FADE_DURATION), AppSettings(), fixture.token) {}

        assertThat(outcome.aborted).isTrue()
        assertThat(outcome.record?.restoreAllowed).isFalse()
        assertThat(fixture.backend.writes).hasSize(1)
        assertThat(fixture.backend.writes.single().index).isLessThan(PlaybackFixture.ORIGINAL_VOLUME)
    }

    @Test
    fun routeChangeDuringFadeDisablesRestoration() = runTest {
        val fixture = PlaybackFixture(testScheduler)
        fixture.backend.volumeAction = { backend, index ->
            backend.volumeIndex = index
            backend.route = OutputRoute("different-output")
        }
        val outcome = fixture.coordinator.fade(fixture.run(FADE_DURATION), AppSettings(), fixture.token) {}

        assertThat(outcome.aborted).isTrue()
        assertThat(outcome.record?.restoreAllowed).isFalse()
        assertThat(fixture.backend.writes).hasSize(1)
    }

    @Test
    fun unknownRouteFixedVolumeOrUnobservableRemoteStateSkipsFade() = runTest {
        val fixture = PlaybackFixture(testScheduler)
        fixture.backend.route = null
        assertThat(fixture.coordinator.fade(fixture.run(), AppSettings(), fixture.token) {}.record).isNull()
        fixture.backend.route = PlaybackFixture.ROUTE
        fixture.backend.fixed = true
        assertThat(fixture.coordinator.fade(fixture.run(), AppSettings(), fixture.token) {}.record).isNull()
        fixture.backend.fixed = false
        fixture.backend.remoteObservable = false
        assertThat(fixture.coordinator.fade(fixture.run(), AppSettings(), fixture.token) {}.record).isNull()
        assertThat(fixture.backend.writes).isEmpty()
    }

    @Test
    fun restoredFadeNeverRecapturesAlreadyReducedVolumeAsOriginal() = runTest {
        val fixture = PlaybackFixture(testScheduler)
        val originalRecord = fixture.record()
        fixture.backend.volumeIndex = originalRecord.lastWrittenIndex
        val outcome = fixture.coordinator.fade(fixture.run(fade = originalRecord), AppSettings(), fixture.token) {}

        assertThat(outcome.record).isEqualTo(originalRecord)
        assertThat(fixture.backend.writes).isEmpty()
    }

    @Test
    fun automaticRestoreRequiresSilenceAndSettlingAndNeverWritesWhilePlaying() = runTest {
        val fixture = PlaybackFixture(testScheduler)
        val record = fixture.record()
        fixture.backend.volumeIndex = record.lastWrittenIndex
        fixture.backend.playing = false
        val result = fixture.coordinator.stop(fixture.run(fade = record), AppSettings(), fixture.token) {}

        assertThat(result.status).isEqualTo(StopStatus.NOTHING_PLAYING)
        assertThat(fixture.backend.writes.single().index).isEqualTo(record.originalIndex)
        assertThat(fixture.backend.writes.single().playing).isFalse()
        assertThat(fixture.backend.writes.single().elapsed - PlaybackFixture.BASE_ELAPSED).isAtLeast(SETTLE_MILLIS)
        assertThat(fixture.backend.abandonedAt.single()).isLessThan(fixture.backend.writes.single().elapsed)
    }

    @Test
    fun changedUserVolumeOrExpiredRecordCannotRestore() = runTest {
        val fixture = PlaybackFixture(testScheduler)
        fixture.backend.playing = false
        fixture.backend.volumeIndex = PlaybackFixture.FADED_VOLUME + 1
        assertThat(fixture.coordinator.recoverFade(fixture.record(), fixture.token, false)).isFalse()
        fixture.backend.volumeIndex = PlaybackFixture.FADED_VOLUME
        val expired = fixture.record().copy(expiresElapsed = fixture.clock.elapsedRealtime())
        assertThat(fixture.coordinator.recoverFade(expired, fixture.token, true)).isFalse()
        assertThat(fixture.backend.writes).isEmpty()
    }

    @Test
    fun explicitCancellationRampsGentlyWhilePlayingButNeverAboveOriginal() = runTest {
        val fixture = PlaybackFixture(testScheduler)
        val record = fixture.record()
        fixture.backend.volumeIndex = record.lastWrittenIndex
        val restored = fixture.coordinator.recoverFade(record, fixture.token, allowWhilePlaying = true)

        assertThat(restored).isTrue()
        assertThat(fixture.backend.writes.last().index).isEqualTo(record.originalIndex)
        assertThat(fixture.backend.writes.all { it.index <= record.originalIndex }).isTrue()
        assertThat(fixture.backend.writes.last().elapsed - PlaybackFixture.BASE_ELAPSED).isAtLeast(TimerLimits.SECOND)
    }

    @Test
    fun callOrLostAccessDuringCancellationPreventsVolumeIncrease() = runTest {
        val fixture = PlaybackFixture(testScheduler)
        fixture.backend.volumeIndex = PlaybackFixture.FADED_VOLUME
        fixture.backend.interruption = Interruption.CALL
        assertThat(fixture.coordinator.recoverFade(fixture.record(), fixture.token, true)).isFalse()
        fixture.backend.interruption = Interruption.NONE
        fixture.backend.remoteObservable = false
        assertThat(fixture.coordinator.recoverFade(fixture.record(), fixture.token, true)).isFalse()
        assertThat(fixture.backend.writes).isEmpty()
    }

    private companion object {
        const val FADE_DURATION = 5 * TimerLimits.SECOND
        const val SETTLE_MILLIS = 2_500L
        val descendingOrder = Comparator<Int> { first, second -> second.compareTo(first) }
    }
}
