# Progress

Last updated: 2026-11-13 (Asia/Kolkata).

## Current position

**Phase 6 release preparation is implemented and host verification passes. Phase 6 is not complete:** app-specific profile collection, physical performance/reliability/16 KB/signed-candidate smoke, production identity and external upload signing remain open. This task did not upload or publish a release. Stop cancels the timer and leaves playback running; expiry performs the playback-stop pipeline.

ADB listed no phone on November 13, including the new launch helper's connection check. The new debug/release/test APKs were built locally; no installation, permission grant, benchmark/profile run or user timer mutation was performed on a phone. Last observed phone evidence remains the November 7 Phase 4 Moto g54 5G / Android 15 idle/repeated-Custom smoke check.

## Implemented in Phase 6 preparation

- Optimized unsigned production APK/AAB with R8's full-mode defaults and resource shrinking. Examined generated/consumer Hilt, Glance and serialization retention rather than adding blanket keep rules. Production artifact inspection rejects unexpected permissions, debuggable/profileable release flags and known debug/benchmark DEX descriptors; the release runtime graph excludes LeakCanary and benchmark libraries.
- Validated candidate version overrides and all-or-none external signing inputs. Missing signing remains unsigned; partial signing fails immediately. No upload key, production credential or publisher identity was generated. Mapping/configuration and artifact checksums remain available with each build.
- Applied the pinned Baseline Profile consumer/producer plugin and explicit profileinstaller runtime dependency. Added two real profile-collection journeys and five startup/frame benchmarks. Their shared Kotlin fixture is compiled/checked only as instrumentation source; normal host builds never collect a profile or wait for a device.
- Built the actual nonMinifiedRelease/benchmarkRelease targets as the isolated `com.sujal.sleeptimer.benchmark`, labelled Sleep timer benchmark. Static APK inspection confirms both are non-debuggable and shell-profileable; the two test APKs self-instrument in their own packages. The optimized benchmark target uses the local debug key, separately from production release signing. Real instrumentation remains unrun.
- Added a standard-library APK/AAB native verifier with malformed ELF, 4 KB-only, program-header, ZIP-offset and prohibited runtime descriptor regressions. All eight shipped native entries pass inspection; Build Tools zipalign independently passes. Play split-APK and actual 16 KB execution remain separate gates.
- Isolated clock-only Home state from Route/layout/overlays. The dial consumes live state and the reduction control observes only its five-minute threshold. A Compose regression changes the countdown/control boundary while confirming no parent-screen recomposition. Physical tracing must still establish the stricter text-only invalidation/frame budgets.
- Added `scripts/run-phone.sh`: checks a selected phone before building, resolves the existing SDK/JDK, builds debug, installs with `adb -s ... install -r` and opens MainActivity. It preserves app data, grants no optional app permission and starts no timer. Missing/unauthorized/multiple devices receive actionable instructions. The no-device path and shell syntax were checked; actual launch remains pending.
- Completed [PLAY_SUBMISSION.md](PLAY_SUBMISSION.md), [RELEASE_TESTING.md](RELEASE_TESTING.md) and [CHANGELOG.md](../CHANGELOG.md): declaration/video/privacy/disclosure drafts, signing/version/rollout/listing plans, device/benchmark commands and explicit owner/QA gates. All existing manual cases remain pending until observed.
- Rechecked 34 Maven catalog versions on November 13; no newer catalog pins found. Required release lint found Gradle **9.9.1**, now checksum-pinned and verified on existing JDK 17.0.20.1. No SDK/JDK/emulator/global CLI installation or shell-profile edit occurred.

## Current verification — Phase 6 preparation

