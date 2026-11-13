plugins {
    id("sleep.android.test")
}

android {
    namespace = "com.sujal.sleeptimer.macrobenchmark"
    buildFeatures.buildConfig = true
    defaultConfig {
        minSdk = 28
        buildConfigField("String", "TARGET_PACKAGE", "\"com.sujal.sleeptimer.benchmark\"")
    }
    buildTypes.create("benchmarkRelease") {
        isDebuggable = true
        signingConfig = signingConfigs.getByName("debug")
        matchingFallbacks += "release"
    }
    sourceSets.getByName("main").kotlin.srcDir("../tools/performance/src/main/kotlin")
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
}
