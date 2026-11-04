# Progress

Last updated: 2026-11-04 (Asia/Kolkata).

## Current position

**Phase 1 Reliability core is implemented and all host checks pass. Its required physical-device exit test is pending. Stop before Phase 2 Home UI.** The owner authorized Phase 1 after Foundation and confirmed that user Stop cancels the timer while playback continues.

The phone was disconnected at the latest ADB check. No Phase 1 installation, Spotify stop, forced-Doze run, physical fade/restore or Android 17 hardening test is claimed. The owner's permission to defer unavailable device testing remains in effect. See [PHASE1_TESTING.md](PHASE1_TESTING.md) for exact install and timer commands.

## Implemented in Phase 1

- Pure Kotlin versioned settings/run/snapshot/result models, validated commands, injectable clock and platform ports. Elapsed realtime is the only countdown clock.
- One serial engine actor; initialization before publication, durable command deduplication, run/schedule/effect generations, cancellation of stale effects, bounded duration arithmetic and persisted warning/fade bookkeeping.
- Real settings and timer DataStores with separate files, bounded JSON serializers, semantic/schema validation and corruption handlers. Schema 1 is the initial format; unsupported versions fail closed. Settings-only backup excludes timer snapshots.
- Explicit immutable final-window and best-effort backup alarms, cancellation/reconciliation, timed idempotent wake leases, and a conservative full-run fallback when exact access is missing or a short run is at alarm-quota risk.
- Hilt-composed `specialUse` foreground service, immediate foreground promotion, ephemeral capability tracking, validated gateway, alarm/system receivers and visible-Activity debug entry points. No snapshot is accepted as proof of Android 17 while-in-use capability.
- Ordinary silent countdown notification, run-bound extend/cancel actions, separate-tap delivery IDs, permission-aware result posting and dismissal suppression until a meaningful state change. Promotion/Live Updates remain Phase 4.
- Real local/remote playback observation, optional notification-listener media-session pause, eligible focus handling including delayed/failed results, PAUSE down/up fallback and one retry. Calls/alarms defer; a screen-off 30-second watchdog handles auto-resume.
- Cancellable ease-out fade, persistence before volume writes, route/readback/user-change guards, settled-silence automatic restoration, two-minute restoration expiry and guarded gradual cancellation recovery. Native route eligibility is deliberately limited; see below.
- Same-boot process recovery, reboot rejection, bounded late-stop policy, persisted cancellation recovery and Android 15 stopped-package startup inspection. Available exit reason codes remain local; no app names/media metadata are logged.
- Separate debug-only test-player APK: real bundled local MediaPlayer/MediaSession, cooperative/focus-ignoring/auto-resume modes, silent output by default and deterministic resource cleanup. The production app does not depend on it.
- Deterministic and Robolectric coverage for state/recovery/scheduling, real DataStore corruption/reopen behavior, alarm identities, lock ownership, playback safety, command decoding and notifications.

## Verification actually completed

Commands used the existing JDK 17.0.20.1 through command-local JAVA_HOME.

| Command/check | Observed result |
|---|---|
| `./gradlew formatAll --console=plain` | **PASS**, exit 0 |
| `./gradlew assembleDebug testDebugUnitTest lintDebug spotlessCheck detekt --continue --console=plain` | **PASS**, BUILD SUCCESSFUL in 13 s; 877 tasks, 79 executed / 798 up-to-date |
| Host tests | **129 passed**, zero failures/skips in the test XML results |
| `aapt2 dump permissions` on debug APK | No INTERNET, USE_EXACT_ALARM or REQUEST_IGNORE_BATTERY_OPTIMIZATIONS; MODIFY_AUDIO_SETTINGS is included for volume APIs |
| `adb devices -l` | ADB started successfully; **no connected device** |
| Configuration cache | Stored for the final verification command; prior checks reused it |

Test breakdown: engine **24**, service-intent decoding **8**, playback **37**, data **20**, system **23**, notification **3**, Foundation navigation/theme/contrast **14**. Robolectric covers APIs 26/35 (theme also 31); this is not API 37 runtime evidence. Build-only future modules report NO-SOURCE, not feature coverage. No lint baseline or global analyzer threshold relaxation was added.

Debug APKs: `app/build/outputs/apk/debug/app-debug.apk` (`com.sujal.sleeptimer.debug`) and `tools/testplayer/build/outputs/apk/debug/testplayer-debug.apk` (`com.sujal.sleeptimer.testplayer`). The test-player release variant is disabled. LeakCanary is debug-only and contributes legacy storage permissions in the debug merged manifest; these are not production dependencies. No storage permission was granted during this task.