| Check | Observed result |
|---|---|
| Existing runtime/toolchain | Gradle **9.9.1**, launcher/daemon JDK **17.0.20.1**, existing SDK 37.0 / Build Tools 36.0.0 |
| `./gradlew formatAll --console=plain` | **PASS** under the new wrapper |
| `./gradlew verifyRelease --continue --console=plain` | **PASS**, BUILD SUCCESSFUL in 2 min 48 s; 1,386 tasks: 1,096 executed / 188 from cache / 102 up-to-date |
| Required checks within verifyRelease | Debug assembly, all JVM/Android-host tests, lintDebug, Spotless/ktlint, detekt, build-logic checks, image comparisons, profiling test APK compilation, release lint/APK/AAB and artifact audit |
| Host XML | **209 passed**, zero failures/errors/skips; Home now 22, other module counts unchanged from Phase 5 |
| Artifact-verifier tests | **9 passed**, including 32/64-bit header bounds, alignment/congruence, positive/negative ZIP layout and prohibited DEX markers |
| Image comparisons | **15 passed**; established images unchanged, no baseline regeneration |
| Actual profiling target APK builds | **PASS**, `:app:assembleBenchmarkRelease :app:assembleNonMinifiedRelease`, 58 s; correct isolated package/profileable/non-debuggable flags; self-instrumenting runner package bindings inspected |
| Optimized production artifacts | Unsigned APK **2,938,800 bytes**; AAB **6,359,214 bytes**; these are artifact bytes, not measured Play downloads |
| Native code | 8 matching APK/AAB ELF entries, initial PT_LOAD alignments all 16384; uncompressed APK native ZIP offsets pass verifier and `zipalign -c -P 16 4` |
| Production flags/permissions/dependencies | Not debuggable/profileable; allowlist passes, no INTERNET/USE_EXACT_ALARM/REQUEST_IGNORE_BATTERY_OPTIMIZATIONS or debug storage permissions; debug/benchmark dependencies absent from release runtime graph |
| Distribution guard failure paths | Missing app profile **rejected as expected**; absent upload signing **rejected as expected**; deliberately partial non-secret signing inputs **rejected as expected** |
| App-specific baseline profile | **PENDING**; no app profile source was fabricated. Packaged dependency binary profile exists and is not counted as collected app evidence |
| Phone helper | `bash -n` **PASS**; no-device run exits with connection guidance before building/installing |
| SDK local path | local.properties remains ignored and untracked |
| Device/profile/performance/signing/Play gates | **PENDING**, as listed in RELEASE_TESTING and PLAY_SUBMISSION |

The final artifact JSON is generated at `build/reports/release/artifacts.json`; retain it with mapping/profile/source-commit evidence for a signed candidate. Default candidate remains 0.1.0 / code 1, not a published version. No performance sample or release runtime pass is claimed.

Known build-tool limits: Gradle's Java download/URL probe timed out; the official distribution was downloaded separately, verified against the official SHA-256 and placed in the approved wrapper cache. The bootstrap checksum also matches the official 9.9.1 wrapper, URL validation remains enabled, network timeout is 60 s, and the actual wrapper/full build passed. AGP 9.5.1 `:app:tasks --all` realizes a Compose Preview task requiring disabled release unit tests; targeted host/release tasks pass. No lint baseline, analyzer threshold or benchmark error suppression was added. Existing test API/native stripping and Gradle 10 deprecation notices remain.

Phase 6 preparation source commits: `49db628` Gradle patch; `cc6fe51` clock composition isolation; `b2f61d2` artifact audit/tests; `d0340bf` signing/profile/benchmark build setup; `0a38cb5` phone launcher.

## Implemented in Phase 5

