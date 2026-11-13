plugins {
    id("sleep.quality")
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.baselineprofile) apply false
}

val checkedModules = listOf(
    ":app", ":core:model", ":core:timer", ":core:data", ":core:playback", ":core:system",
    ":core:designsystem", ":feature:home", ":feature:settings", ":surface:notification",
    ":surface:tile", ":surface:widget", ":tools:testplayer", ":baselineprofile", ":benchmark",
)

val applicationAndLibraryModules = checkedModules - setOf(":core:model", ":baselineprofile", ":benchmark")

tasks.register("assembleDebug") {
    group = "build"
    dependsOn(applicationAndLibraryModules.map { "$it:assembleDebug" })
    dependsOn(
        ":core:model:assemble",
        ":baselineprofile:assembleNonMinifiedRelease",
        ":benchmark:assembleBenchmarkRelease",
    )
}

tasks.register("testDebugUnitTest") {
    group = "verification"
    dependsOn(applicationAndLibraryModules.map { "$it:testDebugUnitTest" })
    dependsOn(":core:model:test")
}

tasks.register("lintDebug") {
    group = "verification"
    dependsOn(applicationAndLibraryModules.map { "$it:lintDebug" })
}

tasks.named("spotlessCheck") {
    dependsOn(checkedModules.map { "$it:spotlessCheck" })
    dependsOn(gradle.includedBuild("build-logic").task(":spotlessCheck"))
}

tasks.named("detekt") {
    dependsOn(checkedModules.map { "$it:detekt" })
    dependsOn(gradle.includedBuild("build-logic").task(":detekt"))
}

tasks.register("formatAll") {
    group = "formatting"
    description = "Formats source and Gradle scripts in every module and the included build."
    dependsOn("spotlessApply")
    dependsOn(checkedModules.map { "$it:spotlessApply" })
    dependsOn(gradle.includedBuild("build-logic").task(":spotlessApply"))
}

tasks.register("verifyFoundation") {
    group = "verification"
    description = "Builds Foundation and runs unit, lint, formatting and static-analysis checks."
    dependsOn("assembleDebug", "testDebugUnitTest", "lintDebug", "spotlessCheck", "detekt")
    dependsOn(gradle.includedBuild("build-logic").task(":check"))
}

tasks.register<Exec>("verifyReleaseArtifacts") {
    group = "verification"
    description = "Audits the optimized APK/AAB, native alignment, permissions and shipped code."
    dependsOn(":app:assembleRelease", ":app:bundleRelease")
    commandLine("python3", "scripts/verify_release.py", "--report", "build/reports/release/artifacts.json")
}

tasks.register("verifyRelease") {
    group = "verification"
    description = "Runs host quality/image checks, release lint and optimized artifact inspection."
    dependsOn("verifyFoundation", "verifyReleaseArtifacts", "verifyArtifactVerifier", ":app:lintRelease")
    dependsOn(":feature:home:verifyRoborazziDebug", ":feature:settings:verifyRoborazziDebug")
}

tasks.register<Exec>("verifyDistributionArtifacts") {
    group = "verification"
    description = "Additionally requires an app-generated profile and externally supplied release signing."
    dependsOn("verifyRelease")
    commandLine("python3", "scripts/verify_release.py", "--require-app-profile", "--require-signed")
}

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    kotlin { target("tools/performance/**/*.kt") }
}

tasks.named<io.gitlab.arturbosch.detekt.Detekt>("detekt") {
    setSource(fileTree("tools/performance/src/main/kotlin") { include("**/*.kt") })
}

tasks.register<Exec>("verifyArtifactVerifier") {
    group = "verification"
    description = "Tests rejection of malformed and 4 KB-only native binaries."
    commandLine("python3", "-m", "unittest", "discover", "-s", "scripts", "-p", "test_*.py")
}
