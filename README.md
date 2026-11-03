# Sleep timer for Android

Native Android sleep timer, built with Kotlin and Material 3 Expressive. The build brief and visual references are in [AGENTS.md](AGENTS.md) and [design](design).

## Current scope

Foundation provides the dynamic/fallback theme, system splash and icons, edge-to-edge Home/Settings navigation, module boundaries and build checks. The screens intentionally contain only their real app bars at this phase. Playback control and timer controls start in later phases; this build cannot stop a player yet.

See [docs/PROGRESS.md](docs/PROGRESS.md) for observed validation results and pending runtime checks, [docs/PLAN.md](docs/PLAN.md) for the phase sequence, and [docs/DECISIONS.md](docs/DECISIONS.md) for engineering choices.

## Build on macOS

Use JDK 17 and Android SDK Platform 37.0 with Build Tools 36.0.0. Keep the SDK path in an ignored root `local.properties`:

```properties
sdk.dir=/absolute/path/to/Android/sdk
```

On the owner's machine, the existing JDK can be selected for the current terminal:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew verifyFoundation
```

The checksum-pinned Gradle wrapper downloads its distribution and pinned project dependencies on first use. `verifyFoundation` runs debug assembly, host unit tests, Android Lint, Spotless/ktlint, detekt and included-build checks. The equivalent core checks are:

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug spotlessCheck detekt
```

Use `./gradlew formatAll` to format all modules and build logic. Stable detekt runs syntax analysis without type resolution because its embedded Kotlin compiler is older than the project compiler. This limitation does not disable Kotlin compilation or Android Lint.

Host navigation tests run on Robolectric APIs 26 and 35 with JDK 17. Robolectric API 37 requires JDK 21; no additional JDK is installed automatically. Host tests do not replace emulator/device smoke tests.

## Foundation device smoke test

After a successful debug build, install on an emulator or a connected phone:

```sh
./gradlew :app:installDebug
```

Open **Sleep timer**, tap the tune action to open **Settings**, and return using the Back arrow and system Back. Rotate on each screen, repeat with the largest font setting and both system light/dark themes, then cold-launch in dark mode to inspect splash continuity. Android 12+ uses wallpaper colors. Android 8–11 uses the generated fallback palette. Check gesture and three-button navigation insets where available.

The development package is `com.sujal.sleeptimer.debug`; its APK is `app/build/outputs/apk/debug/app-debug.apk`. Production package identity and signing remain release inputs. No device connection is needed for the host build. No emulator, SDK, JDK or device installation is performed automatically by the project.
