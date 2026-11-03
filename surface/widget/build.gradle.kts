plugins {
    id("sleep.android.library")
}

android { namespace = "com.sujal.sleeptimer.surface.widget" }

dependencies {
    implementation(project(":core:model"))
}
