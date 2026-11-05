package com.sujal.sleeptimer.core.system

import android.content.Context
import androidx.core.content.edit

class TileMembership(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("tile-membership", Context.MODE_PRIVATE)
    var added: Boolean
        get() = preferences.getBoolean("added", false)
        set(value) {
            preferences.edit { putBoolean("added", value) }
        }
}
