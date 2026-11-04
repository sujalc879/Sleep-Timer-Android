package com.sujal.sleeptimer.core.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.sujal.sleeptimer.core.model.AppSettings
import com.sujal.sleeptimer.core.model.Diagnostics
import com.sujal.sleeptimer.core.model.SettingsStore
import com.sujal.sleeptimer.core.model.SnapshotStore
import com.sujal.sleeptimer.core.model.TimerSnapshot
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

object PersistenceFiles {
    const val SETTINGS = "settings.json"
    const val TIMER = "timer.json"
}

/** Create exactly one instance per application; each store owns a distinct file. */
class JsonSettingsStore(
    context: Context,
    diagnostics: Diagnostics,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : SettingsStore {
    private val appContext = context.applicationContext
    private val store: DataStore<AppSettings> = DataStoreFactory.create(
        serializer = ValidatedJsonSerializer(AppSettings(), AppSettings.serializer(), AppSettings::isValid),
        corruptionHandler = ReplaceFileCorruptionHandler {
            diagnostics.record("settings_corruption_defaults")
            AppSettings()
        },
        scope = scope,
        produceFile = { File(appContext.filesDir, "datastore/${PersistenceFiles.SETTINGS}") },
    )

    override val data: Flow<AppSettings> = store.data

    override suspend fun update(transform: (AppSettings) -> AppSettings): AppSettings = store.updateData { previous ->
        transform(previous).also { require(it.isValid()) { "Invalid settings" } }
    }
}

/** Timer snapshots are deliberately not in the settings backup allowlist. */
class JsonSnapshotStore(
    context: Context,
    diagnostics: Diagnostics,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : SnapshotStore {
    private val appContext = context.applicationContext
    private val store: DataStore<TimerSnapshot> = DataStoreFactory.create(
        serializer = ValidatedJsonSerializer(TimerSnapshot(), TimerSnapshot.serializer(), SnapshotValidation::isValid),
        corruptionHandler = ReplaceFileCorruptionHandler {
            diagnostics.record("timer_corruption_discarded")
            TimerSnapshot()
        },
        scope = scope,
        produceFile = { File(appContext.filesDir, "datastore/${PersistenceFiles.TIMER}") },
    )

    override suspend fun load(): TimerSnapshot = store.data.first()

    override suspend fun save(snapshot: TimerSnapshot) {
        require(SnapshotValidation.isValid(snapshot)) { "Invalid timer snapshot" }
        store.updateData { snapshot }
    }
}

/** Version one is the first persisted format; no invented legacy migration is applied. */
internal class ValidatedJsonSerializer<T>(
    override val defaultValue: T,
    private val serializer: KSerializer<T>,
    private val isValid: (T) -> Boolean,
) : Serializer<T> {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun readFrom(input: InputStream): T {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (bytes.size() + count > MAXIMUM_BYTES) throw CorruptionException("Stored data is too large")
            bytes.write(buffer, 0, count)
        }
        return try {
            json.decodeFromString(serializer, bytes.toString(Charsets.UTF_8.name())).also {
                if (!isValid(it)) throw CorruptionException("Unsupported or invalid stored schema")
            }
        } catch (exception: SerializationException) {
            throw CorruptionException("Cannot read stored JSON", exception)
        } catch (exception: IllegalArgumentException) {
            throw CorruptionException("Invalid stored value", exception)
        }
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        require(isValid(t)) { "Invalid stored value" }
        val bytes = json.encodeToString(serializer, t).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAXIMUM_BYTES) { "Stored data is too large" }
        output.write(bytes)
    }

    private companion object {
        const val BUFFER_SIZE = 4_096
        const val MAXIMUM_BYTES = 65_536
    }
}
