# Engineering decisions

Recorded 2026-11-03 (Asia/Kolkata). “Accepted” describes the planned design, not implemented or tested application behavior. Recheck library sources and current platform documentation before first use. See [PLAN.md](PLAN.md) and [PROGRESS.md](PROGRESS.md).

## D001 — Respect the documentation-only checkpoint

**Historical checkpoint, satisfied.** The owner initially narrowed Phase 0 to environment confirmation, ignored SDK configuration and planning. That checkpoint was committed as `95d885e` without implementation. The owner subsequently authorized the remaining Foundation work; D021 records that scope. Maintain PROGRESS with actual observed checks.

## D002 — Reuse the installed SDK and JDK

**Verified environment.** SDK: `/Users/sujalsunilchaudhary/Library/Android/sdk`. Homebrew JDK: `/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`, version 17.0.20.1. The default macOS Java shim and `java_home -V` did not find a registered runtime, but direct `java`/`javac` execution succeeds. A global Java registration, shell-profile edit or replacement JDK is unnecessary.

The owner approved installing API 37. The actual repository package is **`platforms/android-37.0`**, revision 2; `platforms/android-37` was rejected. Installation completed, and metadata identifies a release Android 17 SDK with preview SDK 0. Use ignored `local.properties` for the SDK and a shell-local `JAVA_HOME` for builds. No Android Studio, emulator, system image, CLI update or other SDK package was installed.

## D003 — AGP, Gradle and SDK baseline

**Verified planning baseline.** Latest stable AGP is **9.5.0**. Its compatibility table specifies Gradle **9.6.0**, JDK **17**, Build Tools **36.0.0** and maximum supported API **37**. Existing Build Tools 36.0.0 therefore suffice. The SDK catalog also lists 37.0.0 Build Tools; installing that is not a prerequisite for this baseline and was outside the approved API-platform addition.

Use `minSdk = 26`, base `compileSdk = 37` and `targetSdk = 37`. Repository package naming `37.0` does not mean writing a decimal Gradle API number. Re-evaluate versions when implementation starts and pin the resolved build configuration.

