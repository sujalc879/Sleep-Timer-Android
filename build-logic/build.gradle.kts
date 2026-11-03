import io.gitlab.arturbosch.detekt.Detekt

plugins {
    `kotlin-dsl`
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

group = "com.sujal.sleeptimer.buildlogic"

kotlin {
    jvmToolchain(17)
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
            .setEditorConfigPath(file("../.editorconfig"))
            .editorConfigOverride(mapOf("ktlint_function_naming_ignore_when_annotated_with" to "Composable"))
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
            .setEditorConfigPath(file("../.editorconfig"))
            .editorConfigOverride(mapOf("ktlint_function_naming_ignore_when_annotated_with" to "Composable"))
    }
}

detekt {
    toolVersion = libs.versions.detekt.get()
    buildUponDefaultConfig = true
    config.setFrom(file("../config/detekt/detekt.yml"))
    source.setFrom(fileTree("src") { include("**/*.kt") })
}

tasks.withType<Detekt>().configureEach {
    // Stable detekt embeds Kotlin 2.0.21; keep analysis independent of the Kotlin 2.4 classpath.
    classpath.setFrom(emptyList<Any>())
}

tasks.named("check") {
    dependsOn("spotlessCheck", "detekt")
}

dependencies {
    implementation(libs.agp)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.compose.compiler.gradle.plugin)
    implementation(libs.spotless.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "sleep.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidComposeApplication") {
            id = "sleep.android.compose.application"
            implementationClass = "AndroidComposeApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "sleep.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidComposeLibrary") {
            id = "sleep.android.compose.library"
            implementationClass = "AndroidComposeLibraryConventionPlugin"
        }
        register("kotlinLibrary") {
            id = "sleep.kotlin.library"
            implementationClass = "KotlinLibraryConventionPlugin"
        }
        register("androidTest") {
            id = "sleep.android.test"
            implementationClass = "AndroidTestConventionPlugin"
        }
        register("quality") {
            id = "sleep.quality"
            implementationClass = "QualityConventionPlugin"
        }
    }
}
