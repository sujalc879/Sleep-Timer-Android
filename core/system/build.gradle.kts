plugins {
    id("sleep.android.library")
}

android { namespace = "com.sujal.sleeptimer.core.system" }

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.bundles.unit.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
}
