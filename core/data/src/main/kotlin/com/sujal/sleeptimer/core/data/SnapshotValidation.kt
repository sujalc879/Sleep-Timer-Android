package com.sujal.sleeptimer.core.data

import com.sujal.sleeptimer.core.model.TimerLimits
import com.sujal.sleeptimer.core.model.TimerPhase
import com.sujal.sleeptimer.core.model.TimerRun
import com.sujal.sleeptimer.core.model.TimerSnapshot
import com.sujal.sleeptimer.core.model.VolumeRecord

internal object SnapshotValidation {
    fun isValid(snapshot: TimerSnapshot): Boolean =
        snapshot.schemaVersion == 1 && snapshot.bootCount >= -1 && snapshot.revision >= 0 &&
            snapshot.scheduleGeneration >= 0 &&
            snapshot.selectedDurationMillis in TimerLimits.MINUTE..TimerLimits.MAX_DURATION &&
            snapshot.acceptedCommandIds.size <= MAXIMUM_COMMAND_IDS &&
            snapshot.acceptedCommandIds.all { it.isNotBlank() && it.length <= MAXIMUM_ID_LENGTH } &&
            hasValidRun(snapshot) && hasValidResult(snapshot)

    private fun hasValidRun(snapshot: TimerSnapshot): Boolean {
        val run = snapshot.run
        val requiresRun = snapshot.phase != TimerPhase.IDLE && snapshot.phase != TimerPhase.FINISHED
        return if (run == null) !requiresRun else requiresRun && isValid(run)
    }

    private fun isValid(run: TimerRun): Boolean =
        run.id.isNotBlank() && run.id.length <= MAXIMUM_ID_LENGTH && run.startedElapsed >= 0 &&
            run.endElapsed >= run.startedElapsed && run.totalDurationMillis > 0 &&
            run.deferredUntilElapsed?.let { it >= 0 } != false && run.fade?.let { isValid(it) } != false

    private fun hasValidResult(snapshot: TimerSnapshot): Boolean = snapshot.lastResult?.let {
        it.completedElapsed >= 0 && it.firstAttemptElapsed?.let { first -> first >= 0 } != false &&
            it.detail.length <= MAXIMUM_DETAIL_LENGTH
    } ?: true

    private fun isValid(record: VolumeRecord): Boolean =
        record.minimumIndex >= 0 && record.originalIndex >= record.minimumIndex &&
            record.lastWrittenIndex in record.minimumIndex..record.originalIndex &&
            record.capturedElapsed >= 0 && record.expiresElapsed >= record.capturedElapsed &&
            record.route.identity.isNotBlank() && record.route.identity.length <= MAXIMUM_ROUTE_LENGTH

    private const val MAXIMUM_COMMAND_IDS = 64
    private const val MAXIMUM_ID_LENGTH = 128
    private const val MAXIMUM_DETAIL_LENGTH = 2_048
    private const val MAXIMUM_ROUTE_LENGTH = 512
}
