package com.sujal.sleeptimer.core.timer

import com.sujal.sleeptimer.core.model.CommandResult
import com.sujal.sleeptimer.core.model.TimerCommand
import com.sujal.sleeptimer.core.model.TimerPhase
import com.sujal.sleeptimer.core.model.TimerRun
import java.util.UUID

internal data class ReplacementUndo(
    val previous: TimerRun,
    val selection: Long,
    val replacementId: String,
    val generation: Long,
    val createdElapsed: Long,
)

@Suppress("ReturnCount") // Reject absent, stale and expired replacements independently.
internal suspend fun EngineRuntime.undoReplacement(command: TimerCommand.UndoReplacement): CommandResult {
    val undo = replacementUndo ?: return CommandResult(false, "No replacement to undo")
    val current = snapshot.run
    val identityMatches = current?.id == command.expectedRunId && current.id == undo.replacementId
    val now = clock.elapsedRealtime()
    val validTime = undo.previous.endElapsed > now && now - undo.createdElapsed in 0..UNDO_WINDOW
    if (!identityMatches || !validTime || snapshot.scheduleGeneration != undo.generation) {
        return CommandResult(false, "Replacement changed or expired")
    }
    val previous = deactivate()
    val restored = undo.previous.copy(
        id = UUID.randomUUID().toString(),
        fade = current?.fade,
        hasWhileInUse = current?.hasWhileInUse == true,
    )
    alarms.cancel(undo.replacementId, undo.generation)
    replacementUndo = null
    commit(
        snapshot.copy(
            phase = TimerPhase.RUNNING,
            run = restored,
            selectedDurationMillis = undo.selection,
            lastResult = null,
            scheduleGeneration = snapshot.scheduleGeneration + 1,
            acceptedCommandIds = snapshot.remember(command.commandId),
            needsAlarmReconcile = true,
        ),
    )
    rememberDuration(undo.selection)
    activatePlan(previous, recoverFade = restored.fade != null, allowPlayingRecovery = true)
    return CommandResult(true)
}

private const val UNDO_WINDOW = 60_000L
