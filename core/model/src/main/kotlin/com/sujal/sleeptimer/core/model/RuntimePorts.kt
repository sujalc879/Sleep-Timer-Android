package com.sujal.sleeptimer.core.model

import kotlinx.coroutines.flow.Flow

interface Clock {
    fun elapsedRealtime(): Long
    fun wallTimeMillis(): Long
    fun bootCount(): Int
}

interface SnapshotStore {
    suspend fun load(): TimerSnapshot
    suspend fun save(snapshot: TimerSnapshot)
}

fun interface RecoveryInspector {
    fun wasForceStopped(): Boolean
}

interface SettingsStore {
    val data: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings): AppSettings
}

data class AlarmPlan(val runId: String, val generation: Long, val finalWindowElapsed: Long, val endElapsed: Long)

interface AlarmScheduler {
    fun canScheduleExactAlarms(): Boolean
    fun schedule(plan: AlarmPlan): Boolean
    fun cancel(runId: String, generation: Long)
}

fun interface WakeLease {
    fun release()
}

interface WakeLocks {
    fun acquire(reason: String, timeoutMillis: Long): WakeLease
}

interface TimerFeedback {
    fun warning(mode: WarningMode)
    fun isInteractive(): Boolean
}

fun interface Diagnostics {
    fun record(event: String)
}

enum class Interruption { NONE, CALL, ALARM, UNKNOWN }

data class VolumeState(val index: Int, val minimum: Int, val maximum: Int, val fixed: Boolean, val route: OutputRoute?)

data class PlaybackObservation(
    val localPlaying: Boolean,
    val remotePlaying: Boolean = false,
    val interruption: Interruption = Interruption.NONE,
    val volume: VolumeState? = null,
    val remoteObservable: Boolean = true,
) {
    val playing: Boolean get() = localPlaying || remotePlaying
    val mustDefer: Boolean get() = interruption == Interruption.CALL || interruption == Interruption.ALARM
}

enum class FocusResult { GRANTED, DELAYED, FAILED }

/** A generation invalidation and a guarded binder write share the same short critical section. */
class EffectGate {
    private var generation = 0L

    @Synchronized
    fun next(): EffectToken {
        generation += 1
        return EffectToken(this, generation)
    }

    @Synchronized
    fun invalidate() {
        generation += 1
    }

    @Synchronized
    internal fun valid(expected: Long): Boolean = generation == expected

    @Synchronized
    internal fun <T> guarded(expected: Long, action: () -> T): T? = if (generation == expected) action() else null
}

class EffectToken internal constructor(private val gate: EffectGate, private val generation: Long) {
    fun isValid(): Boolean = gate.valid(generation)
    fun <T> guarded(action: () -> T): T? = gate.guarded(generation, action)
}

class RuntimeCapabilities {
    @Volatile var shakeMonitoringActive: Boolean = false

    @Volatile var screenLockAvailable: Boolean = false

    @Volatile var foregroundServiceActive: Boolean = false

    @Volatile var foregroundServiceHasWhileInUse: Boolean = false

    @Volatile var activityVisible: Boolean = false
}

/** Native observations and narrowly scoped actions; implemented without network or hidden APIs. */
interface PlaybackBackend {
    fun observe(): PlaybackObservation
    fun pauseSessions(): Boolean
    fun requestFocus(ownerId: String): FocusResult
    fun focusResult(ownerId: String): FocusResult
    fun abandonFocus(ownerId: String)
    fun dispatchPause()
    fun setVolume(index: Int)
    fun canControlAudio(run: TimerRun): Boolean
    fun isInteractive(): Boolean
}

data class FadeOutcome(val record: VolumeRecord?, val aborted: Boolean = false)

interface PlaybackController {
    suspend fun observe(): PlaybackObservation
    suspend fun fade(
        run: TimerRun,
        settings: AppSettings,
        token: EffectToken,
        onRecord: suspend (VolumeRecord?) -> Unit,
    ): FadeOutcome
    suspend fun stop(
        run: TimerRun,
        settings: AppSettings,
        token: EffectToken,
        onRecord: suspend (VolumeRecord?) -> Unit,
    ): StopResult
    suspend fun recoverFade(record: VolumeRecord, token: EffectToken, allowWhilePlaying: Boolean): Boolean
    fun abandonFocus(ownerId: String)
}
