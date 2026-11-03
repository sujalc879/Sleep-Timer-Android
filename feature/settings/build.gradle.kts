plugins {
    id("sleep.android.compose.library")
}

android { namespace = "com.sujal.sleeptimer.feature.settings" }

dependencies {
    implementation(project(":core:designsystem"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    lintChecks(libs.compose.lint.checks)
}
