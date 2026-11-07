package com.sujal.sleeptimer

import android.content.Context
import android.content.pm.ShortcutManager
import com.sujal.sleeptimer.core.model.StartSource
import com.sujal.sleeptimer.core.model.TimerPhase
import com.sujal.sleeptimer.core.model.isActive
import com.sujal.sleeptimer.core.timer.SurfaceEvent
import com.sujal.sleeptimer.core.timer.SurfaceEventKind
import com.sujal.sleeptimer.core.timer.SurfaceRequest
import com.sujal.sleeptimer.core.timer.TimerEngine
import com.sujal.sleeptimer.core.timer.TimerGateway
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityRetainedScoped
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

@ActivityRetainedScoped
class SurfaceLaunchCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: TimerEngine,
    private val gateway: TimerGateway,
) {
    private val mutableEvents = MutableStateFlow<SurfaceEvent?>(null)
    val events = mutableEvents.asStateFlow()
    fun consumed(id: String) {
        if (mutableEvents.value?.id == id) mutableEvents.value = null
    }

    @Suppress("ReturnCount") // Handle custom, stale-idle and rejected-start actions before awaiting an accepted run.
    suspend fun launch(request: SurfaceRequest) {
        val id = UUID.randomUUID().toString()
        if (request == SurfaceRequest.Custom) {
            context.getSystemService(ShortcutManager::class.java)?.reportShortcutUsed("custom")
            mutableEvents.value = SurfaceEvent(id, SurfaceEventKind.CUSTOM)
            return
        }
        request as SurfaceRequest.Start
        val before = engine.state.first { it.initialized }.snapshot
        if (request.onlyIfIdle && before.isActive) return
        val duration = request.duration ?: before.selectedDurationMillis
        if (!gateway.startFromVisibleActivity(duration, id, request.source, request.onlyIfIdle)) {
            mutableEvents.value = SurfaceEvent(id, SurfaceEventKind.FAILED)
            return
        }
        val accepted = withTimeoutOrNull(ACK_TIMEOUT) {
            engine.state.first { id in it.snapshot.acceptedCommandIds }
        }
        if (accepted == null) {
            if (!request.onlyIfIdle) mutableEvents.value = SurfaceEvent(id, SurfaceEventKind.FAILED)
        } else {
            request.shortcutId?.let { context.getSystemService(ShortcutManager::class.java)?.reportShortcutUsed(it) }
            val replaceable =
                before.run?.isTest == false && before.phase in setOf(TimerPhase.RUNNING, TimerPhase.FADING)
            if (request.source == StartSource.SHORTCUT && replaceable &&
                accepted.snapshot.acceptedCommandIds.lastOrNull() == id
            ) {
                mutableEvents.value = SurfaceEvent(id, SurfaceEventKind.REPLACED, accepted.snapshot.run?.id)
            }
        }
    }
    private companion object {
        const val ACK_TIMEOUT = 5_000L
    }
}
