# Sleep timer implementation plan

Planning date: 2026-11-03 (Asia/Kolkata).

## Scope and current gate

The owner authorized **Phase 6 Release preparation** on November 13. Host release artifacts/audits, baseline-profile and macrobenchmark journeys, clock composition isolation, the phone launcher and the Play/QA handoff are implemented. Real app profiles and targeted connected journeys have evidence; the November 20 full check fails dependency-update lint despite passing host tests/images/style and artifact checks. A release candidate remains gated on final-source measured budgets/profiles, production identity/signing and the pending reliability matrix. Physical surface synchronization and playback reliability evidence remain pending. Runtime checks that were unavailable remain explicitly pending rather than being reported passed. See [PROGRESS.md](PROGRESS.md) for actual verification and the current handoff, and [DECISIONS.md](DECISIONS.md) for choices and platform corrections.

The authority is [AGENTS.md](../AGENTS.md), with all four PNGs in [design](../design) visually inspected. The PDF duplicates those references. The product priority is reliable playback stopping, followed by safe volume handling, platform-native presentation, accessibility, and low battery use. A disconnected phone does not block host implementation; unavailable runtime checks remain pending.

## Confirmed macOS environment

| Component | Confirmed state |
|---|---|
| Host | macOS, Apple Silicon / arm64 |
| Android SDK | `/Users/sujalsunilchaudhary/Library/Android/sdk` |
| Installed platforms | 34 revision 3; 35 revision 2; 36 revision 2; **37.0 revision 2** |
| Installed Build Tools | 34.0.0, 35.0.0, 36.0.0 |
| Platform Tools | 37.0.1; `adb version` executed successfully |
| Command-line Tools | 23.0; existing Android CLI used for SDK installation |
| JDK | Homebrew OpenJDK 17.0.20.1, aarch64; `java` and `javac` executed successfully |
| JDK home | `/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home` |
| Android Studio / bundled JBR | Not found in standard application locations or Spotlight |
| Emulator / system images | Not installed; Phase 1 device tests pending; prior Foundation APK installed on Android 15 |
| Shell configuration | `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `JAVA_HOME` were unset; no global configuration changed |

The approved addition was `platforms/android-37.0`; SDK metadata reports Android 17, `REL`, preview SDK 0. The installed archive opens with JDK `jar`, and Build Tools 36.0.0 `aapt2 version` runs. These are environment checks, not an application build.

The SDK path is saved in ignored `local.properties`. For future terminal builds, set the existing JDK for that shell:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
```

Use the committed Gradle wrapper. No system Gradle, Android Studio, emulator, or replacement JDK is required to begin host builds. Further SDK/tool installations require owner approval.

## Architecture and module boundaries

Use a single application process and a single Hilt-scoped `TimerEngine`. Android components and UI send commands through a service-aware gateway. No surface writes timer state or independently executes the stop pipeline.

| Module | Responsibility and boundaries |
|---|---|
| `:app` | Application, Activity, navigation, manifest assembly, DI bindings; composition root for implementations |
| `build-logic` | Included build with Android/Kotlin/Compose/testing/quality conventions; pinned plugin versions |
| `:core:model` | Pure Kotlin states, commands, results, settings, formatting, clock and platform-neutral port contracts |
| `:core:timer` | Serialized reducer, initialization/reconciliation, effect ownership, service, validated command gateway |
| `:core:data` | Settings and snapshot DataStores, versioned serializers, corruption handlers, migrations; implements model contracts |
| `:core:playback` | Observation, optional session listener, stop attempts, fade and guarded restoration; implements playback contracts |
| `:core:system` | Alarms, bounded wake locks, sensors, vibration, screen locking, permission observations and local diagnostics |
| `:core:designsystem` | Expressive wrappers, theme and generated fallback palettes, motion, typography, dial and segmented settings rows |
| `:feature:home` | Idle/running/finished presentation, Custom and Nightly sheets; command emission only |
| `:feature:settings` | Settings, readiness, test results and contextual system-access flows |
| `:surface:notification` | Immediate foreground notification provider, ongoing content, actions, dismissal policy and promotion fallback |
| `:surface:tile` | Tile lifecycle, listening updates and validated user commands |
| `:surface:widget` | Glance widgets, RemoteViews countdown interop, previews and pinning |
| `:tools:testplayer` | Separate debug/test application with cooperative, focus-ignoring and auto-resuming playback modes |
| `:baselineprofile`, `:benchmark` | Profile generation and release-like startup/frame measurements |

