plugins {
    id("sleep.android.library")
}

android { namespace = "com.sujal.sleeptimer.surface.tile" }

dependencies {
    implementation(project(":core:model"))
}
