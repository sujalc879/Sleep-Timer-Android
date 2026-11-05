plugins {
    id("sleep.android.compose.library")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)
}

android { namespace = "com.sujal.sleeptimer.feature.home" }

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:system"))
    implementation(project(":core:timer"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.adaptive)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    lintChecks(libs.compose.lint.checks)
    testImplementation(libs.bundles.unit.testing)
    testImplementation(libs.bundles.compose.testing)
    testImplementation(libs.bundles.screenshots)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
