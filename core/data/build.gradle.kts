plugins {
    id("sleep.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android { namespace = "com.sujal.sleeptimer.core.data" }

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.bundles.unit.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
}