Dependency direction is toward `:core:model`. Timer orchestration consumes ports; platform/data implementations do not depend on feature or surface modules. The app supplies Hilt bindings. The notification module can implement a foreground-notification contract declared in `:core:timer`, avoiding a timer-to-notification-module cycle. Feature modules use engine/repository contracts and design-system components. Glance uses shared color policy through Glance-compatible adapters rather than reusing Compose-only controls.

### State, commands and durable execution

1. Restore and reconcile the snapshot before exposing initialized state. The Activity uses a correctly themed startup gate; it must not flash an invented idle/ready state. The service posts a real startup foreground notification immediately, before asynchronous initialization.
2. Model `Idle`, `Running`, `Fading`, `Stopping`, `Deferred`, and `Finished`, including run ID, elapsed deadline, duration, source, test flag, service-start provenance and fade bookkeeping. Keep platform observations separate from persisted facts.
3. Serialize state transitions and snapshot commits. Run fade, verification, deferral and watchdog as cancellable effects outside the command consumer. Effects return internal commands tagged with run ID, state revision and effect generation. Revalidate immediately before side effects; stale work cannot alter a replacement run.
4. Deduplicate a delivery using its command ID. Separate Extend taps remain additive. Persist the accepted command identity with the new state; nightly commands also have an occurrence identity. Alarms include run and schedule generation in their PendingIntent identity.
5. Persist desired state, then reconcile OS alarms and controllers. DataStore and AlarmManager cannot share an atomic transaction, so recovery must cover death on both sides of every boundary. Errors produce an explicit readiness/result state rather than a false success.
6. All deadlines use injectable elapsed realtime. Wall time only supplies display end times and nightly occurrences. UI ticks recompute remaining time; they are never the deadline scheduler. Persist boot count to reject elapsed timestamps from another boot.
7. Every entry point validates actions, duration bounds and command identity. A foreground service is started promptly from eligible user interaction, with immediate `startForeground`; failures cannot leave the UI claiming normal protection.

### Timing and service lifecycle

- Run a `specialUse` FGS for the timer lifetime. Record start provenance, not a fictitious public WIU permission query. Recreated services must not trust capability retained in a snapshot.
- Schedule the final-window elapsed-realtime wakeup at `end − max(enabled warning, effective fade, 0) − 5 s`. If already in that window, acquire its scoped wake lock immediately. Hold it through the elapsed-time warning/fade/stop scheduler and necessary cleanup, always with a timeout and structured release.
- Retain the end+30 s backup as best-effort recovery. Allow-while-idle quota means it is not an independent deadline guarantee. Near-deadline starts/restarts/reschedules need a conservative wake-lock path when alarm delivery is at quota risk; test repeated short runs under forced idle before closing Phase 1.
- If exact access is absent at start, use the explicit whole-run wake-lock fallback, capped at remaining duration + 2 minutes, and report readiness. If access is later revoked, Android can stop the app; recover and explain on the next viable launch instead of promising a live fallback that cannot run.
- Use a wake-up accelerometer when available. For a non-wake-up sensor, support screen-on detection plus a bounded last-five-minute screen-off wake window. Enabling shake therefore changes the normal screen-off battery budget; measure it separately from shake-off and exact-access-denied modes.
- Model deadlines for deferral (2 h), watchdog (30 s) and delayed restore (at most 2 min) separately. Transfer/release scoped resources when phases change. Do not retain the original short final-window timeout as the only protection for a long deferral.
- On cancellation/finish, invalidate effects and alarms, unregister sensors/listeners, abandon held focus and release every lock. Stop the service only after necessary cleanup. Full device force-stop and OS termination cannot be defeated.

