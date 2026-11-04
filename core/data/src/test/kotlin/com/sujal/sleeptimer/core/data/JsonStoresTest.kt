package com.sujal.sleeptimer.core.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.sujal.sleeptimer.core.model.AppSettings
import com.sujal.sleeptimer.core.model.Diagnostics
import com.sujal.sleeptimer.core.model.OutputRoute
import com.sujal.sleeptimer.core.model.StartSource
import com.sujal.sleeptimer.core.model.TimerLimits
import com.sujal.sleeptimer.core.model.TimerPhase
import com.sujal.sleeptimer.core.model.TimerRun
import com.sujal.sleeptimer.core.model.TimerSnapshot
import com.sujal.sleeptimer.core.model.VolumeRecord
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = Application::class)
class JsonStoresTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val jobs = mutableListOf<Job>()
    private val events = mutableListOf<String>()
    private val diagnostics = Diagnostics { synchronized(events) { events.add(it) } }

    @After
    fun closeStores() = runBlocking {
        jobs.forEach { it.cancelAndJoin() }
    }

    @Test
    fun defaultsAndSettingsSurviveAStoreRestart() = runBlocking {
        val firstScope = scope()
        val first = JsonSettingsStore(context, diagnostics, firstScope)
        assertThat(first.data.first()).isEqualTo(AppSettings())
        val changed = first.update { it.copy(lastDurationMillis = 90 * TimerLimits.MINUTE, restoreVolume = false) }
        firstScope.coroutineContext[Job]!!.cancelAndJoin()

        val restored = JsonSettingsStore(context, diagnostics, scope()).data.first()
        assertThat(restored).isEqualTo(changed)
    }

    @Test
    fun timerSnapshotPersistsFadeBookkeepingAndDeduplication() = runBlocking {
        val firstScope = scope()
        val store = JsonSnapshotStore(context, diagnostics, firstScope)
        val snapshot = snapshot()
        store.save(snapshot)
        firstScope.coroutineContext[Job]!!.cancelAndJoin()

        assertThat(JsonSnapshotStore(context, diagnostics, scope()).load()).isEqualTo(snapshot)
    }

    @Test
    fun corruptSnapshotIsDiscardedWithoutRestoringInventedVolume() = runBlocking {
        write(PersistenceFiles.TIMER, "{broken-json")
        val restored = JsonSnapshotStore(context, diagnostics, scope()).load()

        assertThat(restored).isEqualTo(TimerSnapshot())
        assertThat(restored.run).isNull()
        assertThat(events).contains("timer_corruption_discarded")
    }

    @Test
    fun unknownSettingsSchemaIsReplacedWithSafeDefaults() = runBlocking {
        write(PersistenceFiles.SETTINGS, """{"schemaVersion":999,"lastDurationMillis":1}""")

        assertThat(JsonSettingsStore(context, diagnostics, scope()).data.first()).isEqualTo(AppSettings())
        assertThat(events).contains("settings_corruption_defaults")
    }

    @Test
    fun invalidUpdatesLeavePreviousSettingsIntact() = runBlocking {
        val store = JsonSettingsStore(context, diagnostics, scope())
        val result = runCatching { store.update { it.copy(lastDurationMillis = -1) } }

        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(store.data.first()).isEqualTo(AppSettings())
    }

    @Test
    fun simultaneousSettingTransformsDoNotLoseAcceptedUpdates() = runBlocking {
        val store = JsonSettingsStore(context, diagnostics, scope())
        List(5) {
            async(Dispatchers.Default) {
                store.update { previous ->
                    previous.copy(
                        lastDurationMillis =
                            previous.lastDurationMillis + TimerLimits.MINUTE,
                    )
                }
            }
        }.awaitAll()

        assertThat(store.data.first().lastDurationMillis).isEqualTo(35 * TimerLimits.MINUTE)
    }

    @Test
    fun invalidSnapshotCannotReplaceLastCommittedRun() = runBlocking {
        val store = JsonSnapshotStore(context, diagnostics, scope())
        val expected = snapshot()
        store.save(expected)

        val result = runCatching { store.save(expected.copy(run = null)) }

        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(store.load()).isEqualTo(expected)
    }

    private fun scope(): CoroutineScope {
        val job = SupervisorJob()
        jobs.add(job)
        return CoroutineScope(job + Dispatchers.IO)
    }

    private fun write(name: String, content: String) {
        val file = File(context.filesDir, "datastore/$name")
        check(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory)
        file.writeText(content)
    }

    private fun snapshot(): TimerSnapshot = TimerSnapshot(
        bootCount = 4,
        revision = 3,
        scheduleGeneration = 2,
        phase = TimerPhase.FADING,
        run = TimerRun(
            id = "persisted-run",
            startedElapsed = 0,
            endElapsed = TimerLimits.MINUTE,
            totalDurationMillis = TimerLimits.MINUTE,
            source = StartSource.ACTIVITY,
            hasWhileInUse = true,
            fade = VolumeRecord(8, 0, 3, OutputRoute("speaker"), 1, 180_000),
        ),
        acceptedCommandIds = listOf("start-delivery", "extend-delivery"),
    )
}