## Important limits and pending gates

- **Phase 1 device definition of done is not met yet:** the two-minute Spotify screen-off/forced-Doze stop within one second, actual fading and safe physical restore remain unmeasured. The app is not production-ready.
- Native fade/restore currently require API 33+, one connected output matching the media-route prediction, observable media sessions and safe interruption state. Ambiguous routes and all API 26–32 skip volume changes. Ordinary phone/Bluetooth routes may therefore stop without fading. See [playback safety](../core/playback/README.md); do not claim universal fade support.
- Foreground startup denial, exact-alarm revocation, sticky restart, real process death, reboot/update, force-stop, low memory, restricted bucket, OEM survival, long runs and actual wake-lock/battery behavior need runtime tests. Host tests cover the reducer and adapters only. `am kill` can leave an FGS process alive and is not automatically process-death evidence.
- Android 15 force-stop inspection uses the current PID's startup history. Older versions lack that unambiguous flag; detection is incomplete there. Interrupted results are persisted; their complete in-app explanation belongs to the later UI/reliability flows.
- A call/alarm deferral can hold a bounded two-hour wake lease with polling. Battery cost and callback refinement need hardening measurements. DND/Bedtime vibration behavior remains unverified on a device.
- Full Home UI arrives in Phase 2. Settings, contextual permission prompts/readiness, visible test-result flow, shake, nightly scheduling, screen locking and dimming arrive in Phase 3. The only current timer start controls are debug Activity commands; defaults for later features are persisted but those features are not exposed as working controls.
- Android 17 hardening/WIU start-path behavior is unverified. No emulator/system image or JDK 21 was installed. Robolectric API 36–37 requires Java 21, so approved JDK 17 coverage uses earlier APIs.
- Phase 0 API 26/37 emulator smoke checks remain deferred. An earlier Foundation APK installed and cold-launched on a Moto g54 5G / Android 15; that is not evidence for the current timer implementation.
- Stable detekt uses no type resolution because its embedded compiler predates Kotlin 2.4. Kotlin compilation and Android Lint remain enabled. Gradle reports future Gradle 10 deprecations. Release/R8/performance/size/OEM/privacy submission gates remain later phases.

## Environment and repository

SDK: `/Users/sujalsunilchaudhary/Library/Android/sdk`; platforms 34/35/36/37.0, Build Tools 34.0.0/35.0.0/36.0.0, Platform Tools 37.0.1, Command-line Tools 23.0. JDK: `/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`, 17.0.20.1 arm64. No SDK/JDK/emulator/CLI installation or shell-profile change occurred in Phase 1. `local.properties` retains the SDK path and is ignored/untracked.

Foundation checkpoints: `95d885e` planning, `9f62c60` implementation, `a50403a` verification docs, `f153c49` Android 15 installation follow-up. Phase 1 source commits:

| Commit | Change |
|---|---|
| `d336d40` | Model contracts and validated persistence |
| `a74098d` | Android alarm, clock, wake and recovery adapters |
| `617e4b3` | Guarded playback/fade pipeline and tests |
| `4348cb2` | Serial engine and foreground execution |
| `73c6ea3` | App wiring and ordinary notifications |
| `719f344` | Debug local test-player harness |

## Phone commands

From the project root, after connecting/authorizing USB debugging:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$HOME/Library/Android/sdk/platform-tools:$PATH"
adb devices -l
./gradlew :app:installDebug
adb shell am start -W -n com.sujal.sleeptimer.debug/com.sujal.sleeptimer.MainActivity
```

Start music, then run the actual ten-second timer:

```sh
adb shell am start -W -n com.sujal.sleeptimer.debug/com.sujal.sleeptimer.MainActivity -a com.sujal.sleeptimer.debug.TEST
```

Allow notifications in Android app settings to see ongoing controls/results. [PHASE1_TESTING.md](PHASE1_TESTING.md) includes two-minute runs, controlled silent test-player modes, forced-Doze cleanup and recovery checks.

## Next step

When the phone is connected, collect Phase 1 runtime evidence and resolve any failures. If device testing stays deferred, the next implementation phase is **Phase 2 Home UI**, requiring the owner's next continuation. Do not silently claim the pending runtime gates passed.
