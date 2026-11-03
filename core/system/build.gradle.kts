plugins {
    id("sleep.android.library")
}

android { namespace = "com.sujal.sleeptimer.core.system" }

dependencies {
    implementation(project(":core:model"))
}
