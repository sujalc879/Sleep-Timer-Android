package com.sujal.sleeptimer.surface.widget

import android.content.Context
import com.sujal.sleeptimer.core.model.Clock
import com.sujal.sleeptimer.core.model.SettingsStore
import com.sujal.sleeptimer.core.timer.TimerEngine
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetRuntime {
    fun engine(): TimerEngine
    fun settings(): SettingsStore
    fun clock(): Clock
}

internal suspend fun currentWidgetPresentation(context: Context): WidgetPresentation {
    val ports = EntryPointAccessors.fromApplication(context.applicationContext, WidgetRuntime::class.java)
    val snapshot = ports.engine().state.first { it.initialized }.snapshot
    return widgetPresentation(
        snapshot,
        ports.settings().data.first(),
        ports.clock().elapsedRealtime(),
        ports.clock().bootCount(),
    )
}
