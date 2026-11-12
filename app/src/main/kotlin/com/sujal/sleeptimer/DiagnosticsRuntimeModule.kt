package com.sujal.sleeptimer

import android.util.Log
import com.sujal.sleeptimer.core.model.Clock
import com.sujal.sleeptimer.core.model.DiagnosticReport
import com.sujal.sleeptimer.core.model.Diagnostics
import com.sujal.sleeptimer.core.system.LocalDiagnostics
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DiagnosticsRuntimeModule {
    @Provides @Singleton
    fun localDiagnostics(clock: Clock): LocalDiagnostics = LocalDiagnostics(elapsedRealtime = clock::elapsedRealtime)

    @Provides @Singleton
    fun diagnosticReport(memory: LocalDiagnostics): DiagnosticReport = memory

    @Provides @Singleton
    fun diagnostics(memory: LocalDiagnostics): Diagnostics = Diagnostics { event ->
        memory.record(event)
        if (BuildConfig.DEBUG) Log.d("SleepTimer", event)
    }
}
