plugins {
    id("sleep.android.library")
}

android { namespace = "com.sujal.sleeptimer.core.playback" }

dependencies {
    implementation(project(":core:model"))
}
