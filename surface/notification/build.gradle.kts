plugins {
    id("sleep.android.library")
}

android { namespace = "com.sujal.sleeptimer.surface.notification" }

dependencies {
    implementation(project(":core:model"))
}
