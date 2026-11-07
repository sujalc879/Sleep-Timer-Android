package com.sujal.sleeptimer.core.timer

import com.sujal.sleeptimer.core.model.CommandResult
import com.sujal.sleeptimer.core.model.TimerCommand
import com.sujal.sleeptimer.core.model.TimerLimits
import com.sujal.sleeptimer.core.model.TimerPhase
import com.sujal.sleeptimer.core.model.isActive

internal suspend fun EngineRuntime.selectDuration(command: TimerCommand.SelectDuration): CommandResult {
    val valid = command.durationMillis in TimerLimits.MINUTE..TimerLimits.MAX_DURATION && !snapshot.isActive
    return if (valid) {
        settingsStore.update { it.copy(lastDurationMillis = command.durationMillis) }
        commit(
            snapshot.copy(
                phase = TimerPhase.IDLE,
                selectedDurationMillis = command.durationMillis,
                acceptedCommandIds = snapshot.remember(command.commandId),
            ),
        )
        CommandResult(true)
    } else {
        CommandResult(false, "Duration cannot be changed")
    }
}

internal suspend fun EngineRuntime.startTest(command: TimerCommand.StartTest): CommandResult = if (snapshot.isActive) {
    CommandResult(false, "Timer already running")
} else {
    start(
        TEST_DURATION,
        com.sujal.sleeptimer.core.model.StartSource.TEST,
        command.hasWhileInUse,
        command.commandId,
        true,
    )
}

internal fun validCommandId(id: String): Boolean = id.isNotBlank() && id.length <= MAX_COMMAND_ID_LENGTH

internal suspend fun EngineRuntime.startCommand(command: TimerCommand.Start): CommandResult =
    if (command.onlyIfIdle && snapshot.isActive) {
        CommandResult(false, "Timer already running")
    } else {
        start(command.durationMillis, command.source, command.hasWhileInUse, command.commandId)
    }

internal fun EngineRuntime.commandRejection(command: TimerCommand): CommandResult? {
    val id = command.deliveryId()
    return when {
        id != null && !validCommandId(id) -> CommandResult(false, "Invalid command")
        id != null && id in snapshot.acceptedCommandIds -> CommandResult(false, "Duplicate command")
        else -> null
    }
}

internal suspend fun EngineRuntime.rememberDuration(duration: Long) {
    try {
        settings = settingsStore.update { it.copy(lastDurationMillis = duration) }
    } catch (_: java.io.IOException) {
        // Selection is already durable in the timer snapshot. Preference failure must not lose its deadline.
        diagnostics.record("duration-preference-save-failed")
    }
}
