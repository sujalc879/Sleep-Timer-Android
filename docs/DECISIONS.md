# Engineering decisions

Recorded 2026-11-03 (Asia/Kolkata). “Accepted” describes the planned design, not implemented or tested application behavior. Recheck library sources and current platform documentation before first use. See [PLAN.md](PLAN.md) and [PROGRESS.md](PROGRESS.md).

## D001 — Respect the documentation-only checkpoint

**Accepted.** The owner's latest scope narrows Phase 0 to environment confirmation, ignored SDK configuration and planning. Do not generate a wrapper, catalog, module, screen or setup script in this task. Maintain PROGRESS as required by the repository brief. Full Phase 0 remains pending; no build/test pass is claimed.

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

**Accepted.** Documentation and environment checks cannot establish app correctness, overnight survival, real shake thresholds, Bluetooth safety, DND behavior, OEM behavior or release performance. Full Phase 0 must add the wrapper/catalog/modules/theme/navigation and pass its available checks. Runtime checks remain pending when a device/emulator is unavailable; do not relabel them passed.

No further product answer is needed for Foundation. Production application ID, publisher and signing identity remain release inputs. Remaining dependency versions (Hilt/KSP/DataStore/adaptive/Glance/quality/testing libraries) are intentionally not guessed here: verify latest stable coordinates and compatibility when writing the catalog. The plan is concrete, but the implementation and all behavioral evidence are still future work.
