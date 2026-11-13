# Sleep timer for Android

Native Android sleep timer, built with Kotlin and Material 3 Expressive. The build brief and visual references are in [AGENTS.md](AGENTS.md) and [design](design).

## Current scope

Phases 0–5 provide the timer engine/service, guarded playback stopping and volume handling, adaptive Home, Settings and consent flows, shake extension, optional screen locking/dimming, notifications, tile, widgets and shortcuts with replacement Undo. Hardening adds hinge/RTL/large-text fixes, interrupted-run explanations, local diagnostic sharing and a full edge-case test plan. Host build/quality checks, 209 host tests and 15 image comparisons pass. This task did not install the latest APK on a phone; physical playback/Doze, surface latency, sensor, DND, API 36.1/37 and OEM gates remain pending. Phase 6 release preparation adds optimized artifact checks, connected-device profiling/benchmarks and a draft Play handoff; release readiness still requires the pending device/profile/signing/performance gates. This is a development build.

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

## Phone testing

For a connected and authorized phone, build, install and open the normal debug app with:

```sh
./scripts/run-phone.sh
```

The helper uses the existing SDK/JDK and checks device authorization before building. For an emulator or direct Gradle installation:

```sh
./gradlew :app:installDebug
```

Start with the complete [manual test plan](docs/MANUAL_TEST_PLAN.md), which records every runtime case as pending until observed. See [Phase 4 test steps](docs/PHASE4_TESTING.md) for notifications, tile, both widgets and shortcuts, and [Phase 3 test steps](docs/PHASE3_TESTING.md) for controls and consent flows. For a quick check, play media, open **Sleep timer** → **Settings** → **Run a 10-second test**. Review **Share diagnostics** in Reliability; it opens a chooser for bounded current-process events and sends nothing before you choose a target. The Stop control cancels the timer and leaves playback running; expiry uses the stop pipeline.

For navigation/appearance, tap the tune action to open **Settings**, and return using the Back arrow and system Back. Rotate on each screen, repeat with the largest font setting and both system light/dark themes, then cold-launch in dark mode to inspect splash continuity. Android 12+ uses wallpaper colors. Android 8–11 uses the generated fallback palette. Check gesture and three-button navigation insets where available.

The development package is `com.sujal.sleeptimer.debug`; its APK is `app/build/outputs/apk/debug/app-debug.apk`. Production package identity and signing remain release inputs. No device connection is needed for the host build. No emulator, SDK, JDK or device installation is performed automatically by the project.


## Release preparation

With Python 3.9+ available, run `./gradlew verifyRelease` for host quality/image checks, optimized APK/AAB assembly, release lint and artifact inspection. Nine additional Python artifact-verifier tests pass. Signing is optional for this development candidate. `verifyDistributionArtifacts` additionally requires generated app-profile source and external upload signing; it does not replace device QA or publish anything.

See [release testing](docs/RELEASE_TESTING.md) for real connected-device profile/benchmark commands, budget evidence and optimized smoke tests, and [Play submission](docs/PLAY_SUBMISSION.md) for signing/version inputs, privacy/disclosure copy, listing/rollout plans and required owner decisions. No app-specific profile or physical performance result has been collected yet.
