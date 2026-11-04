package com.sujal.sleeptimer.core.timer

import android.app.Notification
import com.sujal.sleeptimer.core.model.StopResult
import com.sujal.sleeptimer.core.model.TimerUiState
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TimerAppScope

interface TimerNotifications {
    fun foreground(state: TimerUiState): Notification
    fun refresh(state: TimerUiState, stateChanged: Boolean)
    fun completed(result: StopResult)
    fun interrupted()
    fun dismissed(runId: String, revision: Long)
}

object TimerNotificationIds {
    const val RUNNING = 100
    const val RESULT = 101
}
