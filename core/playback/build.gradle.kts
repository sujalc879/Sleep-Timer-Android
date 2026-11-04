plugins {
    id("sleep.android.library")
}

android { namespace = "com.sujal.sleeptimer.core.playback" }

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.bundles.unit.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
}