### Playback and volume safety

The owner confirmed: **user Stop cancels the timer and leaves playback running**. Expiry runs the playback-stop pipeline. Cancellation during a fade uses guarded, gentle cancellation recovery; it does not send pause or request new focus.

At expiry, combine local playback observations with accessible session state, including remote sessions. Evaluate calls/alarms before fading and again before every relevant volume/focus/key action. Re-evaluate on callbacks throughout the pipeline. Unknown observation is not proof that an action is safe.

Try active-session pause when access is granted, then eligible audio focus, then media **PAUSE** down/up, verifying between steps and retrying pause once if necessary. Stop escalation when observations confirm inactivity. Skip focus if interruption safety or background capability cannot be established. Never send PLAY_PAUSE. Report unsupported/unknown remote verification honestly.

Keep separate timestamps for the requested deadline, first stop attempt and confirmed cessation. The verification/escalation budget means the one-second target is tested with cooperative players; it is not a blanket guarantee for a player that ignores earlier steps.

Fade only with a verified adjustable stream, safe interruption observations and a sufficiently established output route. Public APIs cannot universally identify a third-party player's device; route ambiguity disables volume writes rather than guessing. Route changes, unexpected readback, user changes or hardening rejection cancel fade/automatic restoration. Guard API 28+ volume-minimum calls; the API 26–27 compatibility behavior is recorded in DECISIONS.

Automatic restore requires confirmed silence for at least 1 s, the additional approximately 1.5 s settling period, the original route and unchanged user control. Recheck just before every write. Drop an unresolved restore after the service's 2-minute window. Never retain a 12-hour surprise volume increase. An explicit extension/cancellation ramp is distinct from automatic restore and retains the route, user-change and volume-cap guards.

If nothing observable is playing, finish quietly. A remote session must not be classified stopped merely because local `isMusicActive` is false. The test uses the real pipeline, a 10 s duration, fade capped at 5 s and no screen lock. An initially silent test cannot prove that a player responded to pause.

## Design implementation contract

| Reference | Preserve |
|---|---|
| [Idle](../design/01-home-idle.png) | Title/tune action; outlined readiness and test chips; 12-o'clock clockwise dial with gap/thumb; centered tabular countdown/end time; connected presets; Custom / large Start / Nightly |
| [Running](../design/02-home-running.png) | Title without settings action; remaining-time wavy ring; conditional shake card; settings-derived pipeline; −5 / Stop / +Extend controls; stable visual transition |
| [Settings](../design/03-settings.png) | Collapsing heading; Ending, Extending, Starting, Appearance, Reliability order; segmented row shapes/gaps; expressive slider and connected theme controls; exact copy with US “Battery optimization” |
| [System surfaces](../design/04-android-surfaces.png) | System-rendered notification hierarchy, moon tile, dial/preset widgets, adaptive/themed moon icon and three shortcuts |

- The 1170 px-wide reference images represent 390 dp. Treat their spacing as a target, not fixed screen coordinates. Keep semantic M3 color roles; the violet is a wallpaper example. Generate fallback tonal palettes once in the theme layer, never scatter literal colors through UI.
- Wrap chips, adapt controls, and preserve 48 dp targets at 320 dp and 200% font scale. Use two panes at compact height and adapt to fold posture; do not lock orientation. Center bounded content on larger screens.
- Use official M3 components where supplied. The interactive circular duration dial needs custom pointer/slider semantics because a circular progress indicator is not an interactive slider. Isolate that component in the design system.
- Keep large timer digits tabular and fitting through 12:00:00, respecting locale. Avoid per-second announcements. Add keyboard, D-pad and TalkBack adjustments. Respect reduced motion and system haptic preferences.
- The screenshots show configured settings, including screen-off enabled; defaults come from §4.6, including screen-off disabled. Only show “Playback stopped” after confirmed stopping. Choose an honest result for failure/interruption.
- Persist theme/dimming policy so splash, first frame, dialogs and transitions do not flash. Use application night mode where available. Restore brightness on leaving the running screen; never keep the screen awake.
- Android 15 gets the regular ongoing notification. Verify promotion eligibility on supported newer releases; the app must not promise a status chip when the system declines it. Widgets use a countdown chronometer and minute-limited ring refreshes, not per-second app updates.

