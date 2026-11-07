package com.sujal.sleeptimer.core.timer

data class SurfaceEvent(val id: String, val kind: SurfaceEventKind, val runId: String? = null)
enum class SurfaceEventKind { CUSTOM, REPLACED, FAILED }
