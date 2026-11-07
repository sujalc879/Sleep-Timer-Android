plugins {
    id("sleep.android.library")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}
android { namespace = "com.sujal.sleeptimer.surface.tile" }
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:system"))
    implementation(project(":core:timer"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.android)
    ksp(libs.hilt.compiler)
    testImplementation(libs.bundles.unit.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
}