## Phase delivery and exit checks

Complete one phase per implementation task unless the owner changes scope. Recheck official versions/APIs before first use and pin actual resolved versions in the catalog. Each task updates PROGRESS with commands, observed outcomes, skips and exact manual steps.

| Phase | Deliverable | Exit evidence |
|---|---|---|
| 0 — Foundation | After plan review: wrapper/catalog, convention plugins, listed modules, quality tasks, generated fallback/dynamic theme, expressive wrappers, symbols, splash, edge-to-edge, two-destination navigation | Host build/unit/lint/format/detekt; real themed Foundation navigation; API 26 and 37 runtime checks when available, otherwise explicitly pending |
| 1 — Reliability core | Engine/ports, snapshot recovery, service/alarm/wake scheduling, basic notification, playback pipeline, fade/restore and testplayer | Deterministic transition/safety tests; receiver/service tests; 2-minute screen-off/forced-Doze stop timing with testplayer; real-player/phone evidence when connected; quota and capability gates resolved |
| 2 — Home UI | Idle/running/finished, interactive dial, presets, Custom/Nightly sheets, transitions and semantics | Design comparison, narrow/large-font/RTL screenshots, interaction and accessibility tests; working behavior wired to real engine |
| 3 — Settings and features | All settings/readiness/test flows, nightly execution, shake/warning/admin/dimming and reliability flows | Rows work end-to-end; volume/interruption/sensor/permission checks; settings persistence and schedule DST tests |
| 4 — System surfaces | Promoted/fallback notifications, tile, two widgets, shortcuts and finalized adaptive/themed icon | Synchronized state after changes/recovery; notification fallback and dismissal; widget update budgets; start-path/capability verification |
| 5 — Hardening | Complete §6 mapping, process/reboot/late-stop recovery, adaptive/i18n, OEM guidance, local diagnostic export | MANUAL_TEST_PLAN covers every edge case; remaining device/OEM gaps named; no unverified claims of overnight reliability |
| 6 — Release | R8/shrinking, signing guidance, AAB, baseline profile, benchmarks and PLAY_SUBMISSION | Release smoke test, measured startup/frame/size/battery budgets, 16 KB compliance, privacy/manifest review and staged-rollout checklist |

Nightly presentation may land in Phase 2, but the full scheduling/settings behavior exits in Phase 3. Recovery primitives are essential in Phase 1 and receive the broader hardening matrix in Phase 5; they are not deferred until then.

## Verification strategy

