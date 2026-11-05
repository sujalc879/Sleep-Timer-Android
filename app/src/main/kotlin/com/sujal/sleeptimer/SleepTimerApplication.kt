package com.sujal.sleeptimer

import android.app.Application
import android.os.StrictMode
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SleepTimerApplication : Application() {
    @javax.inject.Inject lateinit var surfaces: SurfaceSync
    override fun onCreate() {
        super.onCreate()
        surfaces.start()
        if (BuildConfig.DEBUG) {
            StrictMode.enableDefaults()
        }
    }
}
