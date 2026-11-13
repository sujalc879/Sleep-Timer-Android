plugins {
    id("sleep.android.test")
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.sujal.sleeptimer.baselineprofile"
    buildFeatures.buildConfig = true
    defaultConfig {
        minSdk = 28
        buildConfigField("String", "TARGET_PACKAGE", "\"com.sujal.sleeptimer.benchmark\"")
    }
    sourceSets.getByName("main").kotlin.srcDir("../tools/performance/src/main/kotlin")
}

baselineProfile { useConnectedDevices = true }

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
}