Sources: [AGP 9.5 compatibility](https://developer.android.com/build/releases/agp-9-5-0-release-notes?hl=en), [Android 17 SDK setup](https://developer.android.com/about/versions/17/setup-sdk), [Platform Tools releases](https://developer.android.com/tools/releases/platform-tools).

## D004 — Kotlin and compiler integration

**Accepted, build compatibility gate.** Current stable Kotlin is **2.4.21**. Use AGP 9's built-in Kotlin integration for Android modules, not `org.jetbrains.kotlin.android` or kapt. Use KSP for Hilt and align Compose/serialization compiler plugins with the selected Kotlin compiler. Pure JVM modules still use the appropriate JVM Kotlin plugin.

Kotlin's published fully tested AGP range for this compiler reaches 9.3.1, below AGP 9.5.0. Resolve and test the complete latest-stable plugin combination during Foundation before feature code. Record any incompatibility and evidence; do not silently downgrade or invent coordinates. This task does not create a version catalog or claim dependency resolution succeeded.

Sources: [Kotlin releases](https://kotlinlang.org/docs/releases.html), [KGP compatibility](https://kotlinlang.org/docs/gradle-configure-project.html), [built-in Kotlin migration](https://developer.android.com/build/migrate-to-built-in-kotlin), [AGP Kotlin integration](https://developer.android.com/build/releases/agp-9-0-0-release-notes).

## D005 — Material 3 Expressive and navigation

**Verified planning baseline.** The newest required Material 3 1.5 release is **1.5.0-beta02**; override Material 3 above the stable Compose BOM. The official BOM guide currently specifies **2026.09.00**; verify release metadata again before pinning. Stable Navigation 3 **1.2.0** exists, so use Navigation 3 for Home and Settings, with transient sheets owned by their destination.

`MaterialExpressiveTheme`, `MotionScheme.expressive()`, `ButtonGroup` and `CircularWavyProgressIndicator` exist. Several have graduated from experimental status during 1.5 development. Keep any required opt-ins and version-sensitive wrappers in `:core:designsystem`; do not add obsolete blanket opt-ins. Inspect the resolved beta02 source before coding; current AndroidX main is not proof of every pinned signature.

Sources: [Material 3 release history](https://developer.android.com/jetpack/androidx/releases/compose-material3), [Compose BOM guide](https://developer.android.com/develop/ui/compose/bom), [Navigation 3 releases](https://developer.android.com/jetpack/androidx/releases/navigation3), [Expressive theme](https://developer.android.com/reference/kotlin/androidx/compose/material3/MaterialExpressiveTheme.composable), [ButtonGroup](https://developer.android.com/reference/kotlin/androidx/compose/material3/ButtonGroup.composable), [wavy indicator](https://developer.android.com/reference/kotlin/androidx/compose/material3/CircularWavyProgressIndicator.composable).

## D006 — Preserve the design through semantic components

**Accepted.** Use all four PNGs as the visual contract; 1170 px corresponds to 390 dp. Their violet samples are not brand colors. Generate and validate Tonal Spot light/dark fallback palettes in the theme layer; UI uses semantic roles. Use the system font, imported Rounded Symbols and platform components, without decorative additions.

The circular duration control needs custom interaction, range semantics and keyboard actions; a progress indicator does not supply a circular slider. Keep it in the design system, while using the provided wavy progress and connected button components for their intended roles. Let content wrap/scroll on narrow/large-font screens. Follow §4.6 defaults even where the screenshots show different configured values. Runtime values such as end time are never copied as fake data from a screenshot.

## D007 — Serial state transitions, cancellable effects

**Accepted.** Use a single-consumer command processor with immutable state and durable revisioned snapshots. Long fade/stop/deferral/watchdog work runs in owned cancellable jobs; it cannot block delivery of Extend or Stop. Internal results carry run ID, revision and effect generation. Validate authority before each side effect, not just before launching a job. Use injectable elapsed realtime for every countdown/deadline.

Keep contracts in the pure model layer and bind Android implementations in the app. A notification-provider contract in the timer module allows immediate foreground promotion without a circular module dependency.

## D008 — Make delivery idempotency explicit

**Accepted.** Extend/Reduce are additive, so run ID alone does not make their delivery idempotent. Deduplicate command IDs within the relevant run and persist accepted delivery identity with its resulting state. Distinct user taps have distinct IDs. Nightly starts additionally identify their occurrence; alarm identities include run, kind and schedule generation. Validate all external payloads.

DataStore and AlarmManager are separate systems. Persist desired state and reconcile alarms; test process death before/after each commit/schedule boundary. PendingIntent extras alone are not identity. Use explicit immutable intents with identities that prevent stale updates/deliveries from mutating the current run.

Source: [PendingIntent identity and flags](https://developer.android.com/reference/android/app/PendingIntent).

## D009 — Doze backup alarms are best effort

**Platform correction and verification gate.** Allow-while-idle alarms can be delayed and reordered under idle rate limits. A final-window alarm followed by end+30 s backup is not two independent on-time guarantees. Keep the backup as a recovery opportunity; the acquired wake lock and elapsed-time scheduler own execution after final-window delivery.

Engineering inference: short/restarted timers and near-deadline reschedules require explicit quota-risk handling. Acquire immediately if inside the final window, conservatively protect known/uncertain near-term quota-risk deadlines, and test repeated short timers in forced idle. Do not close the reliability phase on an assumption that “exact” means unconditional delivery through all system states.

Source: [setExactAndAllowWhileIdle contract](https://developer.android.com/reference/android/app/AlarmManager#setExactAndAllowWhileIdle(int,%20long,%20android.app.PendingIntent)).

## D010 — Exact-alarm revocation can stop the app

**Platform correction.** The brief's live wake-lock fallback after revocation cannot be guaranteed: Android stops the app and cancels future exact alarms on access revocation. The permission-state broadcast concerns grants. Use a whole-run bounded wake lock when a run begins without exact access, and handle scheduling races. After revocation/force-stop, reconcile at the next viable launch and report interruption rather than inventing an always-running recovery path.

Sources: [exact-alarm access behavior](https://developer.android.com/develop/background-work/services/alarms#using-schedule-exact-alarm-permission), [permission broadcast](https://developer.android.com/reference/android/app/AlarmManager#ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED).

## D011 — WIU is capability provenance, not a public permission boolean

**Accepted, emulator verification gate.** Android 17 restricts background audio focus/volume; targeting 37 adds WIU requirements. No public query establishing the service's current WIU capability was identified. Persist start provenance and observed outcomes, but do not treat a restored `hasWhileInUse` field as authority after recreation.

Verify Activity, notification, widget, tile, nightly and sticky-restart paths separately. Background nightly runs use session pause/media PAUSE without assuming focus or volume rights. Treat silent readback rejection and exceptions, including `IllegalStateException` in hardening throw mode, as capability failure. Validate the actual hardening shell command on the emulator before use. A no-UI Activity must not be assumed to qualify as visible without evidence.

Source: [Android 17 background audio hardening](https://developer.android.com/about/versions/17/changes/bg-audio).

## D012 — Represent playback and routing uncertainty

**Accepted, public-API verification gate.** Local silence does not prove remote playback stopped. Combine local activity and optional session state; use confirmed/inactive/unknown outcomes rather than converting unavailable evidence to success. Public playback configurations may be anonymized and are not a universal route oracle. Connected device lists do not identify a third-party player's active output; historical `getAudioDeviceInfo()` is not a solution.

Use a route result that can be known, ambiguous or unknown. Validate a public, permission-appropriate route strategy before volume work; skip fade/restore when identity is insufficient. No hidden APIs or new Bluetooth permissions merely to guess. Remote verification failure must not trigger an unsafe automatic volume restore or a “Your player stopped” claim.

Sources: [AudioPlaybackConfiguration source](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/media/java/android/media/AudioPlaybackConfiguration.java), [AudioManager reference](https://developer.android.com/reference/android/media/AudioManager).

## D013 — Audio safety overrides uncertain escalation

**Accepted.** Re-evaluate mode/usage observations before the fade and throughout volume/focus/key effects. A call/alarm can begin after preflight. Unknown interruption observations are not proof that focus is safe; omit focus escalation when safety cannot be established. Never request focus over a known call/alarm. Do not send PLAY_PAUSE.

Try direct session pause, eligible focus, then PAUSE keys as specified, with verification and bounded retry. Keep first-attempt and confirmed-stop latency separate: the required multi-second verification budget cannot guarantee every uncooperative player's cessation within one second. Test the one-second target with cooperative players and document other outcomes.

## D014 — Guard every volume increase and expire restoration promptly

**Accepted safety resolution.** Automatic post-stop restore requires confirmed silence, settling time, original route, unchanged user control and another check immediately before writing. Unexpected readback may indicate user action, Bluetooth lag or platform rejection; abort rather than repeatedly fighting it. Respect safe-volume caps.

Resolve the brief's conflicting restore windows (§5.2: service ≤2 min; §6.D: device return ≤12 h) in favor of the **short 2-minute automatic restore window**. Interrupted-fade state can survive process death only with a persisted expiry and the same safety gates; a later launch does not reset the window. Discard stale restores rather than raising volume hours later.

The owner's explicit Extend/Cancel action may invoke the brief's gentle approximately 1-second fade-cancellation ramp while playback continues. It is distinct from automatic post-stop restore, capped at the captured pre-fade index and guarded against route/user changes. If those checks fail, leave volume unchanged.

## D015 — Guard the API 26–27 volume floor

**Compatibility adaptation.** `getStreamMinVolume()` starts at API 28. On API 26–27, use a conservative non-muting fade floor of index 1 only when the stream maximum and captured volume permit lowering to it; otherwise skip the fade. Read back every write and abort on clamping/mismatch. Test both APIs and fixed-volume devices before claiming support. Never call the API 28 method unguarded or use a mute operation.

Source: [getStreamMinVolume API level](https://developer.android.com/reference/android/media/AudioManager#getStreamMinVolume(int)).

## D016 — Scope resources to each execution phase

**Accepted.** Every wake lock has an elapsed deadline, timeout and `finally` release. The final-window lock is not sufficient by itself for a 2 h call/alarm deferral; transition to a separately bounded deferral scope and cancel promptly when superseded. Cancel sensors/listeners/effects before service shutdown. Record lock durations locally without user/app identity.

Non-wake-up accelerometer support adds a last-five-minute wake window; whole-run exact-access fallback costs more again. Measure these separately from the short final-window baseline. Preserve the brief's explicit reliability trade-off rather than claiming all modes have negligible drain.

## D017 — Warning vibration respects system policy

**Accepted, device verification gate.** Classify the one-minute timer warning with alarm-appropriate vibration attributes, using the compatible API-specific form. Verify default Silent, Vibrate, DND and Bedtime behavior and devices without amplitude control. Respect system/user suppression; do not request a DND bypass privilege or promise an unconditional bypass. Nonprivileged apps cannot force the bypass flag to work.

Source: [VibrationAttributes interruption policy](https://developer.android.com/reference/android/os/VibrationAttributes#FLAG_BYPASS_INTERRUPTION_POLICY).

## D018 — User Stop cancels; expiry stops playback

**Owner-confirmed product decision.** Stop on Home, notification or tile cancels the timer and leaves playback running. Cancellation invalidates pending effects/alarms and applies safe fade-cancellation recovery if applicable; it does not pause playback or acquire new audio focus. A repeated Stop delivery is harmless. Mark a cancelled nightly occurrence so it cannot restart that night. Actual expiry and the 10-second test use the real stop pipeline.

## D019 — Privacy, backup and platform surfaces

**Accepted.** No INTERNET, analytics/network SDK, unrestricted-battery-request permission, USE_EXACT_ALARM or accessibility service. Back up versioned settings only; exclude timer snapshots, command recovery state and diagnostics from backup. Optional notification-listener/device-admin access remains contextual, disclosed and revocable. Export local diagnostics only through an explicit user share action.

Keep Android 15 notification fallback as a required path. Promotion is a system eligibility decision, not a guarantee from calling an API. Use the system chronometer rather than per-second widget updates. Permissions never block starting a timer; the foreground-service start precedes contextual prompts. Validate notification dismissal and generated-widget-preview APIs in the surface phase against resolved sources.

## D020 — Honest completion and remaining unknowns

**Accepted.** Foundation host checks cannot establish overnight survival, real shake thresholds, Bluetooth safety, DND behavior, OEM behavior or release performance. Foundation adds the wrapper/catalog/modules/theme/navigation and runs available checks. Runtime checks remain pending when a device/emulator is unavailable; do not relabel them passed. Actual outcomes are in PROGRESS.

No further product answer is needed for Foundation. Production application ID, publisher and signing identity remain release inputs. The Foundation catalog now pins verified dependency versions; future feature behavior remains unimplemented.

## D021 — Implement only the approved Foundation

**Accepted.** The owner's continuation authorizes build logic, listed module boundaries, theme, symbols/icons, splash, edge-to-edge and a two-destination navigation shell. Empty themed screen bodies are explicitly the Foundation deliverable in §13; do not add fake timer state, inactive feature buttons or simulated playback. Data/timer/system/surface/testplayer/benchmark modules contain build boundaries only until their phases.

Use provisional development namespace/application ID `com.sujal.sleeptimer`, with `.debug` application suffix. This does not settle production publisher identity. Keep testplayer release variants disabled and no dependency from the app to that test tool.

## D022 — Pin current artifacts and verify the actual combination

**Accepted.** Official Gradle release metadata selects **9.8.2**, newer than AGP's 9.6.0 minimum/default; use the latest stable version required by the brief. The wrapper JAR and distribution SHA-256 match official metadata and the distribution hash is committed. No global Gradle installation is needed.

All explicitly versioned catalog libraries were checked against official Google Maven or Maven Central POMs; BOM-managed Compose aliases were checked in stable BOM 2026.09.00. Material 3 remains the sole prerelease exception, 1.5.0-beta02. See `gradle/libs.versions.toml` for pins. The actual build/quality outcome, including compatibility limits, is recorded in PROGRESS rather than assumed from metadata.

Disable Gradle's Android SDK auto-download with `android.builder.sdkDownload=false` to preserve the owner's approval requirement for SDK changes. Maven/Gradle project dependency downloads are part of the approved build work.

Sources: [Gradle current-release metadata](https://services.gradle.org/versions/current), [Google Maven](https://dl.google.com/dl/android/maven2/master-index.xml), [Maven Central](https://repo.maven.apache.org/maven2/), [SDK auto-download control](https://developer.android.com/studio/intro/update).

## D023 — Share real quality tasks across module types

**Accepted.** Root aggregate tasks cover application/library debug assembly, host tests and lint, plus formatting/static analysis for every module and build logic. Android test-only modules have assembly tasks but no `lintDebug` in AGP 9.5; do not depend on a nonexistent task. They still receive formatting/detekt and their actual test code is reviewed in the relevant phase.

Use latest stable detekt 1.23.8 as required, with type resolution disabled because its embedded Kotlin compiler is 2.0.21. Kotlin compilation and Android Lint remain enabled. Do not replace it with the available detekt 2 alpha or claim type-aware detekt coverage. Explicitly configure ktlint 1.8's Compose annotation naming exemption in the formatter bridge; retain the other naming checks.

Sources: [detekt compatibility](https://detekt.dev/docs/introduction/compatibility/), [pinned ktlint naming rule](https://raw.githubusercontent.com/pinterest/ktlint/1.8.0/ktlint-ruleset-standard/src/main/kotlin/com/pinterest/ktlint/ruleset/standard/rules/FunctionNamingRule.kt).

## D024 — Generate coherent fallback and launch surfaces

**Accepted.** Official Material Color Utilities 0.4.0, Tonal Spot, seed `0xFF63597F`, 2025 specification/phone platform/standard contrast generate both 48-role palettes. The dark surface, primary and primaryContainer exactly match the brief's samples. Small generated differences in other roles are preserved. The utility is not an app runtime dependency; provenance and symbol attribution are in the design-system README and NOTICE.

Match the default dynamic launch surface: on API 31–33, derive neutral-variant tone 98/light and 6/dark using color-list `lStar` from `system_neutral2_600`; on API 34+, use `system_surface_light/dark`. These mappings were verified against the pinned Material 3 source/AAR. Below 31 use the generated fallback. API 31 host tests observed a one-unit RGB-channel difference between framework and Compose CAM quantization, so tests permit at most one unit per RGB channel there and require exact equality on API 34+. Real cold-launch continuity remains a device smoke test. The future Dynamic color off preference must also select a coherent launch policy when implemented.

Sources: [Material Color Utilities](https://github.com/material-foundation/material-color-utilities), [color-list resources](https://developer.android.com/guide/topics/resources/color-list-resource), [Android 12 color modulation](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android12-release/core/java/android/content/res/ColorStateList.java).

## D025 — Run host tests within the approved JDK

**Accepted.** Robolectric 4.17 can model API 37, but APIs 36–37 require Java 21. Keep the approved JDK 17 and run navigation on APIs 26/35 and dynamic launch checks on 31/35. Do not install another JDK or substitute host simulation for API 26/37 emulator smoke testing. These tests cover UI behavior, composition saved state and palette contracts, not a real process death or physical display behavior.

Sources: [Robolectric compatibility](https://robolectric.org/compatibility_table/), [Navigation 3 saved state](https://developer.android.com/guide/navigation/navigation-3/save-state).

## D026 — Use compatible icon and system-bar defaults

**Accepted.** Keep one adaptive icon in `mipmap-anydpi`, since every supported device is API 26+. Include its monochrome layer in that resource: Android 8's verified parser skips unrecognized children, while Android 13+ consumes it. This avoids duplicating the icon solely for the monochrome child. Remove unused XML-only palette exports and the duplicate moon drawable; all Compose color roles remain intact.

Let Activity's `enableEdgeToEdge()` select supported navigation-bar contrast flags rather than referencing the API 27 navigation-bar attribute in an API 26 base style. Target 37 receives predictive-back behavior by default; Navigation 3 handles the back stack without a redundant newer manifest attribute. Gesture animation and actual API 26 icon rendering remain device checks.

Sources: [Android 8 adaptive-icon parser](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-8.0.0_r1/graphics/java/android/graphics/drawable/AdaptiveIconDrawable.java), [predictive-back defaults](https://developer.android.com/develop/ui/compose/animation/shared-elements/navigation).

## D027 — Give saveable navigation keys JVM-visible serializers

**Verified correction.** Navigation 3's Android default NavKey serializer discovers object serializers reflectively. File-private destination objects caused `IllegalAccessException` during initial composition in the real host tests. Keep the two keys Kotlin `internal`, which produces JVM-visible object classes while keeping the Kotlin API scoped to the app module. Preserve `@Serializable` and `rememberNavBackStack`; do not discard saved-state behavior to avoid the failure. The navigation tests exercise both transitions and saved-state restoration.

## D028 — Make app-bar action bounds explicitly accessible

**Accepted.** The pinned Expressive icon button reported a 40 dp semantic layout width in host tests. Its platform touch expansion may be larger, but the brief requires reliable 48 dp targets and accessible traversal. Give both app-bar icon buttons explicit 48 dp layout bounds, retaining the supplied M3 component and 24 dp symbol. Test these bounds in normal and 200% font/RTL navigation rather than relying on invisible expansion.

## D029 — Gate execution on an actual foreground service

**Accepted for Phase 1.** Creating the application graph restores durable state but does not acquire runtime wake locks. The service promotes immediately, then enables engine execution. Losing the service releases runtime effects while preserving alarms. A valid alarm may run a bounded stop handoff even when foreground startup is denied; it cannot manufacture Android 17 audio privileges. Persisted start provenance is never used as proof that a recreated service has while-in-use capability.

Notification actions carry the expected run ID. Separate taps receive separate delivery IDs, while explicit retried commands are deduplicated. Stop durably records cancellation before recovering a partial fade, so restarting during cancellation cannot accidentally pause playback.

Sources: [foreground-service launch](https://developer.android.com/develop/background-work/services/fgs/launch), [special-use service type](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use), [Android 17 background audio](https://developer.android.com/about/versions/17/changes/bg-audio).

## D030 — Prefer skipping an uncertain fade over an unsafe restore

**Accepted with a material limitation.** Public playback configurations may be anonymized, and connected audio devices do not identify another app's actual route. Fade/restore therefore require an observable media-session state and, on API 33+, a single connected output agreeing with the public media-routing prediction. Ambiguous routes and API 26–32 skip volume changes. Playback stopping still uses available media sessions, safe focus requests and PAUSE keys. Without notification access, test results explicitly describe local playback only. This conservative policy means universal fade support is not implemented and must not be claimed in device results.

Normal `MODIFY_AUDIO_SETTINGS` is required for stream-volume changes and was missing from the brief's permission table; include it without adding any network permission. Read back every write, stop on user/route changes, and expire automatic restoration after two minutes. An explicit cancel/extend may ramp back gradually under the same route/index guards.

Sources: [AudioManager](https://developer.android.com/reference/android/media/AudioManager), [active playback configurations](https://developer.android.com/reference/android/media/AudioManager#getActivePlaybackConfigurations()), [audio-device routing prediction](https://developer.android.com/reference/android/media/AudioManager#getAudioDevicesForAttributes(android.media.AudioAttributes)).

## D031 — Keep Phase 1 controls in a debug test harness

**Accepted.** The production Home layout arrives in Phase 2. For Phase 1, debug-only Activity actions exercise the real service/engine from a visible Activity; release builds ignore these actions. A separate debug-only test player uses a bundled local tone, real MediaPlayer/MediaSession and selectable focus behavior. Its default output is silent at the player, without changing the shared music-stream volume. Basic silent notifications provide cancel/extend; promoted notifications remain Phase 4.

Call/alarm deferral uses a bounded two-hour wake lease with elapsed-time polling. This is conservative and potentially costly during a long call; battery measurements and callback-based refinement remain hardening work. No exact-alarm or notification permission dialog is exposed before the contextual flows in Phase 3, so debug tests must record the permission state and may exercise the full-run wake-lock fallback.


## D032 — Preserve fail-closed guards and isolate failures at the actor boundary

**Accepted.** Split the engine into command, execution, timing and rule files; bundle explicit ports in `TimerDependencies`. Playback stages are separate helpers. Retain narrow, commented detekt exceptions for early safety returns and unexpected storage/OEM binder failures. Cancellation is always propagated separately. No lint baseline, global analyzer threshold increase or disabled safety check is used.

Android 15's `ApplicationStartInfo.wasForceStopped()` is the unambiguous stopped-package signal. Query the current PID's startup record before restoring an active run, cancel that run, and retain an interrupted result rather than silently resume it. API 30+ exit reason codes are recorded locally when available. Older platforms lack this startup flag; force-stop detection remains incomplete there, and no claim of surviving force-stop is made. Device behavior still needs verification.

Sources: [ApplicationStartInfo.wasForceStopped](https://developer.android.com/reference/android/app/ApplicationStartInfo#wasForceStopped()), [Android 15 stopped-package behavior](https://developer.android.com/about/versions/15/behavior-changes-all#stopped-state), [MediaPlayer state and resource ownership](https://developer.android.com/media/platform/mediaplayer/state-resources).


## D033 — Wire Home to durable commands and real capabilities

**Accepted for Phase 2.** Home observes the existing engine through a lifecycle-aware ViewModel. Display ticks recompute elapsed remaining time; they never schedule playback stopping. Duration selection is a serial engine command, and user Stop remains cancellation. Show the finished message only for a confirmed stop, with honest messages for silence, failure and interruption.

Nightly's Home sheet requires a functional alarm-backed schedule, so its narrow scheduling dependency lands with this phase rather than exposing an inert switch. Complete Settings and OEM guidance remain Phase 3. Shake presentation checks actual monitoring capability; a stored default-on preference alone must not promise working detection. Screen-lock steps likewise require a working capability.

The pinned Material 3 1.5.0-beta02 source supplies connected toggle shapes, expressive Button shapes, Text auto-sizing, wavy progress and TimePicker. Keep experimental Expressive opt-ins in the design system. Use shared bounds and the theme's expressive spatial/effect specs for dial motion. At narrow widths/large fonts, allow scrolling and wrap controls. Countdown text uses system-font tabular figures and fitting only inside the dial; ordinary copy follows font scale.


## D034 — Use the verified current Compose equivalents

**Verified against the pinned source.** Compose animation 1.12.1 calls the shared-bounds resize factory `SharedTransitionScope.ResizeMode.scaleToBounds()`. Adaptive 1.3.0 replaces `currentWindowAdaptiveInfo()` with `currentWindowAdaptiveInfoV2()`. Material 3 1.5.0-beta02 replaces the old modal-sheet state helper with `rememberBottomSheetState(Hidden, enabledValues = {Hidden, Expanded})` and provides ListItem's trailing content/click overloads. Use those APIs and keep the required Expressive opt-in contained in the design system.

For the 286 dp running indicator, use the supplied CircularWavyProgressIndicator with an 80 dp preferred wavelength and gentle 10 dp/s wave travel. The pinned implementation returns a flat initial path for fractional amplitude before a Morph exists. Begin at full amplitude and let the final-three-percent change create its supported flattening animation; do not substitute a hand-drawn wave. Clockwise progress and elapsed track remain the library's behavior. Disabled animations flatten the wave.

Sources: [pinned Material sources](https://dl.google.com/dl/android/maven2/androidx/compose/material3/material3-android/1.5.0-beta02/material3-android-1.5.0-beta02-sources.jar), [pinned animation sources](https://dl.google.com/dl/android/maven2/androidx/compose/animation/animation-android/1.12.1/animation-android-1.12.1-sources.jar), [pinned adaptive sources](https://dl.google.com/dl/android/maven2/androidx/compose/material3/adaptive/adaptive/1.3.0/adaptive-1.3.0-sources.jar).

## D035 — Schedule one Nightly occurrence and retain completed test evidence

**Accepted.** Nightly computes the next selected local date using java.time and the current zone. A DST gap moves forward by its gap; an overlap uses the earlier offset once. Store a separate last-nightly command identity so cancelling an occurrence cannot allow it to restart after the bounded general command history rotates. Validate alarm configuration/occurrence, reject early or over-five-minute-late deliveries, and reschedule after delivery, relevant broadcasts and schedule changes. Starting Nightly while another timer is active only records the skipped occurrence; it does not restart that service or interrupt its fade.

Nightly requires exact-alarm access; its sheet explains that access before enabling the schedule. It starts without WIU and retains the conservative Android 17 audio limits. Device delivery remains unverified. Snapshot schema 1 gains optional fields for the nightly identity and last completed test result. Recover the previous test-result format when present, keep test evidence across normal runs/cancelled tests, and retain the settings-only backup policy.

Sources: [LocalDateTime zone resolution](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/LocalDateTime.html#atZone(java.time.ZoneId)), [Compose semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics), [shared bounds](https://developer.android.com/develop/ui/compose/animation/shared-elements).

## D036 — Settings, consent and feature ownership

**Accepted (Phase 3).** Settings writes the existing validated store, then commands engine reconciliation. Reuse the real Home Nightly and test flows rather than create a second runtime. Keep expressive component opt-ins in the design system. Add tile and a functional pin-capable dial widget as dependencies of the Settings Add actions; defer their full Phase 4 presentation and additional surfaces.

Device admin declares only force-lock, is opt-in through Android, and is removable from Settings. Lock only following a successful/quiet non-test pipeline under its current effect token, after volume recovery and with a fresh call/interactive check. External revocation disables the preference and is surfaced to the user.

Sources: [DevicePolicyManager](https://developer.android.com/reference/android/app/admin/DevicePolicyManager), [sensor background restrictions](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview), [wake-up accelerometer](https://developer.android.com/reference/android/hardware/SensorManager), [application night mode](https://developer.android.com/reference/android/app/UiModeManager), [window brightness](https://developer.android.com/reference/android/view/WindowManager.LayoutParams).

## D037 — Honest shake capability and appearance startup

**Accepted.** Register only during a running FGS. Prefer a wake-up accelerometer; with an ordinary accelerometer, monitor while interactive or during the last five minutes, with a timed partial wake lease in that final interval. Re-evaluate on screen and engine/settings changes and clean up synchronously on service destruction. The detector uses three alternating, gravity-filtered peaks, a 1.2-second window and a three-second cooldown. Synthetic tests are not evidence of real-device false-positive tuning.

Hold the splash until settings and snapshot initialization, apply minimum window brightness before drawing an active Home, and restore the original override when leaving it. Use application night mode on API 31+ and Compose theme selection below. Window brightness never keeps the screen on and never changes the global brightness setting.

## D038 — Follow-system night mode and supporting surfaces

**Verified November 5.** Although UiModeManager's general AUTO documentation describes twilight, AOSP's per-application `setApplicationNightMode` maps AUTO/CUSTOM to `UI_MODE_NIGHT_UNDEFINED`, clearing the app override. Use AUTO for System; do not map System to the current app-local YES/NO state, which would pin it. API 31+ persists this through Android, while older releases apply the selected Compose theme. The initial pre-31 resource-theme policy was superseded by the night-safe starting surface in D040; physical cold-start visual checks remain pending.

The pinned Glance 1.2.0 source confirms `material3.ColorProviders(light, dark)` and `AndroidRemoteViews`. Apply the app's generated/dynamic schemes and forced theme to widgets, including their native chronometer text. Push state changes and minute refreshes; the chronometer counts down in the host. The Phase 3 pin target is functional but its dial ring, responsive previews and the presets widget remain Phase 4 work. The tile starts without assuming WIU; Android 17 behavior remains a device gate.

Sources: [AOSP UiModeManagerService](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/UiModeManagerService.java), [Glance themes](https://developer.android.com/develop/ui/compose/glance/theme), [pinning widgets](https://developer.android.com/develop/ui/views/appwidgets/discoverability).

Glance's transitive WorkManager manifest contributes ACCESS_NETWORK_STATE, observed in the final debug APK. This is internal widget infrastructure; the app declares no INTERNET permission and does not use WorkManager for timer deadlines. The existing debug-only LeakCanary storage permissions remain debug-only. No device permission was granted by this task.

## D039 — November 5 catalog refresh

**Accepted.** The required GradleDependency lint detected newly available official artifacts while Phase 3 continued across the date boundary. Pin Material 3 **1.5.0-rc01**, Navigation 3 **1.2.1** and UI Automator **2.4.1**, preserving the explicit Material 3 1.5-line exception. Verify compilation, navigation and screenshot regressions after this change; do not suppress the dependency lint or silently rewrite visual baselines. These are project dependency updates, not SDK/JDK installations.

Sources: [Material 3 releases](https://developer.android.com/jetpack/androidx/releases/compose-material3), [Navigation 3 releases](https://developer.android.com/jetpack/androidx/releases/navigation3), [UI Automator releases](https://developer.android.com/jetpack/androidx/releases/test-uiautomator).

## D040 — Night-safe legacy splash and fresh test evidence

**Accepted.** On API 26–30 Android cannot persist the app night-mode override through UiModeManager. Use the generated dark surface for the starting window in both system modes; hold it until settings/snapshot readiness and the selected Compose theme can draw. This avoids a light starting flash for explicit Dark when the system is Light. A Light selection transitions from that dark starting surface to its intended light screen. API 31+ retains the matching system/application-mode launch resources. Physical cold-start visual checks remain pending.

A new 10-second request captures the engine's current completion stamp before service delivery. Its visible result observer ignores that older result until a new completion arrives. Recomposition while a previous test is still stored must never be presented as a new successful player check. A regression drives old result → new running state → new completion through the actual Compose observer.

## D041 — System-surface behavior and API boundaries

**Accepted for Phase 4.** Request Live Update promotion only on Android 16 QPR1 (36.1) or later when the system permits it, using standard ProgressStyle and a countdown `when` rather than repeatedly posting second-by-second chip text. API 35 retains an ordinary ongoing chronometer/progress notification. Promotion remains the system's decision. Never turn notification dismissal into timer cancellation.

Widgets use Glance with RemoteViews interop for the host chronometer and rendered ring bitmap. Render the ring at state changes and at most once per minute; no second-by-second app widget updates or periodic provider wakeups. Generate previews independently from user state and retain static preview fallbacks. Shortcut and widget inputs are explicit and bounded; Undo is run-bound and cannot resurrect an expired timer or overwrite a newer command.

Sources: [Live Updates](https://developer.android.com/develop/ui/views/notifications/live-update), [Glance generated previews](https://developer.android.com/develop/ui/compose/glance/generated-previews), [static shortcuts](https://developer.android.com/develop/ui/views/launch/shortcuts/creating-shortcuts), [TileService](https://developer.android.com/reference/android/service/quicksettings/TileService).

## D042 — Reactive widget state and conservative tile startup

**Accepted.** Pinned Glance source confirms that updateAll can reuse an existing session; a snapshot captured before provideContent will not become fresh by itself. Publish a versioned presentation through Glance preferences and read it in composition, rejecting older-boot/older-render data in favor of the freshly restored engine. Update both widget providers together on meaningful state changes and minute ticks, and refresh on screen-on, configuration, locale, time and zone changes. Generated previews use explicit preview data, never a user's run.

On API 37, tile start goes through a resumed transparent Activity, with an immutable explicit PendingIntent and show-when-locked, before invoking the visible-Activity gateway. This is the conservative WIU path; it does not claim emulator hardening evidence. API 26–36 retain direct user-tap service startup. Stops remain run-bound cancellation, including on the lock screen. Tile listening observes engine changes immediately; subtitle ticks occur only while listening.

Undo is an engine-owned, in-memory replacement record valid for 60 seconds and the same run/schedule generation. It restores the former still-future deadline under a new run ID, retaining warning history and safely recovering any current fade. It is unavailable after process death, expiry or another adjustment; it cannot restart playback.

## D043 — Preserve Undo identity when remembering a duration

**Accepted after a regression exposed the race.** A successful start now takes the settings object returned by the atomic last-duration write into the engine before its queued settings notification is processed. That notification becomes a no-op rather than spuriously incrementing the schedule generation and invalidating the same start's Undo. The timer snapshot already stores selection durably; a failed preference-only duration write is logged and must not prevent alarm scheduling or the stop pipeline. The same rule applies to restoring a previous deadline. Host regressions cover Undo, expiry, stale actions, idle-only widget races and a duration-preference write failure.

## D044 — November 7 build-tool patch refresh

**Verified.** Required lint detected AGP 9.5.1 and Gradle 9.9.0 after this phase resumed. Google's official AGP module metadata declares JVM 17, and the checksum-verified Gradle 9.9.0 wrapper ran successfully on the existing JDK 17.0.20.1. Pin the project versions and official distribution SHA-256; no SDK/JDK/emulator/global CLI installation occurred. Keep the required dependency/version lint enabled.

Sources: [official AGP module metadata](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/9.5.1/gradle-9.5.1.module), [official Gradle distribution checksum](https://services.gradle.org/distributions/gradle-9.9.0-bin.zip.sha256).

## D045 — Repeated shortcut delivery and Activity UI ownership

**Verified by phone tracing.** The first Custom launch was handled, but repeated task-front launches of the standard Activity produced no new-intent callback. Set MainActivity to `singleTop` so repeated launches reach `onNewIntent`, including the same action. Keep launch UI events Activity-retained rather than process-global: they survive rotation within an Activity and cannot be consumed by another Activity's UI. The timer engine/gateway remain process singletons. After removing the temporary trace, rebuilding and installing, three consecutive explicit Custom launches each opened and dismissed the real sheet on the Android 15 phone. Host manifest/input tests alone do not prove delivery; launcher presentation and fixed-start/Undo interaction remain device checks.


## D046 — User-initiated local diagnostic sharing

**Accepted for Phase 5.** Share a bounded plain-text report through Android's Sharesheet from Settings Reliability. Reuse a single process-local event ring for adapters and export; include event codes and relative elapsed durations, without wall-clock times, installed-player names, media metadata, device IDs or exception messages. No file provider, storage permission or network is needed for a small EXTRA_TEXT report. The report states that events are from this app process only; process death is covered by persisted timer results and the recovery inspector, not a claim of a durable diagnostic history. The user reviews/shares using the system chooser. API verified against [Android Sharesheet guidance](https://developer.android.com/develop/ui/compose/sharing/send).

## D047 — Hardening evidence and completion boundary

**Accepted.** Every brief §6 edge case receives a stable manual case ID and an automated test reference where coverage exists. A written procedure is coverage of the test plan, not a passed runtime test. The existing JDK 17 remains the approved host runtime; unavailable API 36.1/37, OEM, real shake traces, overnight and physical fade/restore results remain pending. Do not install tools or weaken lint to manufacture a pass.


## D048 — November 12 dependency audit

**Verified against official repository metadata.** Rechecked 33 catalog version references against Google Maven and Maven Central. Keep the existing stable pins and the required Material 3 1.5.0-rc01 exception; update kotlinx.serialization to **1.13.0** and Roborazzi to **1.79.0**, the two newer stable artifacts found. Required compilation, storage regressions and existing image verification gate these updates; do not regenerate established baselines to hide rendering changes.

Sources: [serialization metadata](https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-json/maven-metadata.xml), [Roborazzi metadata](https://repo.maven.apache.org/maven2/io/github/takahirom/roborazzi/roborazzi/maven-metadata.xml), [AndroidX release table](https://developer.android.com/jetpack/androidx/versions).


## D049 — Hinge coordinates, debug pseudo-locales and display cadence

**Accepted after layout audit.** Adaptive 1.3.0's pinned source confirms Posture.hingeList bounds are in window coordinates. Convert them using the actual content position after Scaffold insets, then give dial and controls separate independently scrollable panes; use an absolute-left parent alignment plus physical offsets so RTL does not move content onto a physical hinge. A regression caught that offsets alone still allowed the parent to mirror the pane origin. Compact normal layouts bound the dial to the available pane width. Preserve the existing 390 dp design baselines.

Enable generated pseudo-locales only in debug build types using the pinned AGP BuildType.isPseudoLocalesEnabled API. Test expanded/bidirectional text instead of treating layout direction alone as localization coverage. Dial Page Up/Down and TalkBack custom actions change five minutes through the same bounded selection path as dragging.

Home's display flow now samples elapsed time once at the next whole-second boundary and stops collection immediately when no lifecycle subscriber remains. It never schedules expiry; the engine/alarm/wake model remains authoritative. Frame/recomposition performance still requires Phase 6 measurement.

Sources: official [adaptive Posture reference](https://developer.android.com/reference/kotlin/androidx/compose/material3/adaptive/Posture) and the Google Maven adaptive-android 1.3.0 source archive; AGP 9.5.1's cached public BuildType interface.


## D050 — Deliver recovered interruptions outside the foreground service

**Accepted after finding a missed explanation.** Restored force-stop, boot mismatch and unsafe-late runs had an interrupted result with the default completion stamp. They could resolve before the service observer attached, leaving an idle screen with no alert or visible explanation. Stamp recovery results with the injected elapsed clock, retain the test flag consistently, and let the application surface observer deliver the silent interrupted alert without starting an otherwise unnecessary foreground service.

Home keeps interruption in readiness and opens its explanation once per result, with the existing battery guidance action. An interrupted test uses that explanation instead of stacking the ordinary test-result dialog. Notification delivery stores the posted recovery identity so controller/process recreation does not repost a dismissed alert. When notification permission is absent, Home still explains the interruption. Host regressions cover restored stamps/test identity, initialization and duplicate UI delivery, and alert dismissal across controller recreation; actual reboot/force-stop behavior remains a device case.

Diagnostics also record phase/deadline changes, outcome codes and the elapsed offset of the first playback attempt and initial verification. Those verification offsets precede the watchdog/restore cleanup; the final result timestamp must not be treated as the audible stop time. No run IDs, player names or media metadata are recorded.


## D051 — Bound numeric text before native RTL autosizing

**Verified by native text measurement.** The new ar-XB/130% screenshot showed small countdown digits. Its TextLayoutResult reported visual overflow even at the smallest auto-size, with a 54 px measured width inside an available 222 px column. Set the countdown Text to fill that bounded column before centering/auto-sizing. The same case then measured 222 px, reported no overflow and rendered large locale digits. Changing paragraph direction alone did not resolve the issue and is not part of the implementation.

Each new Home screenshot also checks the native countdown layout for overflow, including a 12-hour value at 200% font scale. The decorative text remains excluded from accessibility; the dial retains its single range/state description. Established 390 dp images must still pass comparison. This is host rendering evidence, with physical TalkBack/font/display-size cases still pending.

Reference: [Compose TextAutoSize](https://developer.android.com/reference/kotlin/androidx/compose/foundation/text/TextAutoSize).


## D052 — Release evidence and tooling boundary

**Accepted for Phase 6 preparation.** Continue with host-verifiable release work while the phone is disconnected. Build an unsigned optimized APK/AAB unless all externally supplied signing inputs are configured; never fall back to a debug key for production release. Preserve the current application ID/version as development candidate defaults until the owner confirms the permanent Play identity. Signing secrets, keystores, Play account actions and publishing are not created or performed by this task.

Baseline-profile generation and macrobenchmarks use explicit connected-device tasks and a separate profileable target. They are build-time/test dependencies, not shipped runtime code. A normal host verification/release build must not start an emulator, download SDK components or wait for a device. No generated profile or budget pass is claimed without collecting it. Artifact ELF/ZIP alignment, dependency/permission inspection and host tests are evidence only for those checks, not a physical release smoke test or a measured cold-start/overnight pass.


## D053 — Pinned profiling APIs and isolated test data

**Verified against official documentation and Benchmark plugin 1.5.0 sources.** Apply the Baseline Profile consumer/producer plugin, retain manual connected-device generation, and save real app-filtered profiles into source control only after collection. The generated nonMinifiedRelease and benchmarkRelease targets use the separate com.sujal.sleeptimer.benchmark application ID and the local debug signing configuration. Production release has no debug-key fallback. The test modules self-instrument in their own process, as required by Macrobenchmark; they do not instrument the R8 target process or consume its obfuscated classes. Shared journey source is compiled into the two instrumentation APKs only, using AndroidSourceSet.kotlin (AGP 9 built-in Kotlin does not compile added Java-source directories as Kotlin).

Startup compares no-profile and required-profile cold launches; frame journeys exercise dial drag, Settings scrolling and a visible running countdown/wave. Timers use 60 minutes so short performance runs cannot expire into another player's audio. Cancellation waits for idle Home and final instrumentation cleanup force-stops only the isolated benchmark package. The normal debug/release install and its settings are untouched by these journeys. English UI and an unlocked API 33+ phone are the documented profile-generation prerequisites.

References: [Baseline Profile configuration](https://developer.android.com/topic/performance/baselineprofiles/configure-baselineprofiles), [Macrobenchmark setup](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview), [metrics](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics), [built-in Kotlin sources](https://developer.android.com/build/migrate-to-built-in-kotlin). The pinned plugin source confirms disabled synthetic unit tests and manual generation defaults. AGP 9.5.1 task enumeration with --all currently realizes a Compose Preview task requiring disabled release unit tests; targeted build/verification commands are used, without weakening checks or re-enabling synthetic unit tests solely for previews.

## D054 — Release verification, versioning and signing

**Accepted.** Keep SemVer candidate versionName 0.1.0 and monotonically increasing positive versionCode 1 until a distribution version is explicitly chosen; allow validated sleepTimer.versionName/versionCode Gradle properties. All four SLEEP_TIMER signing environment inputs must be supplied together. Keep keys/passwords out of the repository, generated BuildConfig and logs; absent inputs produce unsigned release artifacts. Add ignore patterns for key material, not example keys.

Use R8's optimized defaults and transitive generated/consumer retention rules; initial release assembly needs no blanket package keeps. Preserve mapping/configuration output and require an optimized, non-debuggable, non-profileable production APK with the permission allowlist. The standard-library artifact inspector checks APK/AAB native identity, 64-bit PT_LOAD alignment/congruence/file bounds, uncompressed APK native ZIP offsets and absence of known debug/benchmark DEX descriptors. Malformed/4 KB-only ELF regressions exercise rejection paths. APK ZIP alignment is also independently checked with Build Tools zipalign. AAB native inspection does not prove Play-generated APK ZIP alignment or 16 KB runtime execution; those remain distribution/device gates.

verifyRelease performs host checks and permits unsigned/no-app-profile development candidates. verifyDistributionArtifacts additionally requires actual app profile source, packaged binary profiles and valid externally supplied signatures; it is an artifact gate, not an assertion of completed manual QA or Play approval. Dependency metadata was rechecked for all 34 catalog versions on November 13; no newer stable pins were found, retaining the Material 3 1.5-line exception.

References: [R8 optimization](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization), [16 KB guidance](https://developer.android.com/guide/practices/page-sizes), [app signing](https://developer.android.com/studio/publish/app-signing). ELF inspection follows the ELF32/ELF64 program-header layout; device performance claims require traces, not byte inspection.


## D055 — Isolate clock invalidations from Home layout

**Accepted after the Phase 6 performance audit.** The one-second ViewModel stream still changed the whole Home state, invalidating the screen and its overlays. Keep that live stream for the dial, and expose a distinct layout projection excluding countdown/end-time/progress/brief finished-message fields. Route/sheets observe the projection; the dial reads the live State at its own composition boundary. The reduction control observes a derived boolean, changing only when remaining time crosses its five-minute boundary. User commands, settings/readiness, phase and run changes remain structural state changes.

A Compose regression advances the live clock through the reduction boundary, checks the displayed countdown and disabled control, and verifies no parent screen recomposition. Existing native images must remain unchanged. This isolates dial updates and avoids unrelated per-second layout work; the brief's stricter text-only invalidation and real frame/CPU cost still need tracing on the physical benchmark target. Do not infer zero dropped frames or the startup budget from this host regression.

Frame benchmarks explicitly kill/relaunch in setup without StartupMode.COLD: that mode kills the target after setup and would discard the prepared visible running screen. Cold-start timing uses StartupMode.COLD with launch inside measureBlock. All measured journeys use production logic in the isolated profileable package.


## D056 — Gradle patch and physical phone launch helper

**Verified November 13.** Release lint identified Gradle 9.9.1, outside the catalog's Maven version references. The official current-release endpoint and distribution checksum agree on d8a18a7a841dd5df825d35a685fad6f2d7f12ac0c280e57b67c27b4a520ba228. Update the checksum-pinned project wrapper and exercise it with the existing JDK 17; this is a project build dependency, not an SDK/JDK/global tool installation. Do not suppress version lint. Java's initial distribution download and the wrapper task's URL probe timed out. Downloading from the official endpoints with curl produced the exact pinned hash; the approved wrapper cache was populated with that verified ZIP. The bootstrap JAR already matches the official 9.9.1 wrapper checksum (621008afc4f5d59357fcf06ffa644dd5aade5e091bdc363742710445f73cdaf8). Keep URL validation enabled and increase the network timeout to 60 seconds; verify the actual new wrapper runtime/build rather than treating the failed regeneration URL probe as a pass.

Add scripts/run-phone.sh because repeated installDebug attempts failed at device discovery. It checks a selected physical device's authorization before building, resolves the existing local SDK/JDK, builds debug, then uses adb -s for both update installation and MainActivity launch. It preserves app data, grants no permissions and starts no timer. No-device and shell-syntax paths are host-testable; successful install/launch remains pending until a phone is authorized.

Sources: [official current Gradle metadata](https://services.gradle.org/versions/current), [9.9.1 distribution checksum](https://services.gradle.org/distributions/gradle-9.9.1-bin.zip.sha256), [ELF program-header specification](https://refspecs.linuxfoundation.org/elf/gabi4%2B/ch5.pheader.html), [Macrobenchmark scope API](https://developer.android.com/reference/kotlin/androidx/benchmark/macro/MacrobenchmarkScope).


## D057 — Connected profile evidence and fresh frame setup

**Accepted November 13.** The phone now reports API 35, en-IN, font scale 1.0, Battery Saver off and an unlocked interactive screen. Its initial battery snapshot is charging at 37 °C. No normal-app TimerService is active; the optional notification listener is bound. Preserve the normal app/settings and the owner's untracked PHONE_COMMANDS.md.

A source audit found the frame setup did not contain the explicit process kill described in D055. Add the verified MacrobenchmarkScope.killProcess call before launch, leaving startupMode unset so the prepared running screen is not killed after setup. Collect the real app profile on the isolated benchmark package and record charging/thermal context with any timing result. A successful profile is not by itself a performance-budget pass or a background-playback reliability pass.

## D058 — Retain the reflectively constructed WorkManager database

**Reproduced on the optimized Android 15 target, November 13.** The first five macrobenchmarks failed before measurement because the target crashed in Startup's WorkManager initializer. The profile-install receiver existed but could not run while its process failed to initialize. The resolved Glance dependency brings WorkManager 2.7.1 / Room 2.2.5. Cached Room bytecode uses Class.newInstance for WorkDatabase_Impl; its consumer rule retains database classes but the R8 usage output lists WorkDatabase_Impl's public no-argument constructor as removed.

Add one explicit constructor keep rule for androidx.work.impl.WorkDatabase_Impl to both optimized production and benchmark builds. Keep full optimization enabled for the rest of the graph. Verify the rebuilt mapping/usage and rerun the real required-profile benchmarks; do not suppress the broadcast error or weaken BaselineProfileMode.Require. Preserve the first failed run as evidence, separately from any successful rerun. Profile sources remain the real successful non-minified collection output.

References: [R8 full mode](https://developer.android.com/topic/performance/app-optimization/full-mode), [narrow reflection rules](https://developer.android.com/topic/performance/app-optimization/keep-rules-overview), plus the resolved Room AAR bytecode/consumer rule and target crash/usage output.

## D059 — Requery stale timer-start handles in performance setup

**Reproduced during the optimized benchmark rerun.** The countdown setup found the 60-minute preset, but its UiObject2 became stale before click as Home settled. UI Automator 2.4.1 reports StaleObjectException when the underlying accessibility node is replaced; its click bytecode resolves that node before injecting input. Requery and retry only that specific exception, at most three attempts, for the preset/Start setup actions. Missing targets and other failures must still fail the test. Do not add retries around measured extension/cancellation or suppress journey failures. Existing completed startup/Settings/dial measurements remain separate evidence; rerun the affected countdown test with the corrected fixture and retain the failed result.

## D060 — Idempotent service execution readiness

**Reproduced November 14 on Android 15.** A controlled 10-second run paused the player, but each alarm service delivery sent both service-ready and service-signal full reconciliations. Those canceled the active verification, rescheduled the elapsed final-window alarm and restarted stopping. The observed result changed from the initial focus stop to a later media-key attempt. Converting only the onStartCommand announcement did not fix the physical retest; the ensureForeground intent decoder also needed correction. TimerEngine.reconcile intentionally rebuilds effects for recovery/access changes, so it must not represent duplicate readiness.

Introduce an internal ExecutionReady command. If execution is already allowed, it succeeds without canceling the effect or rescheduling alarms. If execution was unavailable, reuse reconciliation to activate protection. TimerService sends this typed command after promotion, and the ensureForeground intent decodes to it; real service loss/restart, permission and time-change reconciliation retain their existing behavior. Test duplicate readiness and final-window messages during suspended playback verification, including exact alarm-plan counts, one stop attempt and complete wake-lease release. A separate decoder regression on APIs 26/35 covers the actual alarm service entry path.

## D061 — Separate frame latency, dropped frames and steady wave cost

**Measured November 14 in a retained November 13 capture.** The second successful countdown iteration contains 1,079 app FrameTimeline records across the whole capture (including records outside FrameTimingMetric's measured window): 994 Buffer Stuffing-only late presents, four Dropped Frame records and one App Deadline Missed + Buffer Stuffing record, with other compositor/prediction/unknown classifications. AndroidOwner:draw averages 0.318 ms; RenderThread dequeueBuffer averages 5.127 ms and the main thread waits 6.009 ms in postAndWait. Slice durations are wall time and nested totals overlap, not exclusive CPU cost. Thus the large overrun count is not evidence that wave geometry consumes most of the frame budget. Perfetto describes buffer stuffing as queued frames adding presentation/input latency, potentially without visible stutter. Do not relabel it as a pass or discard the four drops/deadline miss.

Keep the original mixed wave/extend/reduce/cancel benchmark for historical comparisons. Add a separate steady-wave benchmark with the Home transition settled in setup and cancellation outside measurement. Cancel a previous steady run before killing/relaunching the isolated app for the next iteration. Preserve required-profile compilation and raw overrun metrics. Commit read-only SQL for repeatable thread-cost, measured-window classification and deadline-event offsets; retained counts and trace health issues remain visible. Two-method comparison and separate package-scoped steady validation pass. Across ten steady iterations, the measured windows contain 9,567 FrameTimeline records with no Dropped Frame or App Deadline Missed classification; the five mixed iterations contain five drops, all at 8.18–8.27 s when extension/reduction begins. Buffer-stuffing latency remains. No production UI or frame-rate hint was changed; compare dimming/presentation cadence and inspect those interactions before choosing an optimization. This is diagnosis, not a measured app improvement or completed frame-budget gate.

Scope fixture node/cleanup selectors to the isolated target package, using the verified UI Automator 2.4.1 BySelector.pkg API. In particular, pre-launch cleanup must not match another app's Stop control if the foreground surface changes. This protects the normal app without altering production state or weakening missing-target failures.

Copy reusable selectors before adding the package constraint: pkg mutates BySelector and rejects a repeated package assignment. The pinned library's By.copy contract was exercised on the existing JDK with three repeated scoped copies, leaving the source selector unmodified, so a stale-node retry can safely reuse it.

References: [FrameTimeline classifications](https://github.com/google/perfetto/blob/main/docs/data-sources/frametimeline.md), [Compose graphics guidance](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers), and pinned Material 3 1.5.0-rc01 sources (CircularWavyProgressModifiers.kt already uses drawing/geometry caches).

## D062 — Reuse countdown number formatting within the dial

**Source audit November 16.** Every countdown update creates a two-digit NumberFormat, plus another integer formatter for hour durations. Reuse these mutable formatters inside a single composable-owned CountdownFormatter, remembered by locale; initialize the hour formatter only when needed. This scope is confined to UI composition, with no global/shared formatter cache. Cache the displayed string by its rounded whole-second value so unrelated sub-second state emissions do not repeat formatting.

Keep rounding, hour limits, localized digits, grouping and padding unchanged. A locale change replaces the formatter; repeated hour/minute boundary tests guard mutable-format state. Leave end-time formatting's existing timezone/12–24-hour behavior intact. This removes known repeated allocation; physical before/after measurements are required before claiming a frame-time or dropped-frame improvement.

Reference: [Compose guidance on remembering expensive calculations](https://developer.android.com/develop/ui/compose/performance/bestpractices#use-remember). Keys include the locale-owned formatter and rounded seconds, the inputs that affect the displayed numeric text.

**November 17 outcome:** host tests/images pass; the physical interaction comparison does not establish a clear frame-time improvement (overrun P50/P99 essentially unchanged, worse maximum). Full measured-window classification after reconnect found 13 drops before vs 12 after, still around the controls. Keep the bounded formatter reuse for the known repeated construction it removes; do not close performance budgets. See [PHASE6_COUNTDOWN_FORMATTING.md](PHASE6_COUNTDOWN_FORMATTING.md). Analysis/cleanup and normal app installation are complete.

## D063 — Update the host test runtime when release lint discovers a stable release

November 16 verification rejected Robolectric 4.17 because 4.18 is now stable. Maven Central metadata and the published 4.18 POM confirm it; update the test-only catalog pin without suppressing the dependency check. The [4.18 migration guide](https://robolectric.org/upgrade_to_version_4/#migrating-to-418) drops API 23 support (the app minimum is 26) and changes some native rendering behavior. Rerun the existing tests and image comparisons against unchanged baselines; no production dependency or SDK/JDK installation is involved.

## D064 — Reuse stable notification PendingIntent tokens within a bounded run cache

The retained November 16 control trace has four synchronous 0x40 binder calls per notification rebuild, followed by a 0x07 post. The cached Android 15 framework maps these to getIntentSenderWithFeature and enqueueNotificationWithTag; the sequence agrees with the content/delete/extend/stop factories in BasicTimerNotifications. This identifies avoidable repeated lookups, not the cause of every dropped frame. Notification posting, composition, layout and buffer waits remain measurable costs.

Remember the app-opening token for the controller lifetime. Retain only one pair of running action tokens, keyed by run ID and Extend by; replace it on either change. Protect this bounded cache because the service and configuration observer can call the controller on different threads. Do not cache the revision-bearing dismissal token. Preserve explicit immutable intents, URI identities, flags and per-delivery command IDs. Keep foreground promotion and notification ordering synchronous; moving posting to an uncoordinated worker could race cancellation or service destruction. Test settings/run changes and measure the same interaction journey before/after; do not close a frame budget from token reuse alone.

Reference: [PendingIntent identity and reuse](https://developer.android.com/reference/android/app/PendingIntent), including matching operations and FLAG_UPDATE_CURRENT's treatment of extras. These factories are already used by the app; no dependency or platform API change is needed.

**Verification:** all 218 host tests, 15 unchanged image comparisons and release checks pass. The fresh baseline attempt failed with No connected devices; before/after physical measurement and the normal phone update remain pending. See [PHASE6_NOTIFICATION_INTENTS.md](PHASE6_NOTIFICATION_INTENTS.md). No frame improvement is claimed from the source change alone.
