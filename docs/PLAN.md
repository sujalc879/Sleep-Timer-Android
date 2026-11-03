# Sleep timer implementation plan

Planning date: 2026-11-03 (Asia/Kolkata).

## Scope and current gate

The owner reviewed the initial planning checkpoint and authorized the remaining **Phase 0 Foundation implementation**. This scope stops before timer/playback features. Runtime checks that were unavailable remain explicitly pending rather than being reported passed. See [PROGRESS.md](PROGRESS.md) for actual verification and the current handoff, and [DECISIONS.md](DECISIONS.md) for choices and platform corrections.

The authority is [AGENTS.md](../AGENTS.md), with all four PNGs in [design](../design) visually inspected. The PDF duplicates those references. The product priority is reliable playback stopping, followed by safe volume handling, platform-native presentation, accessibility, and low battery use. A disconnected phone does not block implementation; unavailable runtime tests are reported as skipped.

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
| Emulator / system images | Not installed; no device testing performed |
| Shell configuration | `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `JAVA_HOME` were unset; no global configuration changed |

The approved addition was `platforms/android-37.0`; SDK metadata reports Android 17, `REL`, preview SDK 0. The installed archive opens with JDK `jar`, and Build Tools 36.0.0 `aapt2 version` runs. These are environment checks, not an application build.

The SDK path is saved in ignored `local.properties`. For future terminal builds, set the existing JDK for that shell:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
```

Use the committed Gradle wrapper once Foundation creates it. No system Gradle, Android Studio, emulator, or replacement JDK is required to begin host builds. Further SDK/tool installations require owner approval.

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

Once Gradle exists, run after each meaningful implementation change:

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug spotlessCheck detekt
```

Foundation will define and verify those aggregate tasks for all relevant modules, including quality checks for build logic. Add release, screenshot, instrumentation and benchmark tasks when their phase requires them. Repeat checks only after meaningful changes or failures. Never list a command as passed merely because it appears here.

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

The Stop-button product choice is resolved: cancel the timer and keep playback running. No further product answer is required for Foundation planning. Publisher identity, production application ID and signing details must be settled before release configuration; do not invent production credentials or IDs.

The owner authorized Foundation after reviewing this plan. Complete its available checks and report deferred runtime gates, then stop before Phase 1. PROGRESS identifies the next authorized phase and remaining evidence.