Run after each meaningful implementation change:

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug spotlessCheck detekt
```

Foundation defines those aggregate tasks for all relevant modules, including quality checks for build logic. Add release, screenshot, instrumentation and benchmark tasks when their phase requires them. Repeat checks only after meaningful changes or failures. Never list a command as passed merely because it appears here.

| Layer | Required coverage |
|---|---|
| Pure JVM | All state/command transitions; duplicate deliveries versus distinct taps; effect cancellation; stale run/revision; boot/late policies; duration bounds/rounding; warning once; restore safety; DST in Kolkata, London, New York and Lord Howe; shake traces |
| Robolectric | DataStore corruption/migration, backup exclusions, explicit/immutable alarm intents, grant/reconcile receivers, notification contents, service deadlines/failures, tile state and permission observations |
| Compose / screenshots | Idle/running/results/settings/sheets; light/dark, dynamic-off baseline, 1.0/1.3/2.0 font scale, 320/360/390/600/840 dp, RTL and pseudo-locales; dial semantics, input and −5 cutoff |
| Instrumentation | API 26/29/31/33/34/35/36/37; forced Doze, quota-risk short timers, process death, callbacks, screen-off stop, service start paths, Android 17 audio hardening, notification/widget/tile sync |
| Real-device manual | Android 15 with Spotify, YouTube Music/YouTube, podcasts, Audible, VLC and Chrome; long/overnight runs, Bluetooth changes, alarms/calls, DND/Bedtime and optimized/unrestricted battery; OEM coverage tracked separately |
| Release/performance | Startup and frame metrics, baseline profile, release R8 serialization/DI/widget smoke tests, download size, no leaked locks/sensors, StrictMode and LeakCanary |

Physical shake traces, overnight timing, OEM survival, hearing-safety/Bluetooth behavior and phone performance cannot be established by JVM tests. An emulator is useful, but is not evidence for those physical-device claims. Keep testplayer out of production artifacts.

## Highest risks and implementation gates

1. **Timing under OS constraints:** alarm quotas, process death, revoked access and blocked FGS starts. Measure deadline-to-attempt and deadline-to-confirmation, test near-deadline reschedules, expose degraded readiness, and document unavoidable force-stop/revocation limits.
2. **Audio safety and observability:** ambiguous routes, anonymized configurations, remote sessions, concurrent alarms/calls and silent hardening failures. Prefer skipping volume/focus over an unsafe guess; report uncertainty.
3. **WIU and entry points:** public start provenance is not a capability grant. Verify Activity, widget, notification, tile, nightly and sticky-restart paths under Android 17 hardening, including thrown and silent failures.
4. **Dependency compatibility:** newest stable AGP/Kotlin are not fully covered together by the published Kotlin tested range. Resolve KSP/Hilt/compiler plugins and build/lint checks before implementing features; do not silently downgrade.
5. **Platform-owned surfaces and DND:** notification promotion and warning vibration depend on system eligibility and user policy. Verify supported behavior and retain fallbacks; do not seek bypass privileges.
6. **Battery and delivery scope:** 12 h whole-run fallback, non-wake sensor windows, 2 h deferral and watchdog have distinct costs. Measure each mode and keep every lock bounded; do not claim a general “CPU sleeps throughout” budget.
7. **Visual and release budgets:** Expressive API changes, accessible layouts and the <5 MB target require actual measurements. Resource/R8 optimization must not trade away correctness.

## Questions and review boundary

The Stop-button product choice is resolved: cancel the timer and keep playback running. No further product answer is required for Reliability core. Publisher identity, production application ID and signing details must be settled before release configuration; do not invent production credentials or IDs.

Phase 2 Home implementation and host checks are complete. The owner authorized Phase 3 on November 4. PROGRESS, PHASE1_TESTING and PHASE2_TESTING retain the pending runtime evidence and exact phone commands.


## Phase 2 execution

Add idle/running/finished Home, keyboard/TalkBack dial, connected presets, Custom/Nightly sheets, real engine commands and adaptive Expressive motion. Nightly requires a functional scheduler to avoid an inert switch; include that supporting dependency with its sheet. Complete Settings, shake and screen locking remain Phase 3. Render the shake card only while monitoring is actually active. Preserve the pending phone/Doze gates.

## Phase 3 execution (authorized November 4)

Build the complete segmented Settings list and real persistence, permission explanations, warning/extend pickers, existing Nightly/test entry points, appearance and window brightness. Add a force-lock-only device administrator behind explicit system consent, and invoke it after the real stop/restore pipeline with run-generation, call and test guards. Own accelerometer registration and bounded fallback wake leases in the foreground service; keep sensor samples local and test alternating-peak rejection, cooldown and lifecycle policy. Synthetic traces establish algorithm behavior; physical trace tuning remains an explicit device gate.

The tile/widget Add rows require real components. Include a functioning tile and pin-capable dial widget as supporting dependencies now; full surfaces, presets widget, promotion, previews and shortcuts remain Phase 4. Keep no inert Add actions. Check Android APIs against official documentation and pinned library sources. Verify settings interactions, persistence, policy and engine safeguards, then run all required host checks and existing screenshot comparisons. Do not claim pending Spotify/Doze, physical sensor, admin/PIN, DND or Android 17 gates passed.

## Phase 4 execution (authorized November 5)

Complete Android surfaces from design/04-android-surfaces.png: promote eligible notifications on 36.1+ with the existing silent countdown fallback; finish the reactive tile and both Glance widgets, including native host countdown, minute-only ring renders, responsive layout, generated/static previews and theme/accessibility. Add adaptive monochrome icons and validated static shortcuts. All mutations remain serialized engine commands; shortcut replacement Undo must restore a still-valid previous deadline without allowing stale actions to affect another run.

Refresh surfaces for real run/settings changes, wall-clock/zone/locale/theme changes and process restoration. Keep dismissal suppression durable enough for controller recreation, isolate widget/notification failures and avoid polling widgets each second. Use official documentation and pinned source APIs. Test adapters, action identities, widget layouts/previews and stale replacement behavior; run required checks and existing image comparisons. Do not install SDK/JDK/emulator components or wait for a phone. Record unavailable API 36.1/37 promotion and WIU/device synchronization checks as pending.


## Phase 5 execution (authorized November 12)

Read the full brief and all four design PNGs, then audit the existing recovery/resource ownership tests rather than replacing the engine. Add the specified local diagnostics share action in Settings Reliability, expose only the bounded internal event buffer and relative elapsed times, and keep sharing user initiated. Keep export generation in memory; no network, storage permission, app metadata or device identifier is needed.

Complete dial Page Up/Down and ±5-minute accessibility actions with localized labels. Exercise narrow/large-font, RTL/pseudo-locales, landscape and tabletop presentation, fixing any reproduced layout or hinge issue. Audit process/reboot/late-stop and call/alarm resource safety; add regressions for actual gaps. Check OEM guidance coverage and pin currently verified dependency versions.

Create docs/MANUAL_TEST_PLAN.md with stable case IDs covering every §6 bullet, explicit expected results, host test references and pending physical-device status. Include real-player/Doze, volume/route, call/alarm, DND, sensor, permission, surface, recovery and API/OEM procedures with cleanup for reversible adb changes. No SDK/JDK/emulator install is authorized. Run required quality and image checks; use a connected phone only when available and preserve active user timers. Record unmeasured device gates honestly and stop before release/performance work.


## Phase 6 execution (authorized November 13)

Retain the existing toolchain and recheck pinned dependency metadata. First build the optimized unsigned release APK/AAB and inspect R8 output, merged permissions/components, shipped native binaries and package size. Use generated library consumer rules and manifest retention; add only keep rules justified by an actual reflection boundary or shrinker failure. Configure optional secret-only signing inputs with fail-fast validation; do not create a keystore or publish an artifact.

Implement connected-device baseline-profile generation and cold-start/frame-timing macrobenchmarks against an optimized, profileable benchmark variant. Cover Home startup, Settings/scrolling, dial drag, visible countdown/wave, extension and cancellation; close the timer in cleanup. Keep all benchmark/profile libraries out of production runtime dependencies. No fake baseline profile or invented timing result is acceptable. An absent device does not block host compilation, release assembly or artifact auditing.

Add a repeatable host artifact check for forbidden permissions/debug tooling and 16 KB native ELF/ZIP alignment, distinguishing binary inspection from runtime compatibility. Document measured artifact sizes separately from Play split/download estimates. Complete PLAY_SUBMISSION.md with special-use FGS/exact-alarm rationale, video script, consent/disclosure/privacy copy, signing/versioning/rollout/screenshot plans and explicit pending owner/QA inputs. Add release test commands, preserve every unresolved manual case, run debug quality/image checks plus release lint/assembly, and report the exact install-and-launch commands for the owner's phone. Phase 6 is not complete until its device/performance/profile gates have actual evidence.


## Phase 6 connected-device continuation (November 13)

The Moto g54 / Android 15 is now authorized. Confirm no normal-app timer is active, preserve the owner's untracked PHONE_COMMANDS.md, and use only the isolated benchmark package for profile/measurement journeys. Record locale/font/power/temperature/thermal state before collection. Collect real startup/navigation/running profiles, rebuild optimized artifacts, run startup/frame benchmarks and inspect actual reports/traces. Charging/thermal conditions qualify the evidence; do not silently change system power settings to manufacture a result.

First correct the frame setup's missing explicit killProcess call: the previous handoff described a fresh launch for each iteration, but the source only pressed Home. Then format/build/check, collect profiles on the connected phone and fix any reproduced journey/runtime failures. Keep profile-generation claims separate from measured performance. At completion leave benchmark timers/services stopped, install/open the normal debug build only if it has no active timer, update progress with observed results and give the exact phone command. Signing/identity, Android 17 and untested reliability cases remain open.

## Phase 6 smoke-test correction (November 14)

The resumed 10-second playback test exposed repeated stop attempts when an alarm delivery announced an already-ready service. Separate the idempotent execution-ready signal from a full reconciliation, preserving the active effect and alarm plan when execution is already allowed. Keep real service loss/restart, permission and time-change reconciliation behavior. Add a regression with playback verification suspended while duplicate readiness/alarm messages arrive; then rerun host verification and the controlled physical test. Preserve the November 13 performance reports as measurements of that earlier source, not evidence of later changes.

## Phase 6 frame-cost investigation (November 14)

Inspect the retained Perfetto frames, main/render thread slices and the pinned Material 3 implementation before changing presentation. Reuse the trace processor already shipped in the Benchmark dependency; no SDK/JDK/global tool installation is needed. Preserve the normal app and any user timer. Separate steady wave rendering from start/extend/cancel interactions and compare the same isolated benchmark journeys under recorded device conditions. Keep the supplied Material 3 component and design/motion behavior; record the measured cause and target only that work. Run host quality/image checks after changes, then actual frame measurements, retain failures/raw samples, clean up the isolated target and give the phone launch command. Unmeasured release gates remain open.

## Phase 6 countdown allocation reduction (November 16)

Capture a current-source mixed interaction baseline on the isolated benchmark app. Replace per-update NumberFormat factories with a dial-owned, locale-keyed formatter; cache the visible string by rounded countdown seconds. Keep hour formatting lazy, preserve exact digit/padding/rounding behavior, and retain live end-time/time-format handling. Verify repeated hour-boundary formatting, locale changes in composition and all established images. Run complete host/release checks and the same physical interaction benchmark after the change. Report allocation removal separately from measured frame outcomes; do not attribute the earlier drops to formatting without evidence. Preserve the normal app's timer/settings and update it only when idle.

## Phase 6 notification intent reuse (November 17)

The retained control trace shows repeated synchronous system calls while rebuilding the running notification. Capture a fresh mixed-journey baseline, then reuse the app-opening PendingIntent and a bounded pair of run/extension-specific action tokens. Continue rebuilding the revision-bearing dismissal token and notification content. Preserve immediate foreground promotion, command delivery IDs, run-bound stale-action rejection and synchronous notification ordering; introduce no asynchronous posting race. Verify changed Extend by values and replacement-run identities against retained old action tokens. Run full host/release/image checks, repeat the unchanged five-iteration benchmark and classify measured frames plus main-thread binder work around controls. Keep remaining notification-post/render costs and unresolved budgets explicit; clean up only owned benchmark/analysis resources and preserve the normal app's timer/settings.

The connected follow-up recovered installation using the existing SDK's non-streaming mode. Apply that one flag to scripts/run-phone.sh so the owner's repeatable command matches the successful normal-app deployment. Preserve its pre-build device checks, data-replacing install and no-permission/no-timer behavior. Check shell syntax, full host verification and the actual helper install/open path; no app source or benchmark journey changes are needed.

## Phase 6 transient cancellation notifications (November 19)

Cancellation durably enters STOPPING with a CANCELLED result before cleanup returns to Idle. When there is no fade record, this transition has no volume recovery to present; avoid replacing the ongoing notification just before the service removes it. Preserve updates for real expiry and cancellation with fade recovery, and label cancellation truthfully as Canceling timer in the notification/Home status. Move the existing cancellation predicate into the pure model as a shared extension, preserving its exact logic and serialized schema. Do not introduce asynchronous notification posting or change engine/foreground lifetimes. Verify ordinary cancellation, fade recovery, natural expiry and UI copy; run full host/image/release checks and the unchanged physical before/after benchmark. Keep final performance/reliability gates open and preserve the owner's data/timer. Leave the new edits uncommitted for review.

## Phase 6 benchmark recovery and frame sample audit (November 19)

Make the instrumentation fixture cancel a persisted isolated timer after bringing its activity to the foreground, both before journeys and during final cleanup. Validate the exact benchmark package before any fixture mutation; never clear normal app data or touch an owner timer. Add physical regressions for a timer left by force-stop, cleanup after leaving the app, repeated cleanup and rejection of the normal package. Keep the measured frame interactions and five-iteration count unchanged; run complete host/release checks and the real recovery tests, then a fresh interaction benchmark with inspection instrumentation closed. Inspect the retained oversized first frame sample against the pinned Benchmark implementation and measured trace window; retain raw results and record the actual cause or remaining uncertainty. Preserve existing profiles and performance history; no frame-budget pass from fixture work alone. Clean up owned phone resources, update progress and leave changes uncommitted.

## Phase 6 control-phase cost audit (November 19)

Inspect the retained validation-6874826c2c main/render-thread slices and synchronous IPC around the recorded control drops, checking platform transaction identities against the installed API source/library. Separate required foreground-service cleanup from avoidable presentation work. Choose a bounded change only after the trace/source evidence supports it, record the choice before implementation, preserve design/motion and timer reliability, and verify the full host/image/release suite plus unchanged physical benchmark where the phone is available. Keep raw/capture-window distinctions and uncommitted work intact; clean up only owned test resources and return the normal app to idle.

The retained cancellation frame reopens the readiness check-circle XML during recomposition. Retain lazily loaded Material Symbol ImageVectors for the SleepTimerTheme composition/configuration, bounded by the existing symbol enum, using the public Compose vector loader and normal Material Icon painter/tint behavior. Reset the cache when resources/theme/configuration change; preserve painterResource fallback outside the theme. Reuse existing screenshot references for a dark/running/light idle transition regression. Preserve current APKs as the baseline, run the full checks, then a fresh unchanged five-iteration before/after pair and inspect XML parses as well as frame outcomes. Do not infer that eliminating one parse fixes all layout/render waits.

## Phase 6 Home transition state investigation (November 20)

Check whether the outgoing AnimatedContent branch reads the incoming mode's layout and live dial state. First reproduce it with a manually advanced transition test, including a clock tick before cancellation. If confirmed, retain each branch's own layout and last rendered clock for its exit, preserving shared bounds, expressive motion and hidden outgoing accessibility semantics. Derive the clock-free layout from the same collected UI State with structural equality so clocks and layout cannot come from separately scheduled flows. Verify active clock/reduce-boundary updates without whole-screen recomposition, interrupted transitions and same-mode run replacement. Preserve baseline APKs, run full host/image/release verification without changing references, and compare the unchanged physical five-iteration journey. Keep all raw results, record correctness separately from performance, clean up owned phone resources and leave edits uncommitted.
