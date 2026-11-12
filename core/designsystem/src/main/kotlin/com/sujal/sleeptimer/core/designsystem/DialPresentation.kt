package com.sujal.sleeptimer.core.designsystem

data class DialPresentation(
    val minutes: Int,
    val countdown: String,
    val endLabel: String,
    val spokenDuration: String,
    val controlLabel: String,
    val increaseLabel: String,
    val decreaseLabel: String,
    val increaseFiveLabel: String,
    val decreaseFiveLabel: String,
    val running: Boolean = false,
    val progress: Float = 0f,
    val message: String? = null,
)
