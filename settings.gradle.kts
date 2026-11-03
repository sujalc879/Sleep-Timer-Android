pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
rootProject.name = "SleepTimer"

include(
    ":app",
    ":core:model",
    ":core:timer",
    ":core:data",
    ":core:playback",
    ":core:system",
    ":core:designsystem",
    ":feature:home",
    ":feature:settings",
    ":surface:notification",
    ":surface:tile",
    ":surface:widget",
    ":tools:testplayer",
    ":baselineprofile",
    ":benchmark",
)