- Settings → Reliability → **Share diagnostics** opens the Android Sharesheet with a bounded, process-local report. The report contains relative elapsed times and internal state/alarm/wake/playback event codes, without player names, media metadata, run IDs or device identifiers. Nothing is shared until the user chooses a target. No file, network or storage permission was added.
- Fixed an interrupted-recovery explanation gap: force-stop, reboot/unknown boot and unsafe-late recovery results receive a real completion stamp and preserve the test flag. Home opens readiness once per interruption and retains the warning/battery fix; an application observer delivers a silent alert even when no timer service is needed. Posted recovery alerts are deduplicated across controller recreation. An interrupted test uses the explanation instead of stacking another result dialog.
- Added physical hinge-aware panes, accounting for Scaffold's window origin and RTL parent alignment; tabletop separates dial/controls above/below the hinge. Narrow and compact landscape dial sizes respect available space. A native RTL text regression exposed an autosizing overflow; filling the bounded countdown column restores large locale digits without changing established design snapshots.
- Completed dial keyboard Page Up/Down and TalkBack ±5-minute actions through the same bounded minute selection path. Debug builds generate en-XA/ar-XB resources. Added six reviewed native rendering cases for narrow/200% text, actual pseudo-locales/RTL, landscape and tabletop, with countdown overflow assertions.
- Home's display ticker aligns to elapsed seconds and stops reading immediately when its lifecycle collection ends. It does not schedule expiry. OEM mapping coverage includes every requested manufacturer and the generic fallback; physical vendor settings remain manual cases.
- Completed the §6 A–L manual matrix with stable case IDs, exact procedures, expected results, cleanup for ADB overrides and explicit pending evidence. Includes real players, Doze/overnight, volume/routes, calls/alarms, DND, sensors, permissions, process/boot/late recovery, surfaces, OEMs and accessibility.
- Audited 33 catalog references against official Maven metadata on November 12. Updated serialization to **1.13.0** and Roborazzi to **1.79.0**; retained the required Material 3 **1.5.0-rc01** exception. No SDK/JDK/emulator/global CLI installation or shell-profile change occurred.

## Historical verification — Phase 5

Commands used command-local JAVA_HOME for the existing JDK 17.0.20.1.

| Check | Observed result |
|---|---|
| `./gradlew formatAll --console=plain` | **PASS** |
| `./gradlew verifyFoundation :feature:home:verifyRoborazziDebug :feature:settings:verifyRoborazziDebug --continue --console=plain` | **PASS**, BUILD SUCCESSFUL in 20 s; 936 tasks, 88 executed / 848 up-to-date |
| All host test XML, including pure-model JVM tests | **208 passed**, zero failures/errors/skips |
| Image comparisons, included in host coverage | **15 passed**: Home 7, Settings 4, native widget previews 4 |
| Required quality checks | Debug assembly, unit tests, Android Lint, Spotless/ktlint, detekt and build-logic checks pass through verifyFoundation |
| New native images | Six reviewed; generated en-XA/ar-XB strings and explicit RTL geometry, 320/360/600 dp, font 1.0/1.3/2.0, light/dark subset, landscape/tabletop; established nine images unchanged |
| Final debug APK permissions | No INTERNET, USE_EXACT_ALARM or REQUEST_IGNORE_BATTERY_OPTIMIZATIONS; no new permission for diagnostics |
| SDK local path | local.properties remains ignored and untracked |
| Connected device / runtime gates | No device available; installation, real-player/Doze, long-run/OEM, TalkBack/sensors/DND and API 36.1/37 checks **pending** |

Test breakdown: app 19; model 4; data 20; design system 5; playback 37; system 31; timer 42; Home 21; Settings 11; notification 6; tile 4; widget 8. JDK 17 Robolectric coverage uses APIs 26/31/35 where configured; it does not establish API 36.1/37 runtime behavior.

The added checks caught and corrected RTL pane mirroring and native countdown overflow. Analyzer findings were refactored; no lint baseline, global threshold or established image was changed. The first new Settings capture needed an advancing test clock during LazyColumn scrolling; the final capture freezes only after scrolling settles. Temporary measurement output was removed. Existing test API and Gradle 10 deprecation warnings remain.

For phone testing, use the commands below, then [MANUAL_TEST_PLAN.md](MANUAL_TEST_PLAN.md). Existing targeted procedures remain in [PHASE4_TESTING.md](PHASE4_TESTING.md), [PHASE3_TESTING.md](PHASE3_TESTING.md) and [PHASE1_TESTING.md](PHASE1_TESTING.md). Phase 6 release/performance/signing work has not started.

Phase 5 source commits: `c3a7642` dependency pins; `ee76c3e` diagnostics; `1b6a5b1` adaptive/input rendering; `a586b3d` display ticker; `00ad30d` recovery explanations; `3e39595` OEM guide coverage.

## Implemented in Phase 4

