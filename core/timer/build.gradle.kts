plugins {
    id("sleep.android.library")
}

android { namespace = "com.sujal.sleeptimer.core.timer" }

dependencies {
    implementation(project(":core:model"))
}
