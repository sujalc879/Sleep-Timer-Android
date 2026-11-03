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
    dependsOn(":core:model:assemble", ":baselineprofile:assembleDebug", ":benchmark:assembleDebug")
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
