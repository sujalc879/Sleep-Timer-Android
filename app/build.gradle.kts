plugins {
    id("sleep.android.compose.application")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.sujal.sleeptimer"
    buildFeatures { resValues = true }
    defaultConfig {
        applicationId = "com.sujal.sleeptimer"
        versionCode = 1
        versionName = "0.1.0"
        resValue("string", "shortcut_target_package", "com.sujal.sleeptimer")
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            resValue("string", "shortcut_target_package", "com.sujal.sleeptimer.debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    androidResources {
        generateLocaleConfig = true
    }
}

dependencies {
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
