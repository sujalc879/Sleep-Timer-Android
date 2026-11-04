import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.TestExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

private const val MINIMUM_ANDROID_API = 26
private const val COMPILE_ANDROID_API = 37
private const val TARGET_ANDROID_API = 37
private const val JVM_TOOLCHAIN_VERSION = 17

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("sleep.quality")
        extensions.configure<ApplicationExtension> {
            compileSdk = COMPILE_ANDROID_API
            defaultConfig {
                minSdk = MINIMUM_ANDROID_API
                targetSdk = TARGET_ANDROID_API
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            testOptions.unitTests.isIncludeAndroidResources = true
            lint {
                warningsAsErrors = true
                checkDependencies = true
                error += setOf("NewApi", "MissingPermission", "UnspecifiedImmutableFlag")
            }
            buildFeatures.buildConfig = true
        }
        configureHostTests()
    }
}

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("sleep.quality")
        extensions.configure<LibraryExtension> {
            compileSdk = COMPILE_ANDROID_API
            defaultConfig {
                minSdk = MINIMUM_ANDROID_API
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            testOptions.unitTests.isIncludeAndroidResources = true
            lint {
                warningsAsErrors = true
                checkDependencies = true
                error += setOf("NewApi", "MissingPermission", "UnspecifiedImmutableFlag")
            }
        }
        configureHostTests()
    }
}

class AndroidComposeApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("sleep.android.application")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.configure<ApplicationExtension> { buildFeatures.compose = true }
    }
}

class AndroidComposeLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("sleep.android.library")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.configure<LibraryExtension> { buildFeatures.compose = true }
    }
}

class KotlinLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("java-library")
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        pluginManager.apply("sleep.quality")
        extensions.configure<KotlinJvmProjectExtension> { jvmToolchain(JVM_TOOLCHAIN_VERSION) }
    }
}

class AndroidTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.test")
        pluginManager.apply("sleep.quality")
        extensions.configure<TestExtension> {
            compileSdk = COMPILE_ANDROID_API
            defaultConfig {
                minSdk = MINIMUM_ANDROID_API
                targetSdk = TARGET_ANDROID_API
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            targetProjectPath = ":app"
            lint { warningsAsErrors = true }
        }
    }
}

private fun Project.configureHostTests() {
    tasks.withType<Test>().configureEach {
        jvmArgs(
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED",
            "--add-opens=java.base/java.io=ALL-UNNAMED",
            "--add-opens=java.base/java.net=ALL-UNNAMED",
            "--add-opens=java.base/java.security=ALL-UNNAMED",
            "--add-opens=java.base/java.text=ALL-UNNAMED",
            "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
            "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
        )
    }
}
