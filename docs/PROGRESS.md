# Progress

Last updated: 2026-11-05 (Asia/Kolkata).

## Current position

**Phase 3 Settings and features is implemented and host verification passes. Stop before Phase 4 system surfaces.** Stop still cancels the timer and leaves playback running. Physical-device exit gates remain pending; this is not a production-readiness claim.

ADB started successfully but reported **no connected devices** during Phase 3. No phone installation, screen locking, playback stop, physical shake, DND or overnight timing result is claimed for this phase. The Moto g54 5G / Android 15 installed and opened the prior Phase 2 APK; that historical result does not validate this new APK.

See [PHASE3_TESTING.md](PHASE3_TESTING.md) for installation and every Settings/permission/sensor test, [PHASE2_TESTING.md](PHASE2_TESTING.md) for Home controls, and [PHASE1_TESTING.md](PHASE1_TESTING.md) for playback/Doze/recovery procedures.

## Implemented in Phase 3

- Complete grouped Settings cards: fade slider, restore, extension/warning pickers, Nightly configuration, real tile/widget Add actions, dynamic color, Light/Dark/System, dimming, playback test and reliability guidance. Preferences use atomic DataStore transforms; the existing engine observes settings changes.
- Optional force-lock-only device admin, explicit biometric/PIN disclosure, Android activation, deactivation/uninstall path and external-revocation readiness. Locking follows a successful/quiet stop pipeline with a fresh playback/call check and effect-token guard; tests and cancellation never lock.
- Foreground-service-owned accelerometer registration, gravity-filtered alternating-peak detector, cooldown, accepted-command confirmation haptics, bounded ordinary-sensor final-window wake leases, registration-failure cleanup and service-destruction cleanup. The final alarm opens a five-minute shake window when shake is enabled; the ordinary-sensor screen-off trade-off is documented. Recorded real-device trace tuning is still pending.
- Application night-mode persistence on API 31+, dynamic/fallback application and widget schemes, minimum window brightness for visible running Home, and restoration on leaving. Legacy starting windows use the generated dark surface until preferences and the initial snapshot are ready; API 31+ keeps its matching launch scheme.
- Contextual notification then exact-alarm prompts after the timer starts, a remembered exact-alarm rationale, actual access refresh, counted critical readiness issues, optional media-session disclosure and manufacturer battery instructions/browser guides.
- Reused real 10-second test/Nightly flows from Settings, plus a completion-stamp guard that prevents an older stored test from appearing as the result of a new request.
- Supporting real QS tile and pin-capable Glance countdown widget so Settings has no inert Add row. State/minute refreshes and native host countdowns are connected. Full dial ring/responsiveness/previews, the presets widget, promoted notifications and shortcuts remain Phase 4.
- Material 3 **1.5.0-rc01**, Navigation 3 **1.2.1**, UI Automator **2.4.1** verified against Google's Maven metadata/POMs on November 5. No SDK/JDK/emulator/global CLI installation occurred.

## Current verification — Phase 3

Commands use the existing JDK 17 through command-local JAVA_HOME.

| Check | Observed result |
|---|---|
| `./gradlew formatAll --console=plain` | **PASS** |
| `./gradlew assembleDebug testDebugUnitTest lintDebug spotlessCheck detekt :feature:home:verifyRoborazziDebug :feature:settings:verifyRoborazziDebug --continue --console=plain` | **PASS**, BUILD SUCCESSFUL in 19 s; 931 tasks, 140 executed / 791 up-to-date |
| All host test XML, including pure-model JVM tests | **174 passed**, zero failures/errors/skips |
| Screenshot comparisons | **5 passed**: existing 3 Home baselines plus visually reviewed Settings dark/light at 390 dp |
| New coverage | Settings interaction 4 + screenshots 2; synthetic shake 4; sensor lifecycle/failure 2; screen-lock engine safeguards 2; tile permission/membership 4; widget metadata 2; window brightness 2; legacy splash 1; new-test freshness 1 |
| Final APK permission inspection | No INTERNET, USE_EXACT_ALARM, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS or AccessibilityService; force-lock-only admin metadata |
| SDK local path | local.properties remains ignored and untracked |
| `adb devices -l` | **No connected devices**; no Phase 3 installation attempted |

The two Settings images were reviewed against design/03-settings.png. These and the Home images are implementation regression baselines, not a claim of pixel-identical mockup matching. The dependency refresh passed existing Home/Settings image comparisons without rewriting the baselines. Test-only legacy Robolectric/Compose helper deprecation warnings remain; Gradle reports future Gradle 10 deprecations. No lint baseline or global analyzer threshold relaxation was added.

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Glance's WorkManager dependency contributes normal ACCESS_NETWORK_STATE for its internal widget infrastructure; it adds no INTERNET permission. Timer deadlines still use the engine/alarm/service path. LeakCanary's legacy storage permissions remain debug-only; no storage permission was requested or granted.

Phase 3 source commits: `ade51e5` dependency pins; `da8148e` sensor/screen-lock runtime; `e719d7d` supporting surfaces; `0cd9cbb` Settings, appearance, permission flows and UI regressions. Prior owner and phase commits are preserved.

## Implemented in Phase 2

