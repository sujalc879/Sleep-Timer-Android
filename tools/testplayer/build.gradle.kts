plugins {
    id("sleep.android.application")
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
