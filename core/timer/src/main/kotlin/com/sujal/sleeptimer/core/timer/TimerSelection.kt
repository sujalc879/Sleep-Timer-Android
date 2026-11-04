package com.sujal.sleeptimer.core.timer

import com.sujal.sleeptimer.core.model.CommandResult
import com.sujal.sleeptimer.core.model.TimerCommand
import com.sujal.sleeptimer.core.model.TimerLimits
import com.sujal.sleeptimer.core.model.isActive

internal suspend fun EngineRuntime.selectDuration(command: TimerCommand.SelectDuration): CommandResult {
    val valid = command.durationMillis in TimerLimits.MINUTE..TimerLimits.MAX_DURATION && !snapshot.isActive
    return if (valid) {
        settingsStore.update { it.copy(lastDurationMillis = command.durationMillis) }
        commit(
            snapshot.copy(
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