- Eligible Android 16 QPR1+ Live Update requests using standard ProgressStyle, tracker dot and native countdown, with the ordinary silent notification retained on Android 15 and earlier. Configured fade/extend changes refresh the presentation; dismissal survives controller recreation and rejects stale revisions.
- Reactive Quick Settings tile with active/off state, localized remaining minutes, run-bound cancellation and stale-idle start protection. API 37 starts through a resumed transparent Activity as the conservative while-in-use path; actual hardening behavior remains unverified.
- Responsive Dial and Presets Glance widgets, theme/dynamic-color roles, native host chronometer, connected preset targets, ring renders on state/minute updates, zero periodic provider updates, and both widget choices in Settings. Versioned Glance state rejects stale process/boot data in favor of the initialized engine.
- Generated Android 15+ previews and visually reviewed light/dark static fallbacks for both widgets. Four native rendering regressions compare actual preview RemoteViews against those fallback images.
- Validated Start 30 min, Start 60 min and Custom time shortcuts targeting the correct package variant. MainActivity uses singleTop for repeated intent delivery, with Activity-retained UI events. Existing adaptive/themed moon icon layers are verified.
- Engine-owned replacement Undo restores a still-future previous deadline under a new run ID; changed/expired/stale replacements are rejected. Idle-only widget starts are checked inside the actor. A last-duration preference-write failure no longer prevents scheduling a durable timer.
- Application surface refreshes for run/settings changes, time/zone/locale/configuration changes and screen-on. The native widget chronometer supplies seconds without per-second app updates. Actual launcher latency remains a device measurement.
- Pinned AGP **9.5.1** and checksum-verified Gradle **9.9.0**, both exercised with the existing JDK 17. No SDK/JDK/emulator/global CLI installation or shell-profile change occurred.

## Historical verification — Phase 4

Commands used command-local JAVA_HOME for the existing JDK 17.0.20.1.

| Check | Observed result |
|---|---|
| `./gradlew verifyFoundation :feature:home:verifyRoborazziDebug :feature:settings:verifyRoborazziDebug --continue --console=plain` | **PASS**, BUILD SUCCESSFUL in 10 s; 936 tasks, 26 executed / 910 up-to-date |
| All host test XML, including pure-model JVM tests | **191 passed**, zero failures/errors/skips |
| Image regressions, included in the host test coverage | **9 passed**: Home 3, Settings 2, native widget previews 4 |
| Required quality checks | Debug assembly, unit tests, Android Lint, Spotless/ktlint, detekt and build-logic checks pass through verifyFoundation |
| Connected phone | Current debug APK installed; idle Home visually inspected; **3/3 repeated Custom launches** opened and dismissed the sheet; no run left active |
| Final debug APK permissions | No INTERNET, USE_EXACT_ALARM or REQUEST_IGNORE_BATTERY_OPTIMIZATIONS; promoted-notification permission present |
| SDK local path | local.properties remains ignored and untracked |
| Device-only surface gates | Tile/widget pinning and synchronization latency, launcher shortcut/Undo interaction, generated picker previews and API 36.1/37 promotion/hardening **pending** |

The phone exposed repeated Custom launches that did not reach a standard Activity's new-intent callback. The singleTop fix was rebuilt, installed and verified with three repeated explicit launches after removing the temporary trace. Host manifest tests alone did not catch this delivery issue. No timer, permission grant, widget pin or tile addition was performed by this smoke check.

Existing Home/Settings baselines were not rewritten. Widget light/dark fallbacks were rendered from the real widget views and visually reviewed against design/04-android-surfaces.png. No lint baseline or global analyzer threshold was relaxed. Existing test-helper deprecations and future Gradle 10 deprecation warnings remain.

Phase 4 source commits: `b92447b` build-tool pins; `ccc86ed` timer safeguards and Undo; `2345e70` notifications/tile/widgets; `356ee32` app integration and shortcut delivery. Prior owner and phase commits are preserved.

## Implemented in Phase 3

