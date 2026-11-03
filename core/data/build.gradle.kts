plugins {
    id("sleep.android.library")
}

android { namespace = "com.sujal.sleeptimer.core.data" }

dependencies {
    implementation(project(":core:model"))
}