- Design-based idle/running/finished Home, connected Expressive presets, bounded circular duration slider, M3 circular wavy progress, dynamic color roles and shared-bounds motion.
- Keyboard arrows/Home/End, semantic TalkBack range/custom actions, whole-minute haptics, tabular fitting countdowns, narrow/large-font scrolling, RTL and expanded two-column layout.
- Lifecycle-aware Home ViewModel connected to durable engine selection/start/cancel/adjust commands; elapsed-time display updates, current end-time formatting and honest completion states.
- Validated Custom duration sheet, 90 min/2 h/3 h choices and real starts up to 12 hours.
- Functional Nightly time/repeat-day/duration sheet and supporting exact-alarm scheduler, with zone-aware recurrence, stale-delivery validation and occurrence deduplication across cancellation/history rotation. It does not replace an active timer or assume WIU.
- Basic readiness sheet with real system-access states and links; real 10-second test entry/result dialogs; notification requests occur after starting the FGS. Full reliability/permission explanations and Settings rows remain Phase 3.
- Separate durable completed-test evidence so ordinary runs and cancelled tests do not erase readiness's last test. Optional schema-1 fields preserve older snapshots.
- Actual capability gates hide the shake card and screen-lock pipeline step until their runtime features exist. No inert enable switch or fake active sensor is exposed.
- Three visually reviewed native screenshot baselines and new interaction, formatting, drag, DST, persistence and occurrence regressions.

## Historical Phase 2 verification

| Check | Observed result |
|---|---|
| `./gradlew formatAll --console=plain` | **PASS** |
| `./gradlew assembleDebug testDebugUnitTest lintDebug spotlessCheck detekt :feature:home:verifyRoborazziDebug --continue --console=plain` | **PASS**, BUILD SUCCESSFUL in 11 s; 889 tasks, 51 executed / 838 up-to-date |
| Test XML totals, including pure-model JVM tests | **150 passed**, zero failures/errors/skips |
| Home interaction tests | **5 passed**: presets/range/keyboard, thumb/start/stop/reduction, 320 dp + 200% font + RTL, Custom bounds, expanded layout |
| Screenshot verification | **3 passed**: idle dark/light and configured running dark, 390 dp, native graphics |
| Additional new tests | Dial drag 3; DST/recurrence 4; formatting/input 3; engine selection/Nightly history/last-test regressions 3 |
| Phone install/open | **PASS** for a Phase 2 APK on Moto g54 5G / API 35; idle Home visually inspected |

The screenshots are regression baselines rendered from the implementation, not pixel-identical copies of the mockups. The configured running screenshot includes future capabilities solely as explicit test inputs; production presentation remains honest. No baseline or global analyzer threshold relaxation was introduced. Narrow commented exceptions retain saveable sheet/coordinator ownership without spreading ViewModels through render components.

The owner's intermediate commit `033b6d1` was preserved. Follow-up runtime safeguards are `7afa087`; Home fixes/tests are `375fcff`; phase notes are a separate commit.

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

## Phase 1 verification previously completed

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

- **Phase 1 device definition of done remains unmet:** the two-minute Spotify screen-off/forced-Doze stop within one second, actual fading and safe physical restore remain unmeasured. The app is not production-ready.
- Native fade/restore currently require API 33+, one connected output matching the media-route prediction, observable media sessions and safe interruption state. Ambiguous routes and all API 26–32 skip volume changes. Ordinary phone/Bluetooth routes may therefore stop without fading. See [playback safety](../core/playback/README.md); do not claim universal fade support.
- Foreground startup denial, exact-alarm revocation, sticky restart, real process death, reboot/update, force-stop, low memory, restricted bucket, OEM survival, long runs and actual wake-lock/battery behavior need runtime tests. Host tests cover the reducer and adapters only. `am kill` can leave an FGS process alive and is not automatically process-death evidence.
- Android 15 force-stop inspection uses the current PID's startup history. Older versions lack that unambiguous flag; detection is incomplete there. Interrupted results are persisted; their complete in-app explanation belongs to the later UI/reliability flows.
- A call/alarm deferral can hold a bounded two-hour wake lease with polling. Battery cost and callback refinement need hardening measurements. DND/Bedtime vibration behavior remains unverified on a device.
- Home and complete Settings are implemented. Nightly background delivery, physical accessibility, screen-lock consent/work profiles, ordinary/wake-up sensor delivery and real shake false positives remain unverified. Vibration mode behavior and brightness/cold-start continuity need device checks. The shake-enabled final alarm currently uses the conservative five-minute window even on devices with a wake-up sensor or no accelerometer; battery optimization of that policy remains hardening work.
- Android 17 hardening/WIU start-path behavior is unverified. No emulator/system image or JDK 21 was installed. Robolectric API 36–37 requires Java 21, so approved JDK 17 coverage uses earlier APIs.
- Phase 0 API 26/37 emulator smoke checks remain deferred. Foundation and a Phase 2 APK opened on the Moto g54 5G / Android 15; this is not evidence for background playback stopping.
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

The Home now provides Start and the 10 s test chip. The debug command remains available for repeatable tests:

```sh
adb shell am start -W -n com.sujal.sleeptimer.debug/com.sujal.sleeptimer.MainActivity -a com.sujal.sleeptimer.debug.TEST
```

Allow notifications in Android app settings to see ongoing controls/results. [PHASE1_TESTING.md](PHASE1_TESTING.md) includes two-minute runs, controlled silent test-player modes, forced-Doze cleanup and recovery checks.

## Next step

Connect the phone and follow PHASE3_TESTING.md, then collect the pending Phase 1 Spotify/Doze evidence. The next implementation phase is **Phase 4 System surfaces**, after the owner's next continuation. Do not silently claim physical gates passed.