- Complete grouped Settings cards: fade slider, restore, extension/warning pickers, Nightly configuration, real tile/widget Add actions, dynamic color, Light/Dark/System, dimming, playback test and reliability guidance. Preferences use atomic DataStore transforms; the existing engine observes settings changes.
- Optional force-lock-only device admin, explicit biometric/PIN disclosure, Android activation, deactivation/uninstall path and external-revocation readiness. Locking follows a successful/quiet stop pipeline with a fresh playback/call check and effect-token guard; tests and cancellation never lock.
- Foreground-service-owned accelerometer registration, gravity-filtered alternating-peak detector, cooldown, accepted-command confirmation haptics, bounded ordinary-sensor final-window wake leases, registration-failure cleanup and service-destruction cleanup. The final alarm opens a five-minute shake window when shake is enabled; the ordinary-sensor screen-off trade-off is documented. Recorded real-device trace tuning is still pending.
- Application night-mode persistence on API 31+, dynamic/fallback application and widget schemes, minimum window brightness for visible running Home, and restoration on leaving. Legacy starting windows use the generated dark surface until preferences and the initial snapshot are ready; API 31+ keeps its matching launch scheme.
- Contextual notification then exact-alarm prompts after the timer starts, a remembered exact-alarm rationale, actual access refresh, counted critical readiness issues, optional media-session disclosure and manufacturer battery instructions/browser guides.
- Reused real 10-second test/Nightly flows from Settings, plus a completion-stamp guard that prevents an older stored test from appearing as the result of a new request.
- Supporting real QS tile and pin-capable Glance countdown widget so Settings has no inert Add row. State/minute refreshes and native host countdowns are connected. Full dial ring/responsiveness/previews, the presets widget, promoted notifications and shortcuts remain Phase 4.
- Material 3 **1.5.0-rc01**, Navigation 3 **1.2.1**, UI Automator **2.4.1** verified against Google's Maven metadata/POMs on November 5. No SDK/JDK/emulator/global CLI installation occurred.

## Historical Phase 3 verification

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
- Android 15 force-stop inspection uses the current PID's startup history. Older versions lack that unambiguous flag; detection is incomplete there. Interrupted results are persisted and now explained by Home readiness and a silent alert when allowed; older-platform force-stop identification remains incomplete.
- A call/alarm deferral can hold a bounded two-hour wake lease with polling. Battery cost and callback refinement need hardening measurements. DND/Bedtime vibration behavior remains unverified on a device.
- Home and complete Settings are implemented. Nightly background delivery, physical accessibility, screen-lock consent/work profiles, ordinary/wake-up sensor delivery and real shake false positives remain unverified. Vibration mode behavior and brightness/cold-start continuity need device checks. The shake-enabled final alarm currently uses the conservative five-minute window even on devices with a wake-up sensor or no accelerometer; battery cost and tuning of that conservative policy remain a pending device measurement.
- Android 17 hardening/WIU start-path behavior is unverified. No emulator/system image or JDK 21 was installed. Robolectric API 36–37 requires Java 21, so approved JDK 17 coverage uses earlier APIs.
- Phase 0 API 26/37 emulator smoke checks remain deferred. Foundation, Phase 2 and Phase 4 APKs opened on the Moto g54 5G / Android 15; this is not evidence for background playback stopping.
- Stable detekt uses no type resolution because its embedded compiler predates Kotlin 2.4. Kotlin compilation and Android Lint remain enabled. Gradle reports future Gradle 10 deprecations. The unsigned R8 candidate is now host-verified; generated app profile, physical performance/signed smoke, Play download size, OEM and privacy/submission review remain open Phase 6 gates.

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

Connect/unlock the phone, enable USB debugging and accept authorization, then run `./scripts/run-phone.sh` from the repo root. Play media and run the 10-second test; inspect Share diagnostics and the manual two-minute screen-off/forced-Doze Spotify case. For Phase 6, follow RELEASE_TESTING to collect the real app profile, rebuild and measure startup/frame timing on the isolated benchmark install, then close all critical runtime/16 KB/retention cases. The owner must confirm the permanent identity/privacy contact and supply external upload signing before a reviewed signed candidate or any Play upload. Phase 6 remains open until those gates have evidence.
