plugins {
    id("sleep.android.compose.application")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.baselineprofile)
}

val signingInputs = listOf("STORE_FILE", "STORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD").associateWith {
    providers.environmentVariable("SLEEP_TIMER_$it").orNull
}
val signingConfigured = signingInputs.values.any { it != null }
require(!signingConfigured || signingInputs.values.all { !it.isNullOrBlank() }) {
    "Release signing requires SLEEP_TIMER_STORE_FILE, SLEEP_TIMER_STORE_PASSWORD, SLEEP_TIMER_KEY_ALIAS and SLEEP_TIMER_KEY_PASSWORD together."
}
val candidateVersionCode = providers.gradleProperty("sleepTimer.versionCode").orElse("1").get().toIntOrNull()
require(candidateVersionCode != null && candidateVersionCode in 1..2_100_000_000) { "Invalid sleepTimer.versionCode" }
val candidateVersionName = providers.gradleProperty("sleepTimer.versionName").orElse("0.1.0").get()
require(candidateVersionName.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?"))) {
    "sleepTimer.versionName must use major.minor.patch with an optional prerelease suffix."
}

android {
    namespace = "com.sujal.sleeptimer"
    buildFeatures { resValues = true }
    defaultConfig {
        applicationId = "com.sujal.sleeptimer"
        versionCode = candidateVersionCode
        versionName = candidateVersionName
        resValue("string", "shortcut_target_package", "com.sujal.sleeptimer")
    }
    if (signingConfigured) {
        signingConfigs.create("upload") {
            storeFile = file(signingInputs.getValue("STORE_FILE")!!)
            require(storeFile!!.isFile) { "SLEEP_TIMER_STORE_FILE must identify an existing keystore." }
            storePassword = signingInputs.getValue("STORE_PASSWORD")
            keyAlias = signingInputs.getValue("KEY_ALIAS")
            keyPassword = signingInputs.getValue("KEY_PASSWORD")
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            resValue("string", "shortcut_target_package", "com.sujal.sleeptimer.debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingConfigured) signingConfig = signingConfigs.getByName("upload")
        }
    }
    buildTypes.create("benchmarkRelease") {
        applicationIdSuffix = ".benchmark"
        matchingFallbacks += "release"
        signingConfig = signingConfigs.getByName("debug")
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        resValue("string", "shortcut_target_package", "com.sujal.sleeptimer.benchmark")
        resValue("string", "app_name", "Sleep timer benchmark")
    }
    buildTypes.create("nonMinifiedRelease") {
        applicationIdSuffix = ".benchmark"
        matchingFallbacks += "release"
        signingConfig = signingConfigs.getByName("debug")
        resValue("string", "shortcut_target_package", "com.sujal.sleeptimer.benchmark")
        resValue("string", "app_name", "Sleep timer benchmark")
    }
    androidResources {
        generateLocaleConfig = true
    }
}

baselineProfile {
    automaticGenerationDuringBuild = false
    saveInSrc = true
    mergeIntoMain = true
    filter { include("com.sujal.sleeptimer.**") }
}

dependencies {
    baselineProfile(project(":baselineprofile"))
    implementation(libs.androidx.profileinstaller)
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:timer"))
    implementation(project(":core:system"))
    implementation(project(":core:playback"))
    implementation(project(":surface:notification"))
    implementation(project(":surface:tile"))
    implementation(project(":surface:widget"))
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(project(":core:designsystem"))
    implementation(project(":feature:home"))
    implementation(project(":feature:settings"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.android)
    ksp(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.leakcanary.android)
    lintChecks(libs.compose.lint.checks)
    testImplementation(libs.bundles.unit.testing)
    testImplementation(libs.bundles.compose.testing)
    testImplementation(libs.bundles.screenshots)
}
