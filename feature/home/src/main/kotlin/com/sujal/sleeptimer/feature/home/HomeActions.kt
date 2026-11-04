package com.sujal.sleeptimer.feature.home

data class HomeActions(
    val onStart: () -> Unit,
    val onStop: () -> Unit,
    val onMinutesChange: (Int) -> Unit,
    val onSelectionFinished: () -> Unit,
    val onPreset: (Int) -> Unit,
    val onReduce: () -> Unit,
    val onExtend: () -> Unit,
    val onTest: () -> Unit,
    val onReadiness: () -> Unit,
    val onCustom: () -> Unit,
    val onNightly: () -> Unit,
)
