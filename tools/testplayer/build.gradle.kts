plugins {
    id("sleep.android.compose.application")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}

android {
    namespace = "com.sujal.sleeptimer.testplayer"
    defaultConfig {
        applicationId = "com.sujal.sleeptimer.testplayer"
        versionCode = 1
        versionName = "0.1.0"
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) { it.enable = false }
}
