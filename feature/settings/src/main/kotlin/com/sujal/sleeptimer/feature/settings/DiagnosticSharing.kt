package com.sujal.sleeptimer.feature.settings

import android.content.Intent

internal fun diagnosticsShareIntent(report: String): Intent = Intent.createChooser(
    Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, report)
    },
    null,
)
