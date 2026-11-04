package com.sujal.sleeptimer.core.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import com.sujal.sleeptimer.core.model.AppSettings
import com.sujal.sleeptimer.core.model.Clock
import com.sujal.sleeptimer.core.model.Diagnostics
import com.sujal.sleeptimer.core.model.SettingsStore
import com.sujal.sleeptimer.core.model.nextNightlyOccurrence
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class NightlyController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    private val clock: Clock,
    private val gateway: TimerGateway,
    private val diagnostics: Diagnostics,
    @TimerAppScope scope: CoroutineScope,
) {
    private val alarm = context.getSystemService(AlarmManager::class.java)
    private val mutex = Mutex()

    init {
        scope.launch { settings.data.collect { reconcile() } }
    }

    suspend fun reconcile(): Boolean = mutex.withLock { schedule(settings.data.first()) }

    suspend fun save(value: AppSettings): Boolean = mutex.withLock {
        if (value.nightlyEnabled && !exactAllowed()) return@withLock false
        val updated = settings.update { current ->
            current.copy(
                nightlyEnabled = value.nightlyEnabled,
                nightlyHour = value.nightlyHour,
                nightlyMinute = value.nightlyMinute,
                nightlyDays = value.nightlyDays,
                nightlyDurationMillis = value.nightlyDurationMillis,
            )
        }
        schedule(updated)
    }

    suspend fun fired(intent: Intent) = mutex.withLock {
        val current = settings.data.first()
        val occurrence = intent.getLongExtra(OCCURRENCE, -1)
        val identityMatches = current.nightlyEnabled && intent.getStringExtra(FINGERPRINT) == fingerprint(current)
        val fresh = identityMatches && occurrence > 0 && abs(clock.wallTimeMillis() - occurrence) <= DELIVERY_TOLERANCE
        if (fresh) {
            gateway.startScheduled(current.nightlyDurationMillis, "nightly:$occurrence")
        } else {
            diagnostics.record("nightly-stale-occurrence")
        }
        schedule(current)
    }

    @Suppress("ReturnCount") // Disabled schedules, denied access and absent occurrences are separate safety guards.
    private fun schedule(value: AppSettings): Boolean {
        cancel()
        if (!value.nightlyEnabled) return true
        if (!exactAllowed()) {
            diagnostics.record("nightly-exact-access-required")
            return false
        }
        val next =
            nextNightlyOccurrence(value, Instant.ofEpochMilli(clock.wallTimeMillis()), ZoneId.systemDefault())
                ?: return false
        val intent = base().putExtra(OCCURRENCE, next.toEpochMilli()).putExtra(FINGERPRINT, fingerprint(value))
        return try {
            val pending = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toEpochMilli(), pending)
            diagnostics.record("nightly-scheduled")
            true
        } catch (_: SecurityException) {
            diagnostics.record("nightly-exact-access-changed")
            false
        }
    }

    private fun exactAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()

    private fun cancel() {
        PendingIntent.getBroadcast(
            context,
            0,
            base(),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.let {
            alarm.cancel(it)
            it.cancel()
        }
    }

    private fun base(): Intent = Intent(context, NightlyAlarmReceiver::class.java).setAction(ACTION)
        .setData("sleeptimer://nightly".toUri())

    private fun fingerprint(value: AppSettings): String = listOf(
        value.nightlyHour,
        value.nightlyMinute,
        value.nightlyDays.sorted(),
        value.nightlyDurationMillis,
        ZoneId.systemDefault().id,
    ).joinToString(":")

    companion object {
        const val ACTION = "com.sujal.sleeptimer.action.NIGHTLY"
        private const val OCCURRENCE = "nightly_occurrence"
        private const val FINGERPRINT = "nightly_fingerprint"
        private const val DELIVERY_TOLERANCE = 5 * 60_000L
    }
}
