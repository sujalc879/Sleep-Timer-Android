# Sleep Timer for Android — Build Brief for Codex

> Save this file as `AGENTS.md` in the repo root (Codex reads it on every task) and put the design images in `/design`. Then start each Codex task with: *"Read AGENTS.md and /design. Continue with the next phase in docs/PROGRESS.md."*

---

## 0. Your role and the quality bar

You are a staff-level Android engineer who has shipped Google first-party apps (Clock, Settings, SystemUI surfaces). Build a **production-grade sleep timer** that will be used by millions of people on thousands of device models.

The bar:

1. **It stops playback on time, every time** — screen off, Doze, battery saver, after 3 hours, on aggressive OEM skins. This is the product. Everything else is secondary.
2. **It looks and behaves like a first-party Android app**, not a branded third-party app: Material 3 Expressive, dynamic color, system font, system patterns, system surfaces. No brand colors, gradients, custom fonts, mascots or decorative illustrations.
3. **It never blasts audio.** A volume restore must never make sound suddenly loud.
4. **It never crashes or ANRs**, never leaks a wake lock, never drains battery, and collects **zero** user data (no `INTERNET` permission).
5. **It is adaptive and accessible**: 320 dp phones to tablets and foldables, portrait and landscape, 200 % font scale, TalkBack, RTL.

The owner's personal device is an **Android 15** phone. The app must also be correct on Android 8.0 – Android 17.

---

## 1. How you must work

1. **Plan first.** Before writing feature code, create `docs/PLAN.md` (architecture, modules, risks, test strategy) and `docs/DECISIONS.md` (every non-obvious choice, with the reason). Keep `docs/PROGRESS.md` updated at the end of every task: what's done, what's next, known issues.
2. **Work in the phases in §13.** Finish one phase per task unless told otherwise. At the end of each phase, stop and report: summary, exact steps for me to test on my phone, known issues.
3. **Verify, don't guess.** Android APIs in this brief were correct when written, but the platform moves fast. Before using an API, check the official docs or the library source in the Gradle cache. If an API named here doesn't exist or has changed, use the current equivalent and record it in `DECISIONS.md`. Never invent APIs or library coordinates.
4. **Use the latest stable versions** of every dependency at the time you start, pinned in `gradle/libs.versions.toml`. The single exception is Compose Material 3 (see §3).
5. **Build and test continuously.** After every meaningful change run `./gradlew assembleDebug testDebugUnitTest lintDebug` (plus ktlint/detekt). Never claim something passes unless you ran it and saw it pass. If your environment cannot run the Android SDK, say so explicitly and set it up (cmdline-tools, platform, build-tools) via a setup script.
6. **No stubs.** No `TODO()`, no placeholder screens, no fake data paths, no "left as an exercise". If something is genuinely blocked, write it in `PROGRESS.md` and tell me.
7. **Ask me only for product decisions** this brief doesn't settle. For engineering decisions, pick the option that best matches this brief and log it.
8. **Small, reviewable commits** with conventional-commit messages, one logical change each.

---

## 2. Design reference

Files in `/design` (the PDF has the same content; prefer the PNGs):

| File | Content |
|---|---|
| `01-home-idle.png` | Home screen, timer not running |
| `02-home-running.png` | Home screen, timer running |
| `03-settings.png` | Settings screen (full scroll) |
| `04-android-surfaces.png` | Live Update, Quick Settings tiles, widgets, app icon, shortcuts |

The mockups are drawn on a **390 dp-wide** phone frame. Sizes below marked ≈ are measured from it; treat them as targets at that width and scale responsively.

### 2.1 How to interpret the design

- The design is the authority for **layout, hierarchy, component choice, iconography, copy and states**.
- The purple colors are an **example of dynamic color** (wallpaper-derived), not brand colors. Map every color to an M3 color role (table in §2.6). Never hard-code a hex value in UI code.
- Where the design is silent, follow **Material 3 Expressive guidance and the conventions of Pixel system apps** (Clock, Settings), and log the decision.
- Don't add UI beyond the design except where this brief marks an addition with ➕.
- Copy: reproduce the strings exactly, with one fix — the design mixes "optimisation" and "color". Use US English (`Battery optimization`) for the default locale.

### 2.2 Home — idle (`01-home-idle.png`)

Top to bottom:

- **Top bar**: title "Sleep timer" (left, headlineSmall-ish ≈24 sp), settings icon (Material Symbols `tune`, right) → Settings.
- **Status chips row** (outlined assist chips, ≈32 dp tall):
  - Readiness chip: ✓ `check_circle` + "Ready to stop playback". When something is wrong, it changes to a warning state (see §5.15). Tap → readiness sheet.
  - Test chip: `timer` icon + "10 s test". Tap → 10-second test (§5.13).
  - The row must wrap or scroll gracefully at narrow widths and large font scales; never truncate.
- **Dial** (≈280 dp diameter, ≈12 dp stroke): circular slider. The arc starts at 12 o'clock and runs clockwise; a full circle = 60 min. The active arc is `primary`, the remaining track is muted, with small gaps between arc, thumb and track (M3 style). A ≈28 dp circular thumb sits at the end of the arc and is draggable.
  - Center: large countdown "30:00" (≈68–72 sp, tabular figures so digits don't jitter) and below it "Ends at 11:42 PM" (onSurfaceVariant). The end time updates live as the clock advances.
  - Drag snaps to whole minutes, range 1–60 min, with a haptic tick per minute (`CLOCK_TICK`/segment tick). It can't go below 1 min or wrap past 60.
  - Durations over 60 min (from Custom) show a full ring and `H:MM:SS`; dragging the thumb returns to the 1–60 range.
- **Presets row**: connected M3 Expressive button group (≈56 dp tall): "15 min", "30 min", "45 min", "60 min". The selected item morphs to a full pill (`primary`/`onPrimary`); unselected items have squared inner corners. Tapping a preset sets the dial. Dragging the dial selects a preset only when the value equals it.
- **Bottom row**:
  - Left: "Custom" — wide tonal icon button (≈72×56 dp, `schedule` icon) with label below → Custom duration sheet (§5.10).
  - Center: **Start** — large rounded-square button (≈96 dp, ≈28 dp corners, `primaryContainer`, `play_arrow`). Shape responds to press with expressive spring motion.
  - Right: "Nightly" — same style as Custom (`bedtime` icon) → Nightly schedule sheet (§5.7).

### 2.3 Home — running (`02-home-running.png`)

- Top bar: title only. The settings icon is hidden while running (follow the design).
- **Ring**: M3 Expressive *wavy* circular progress indicator (≈286 dp). The wavy `primary` arc is the **remaining** time, starting at 12 o'clock and running clockwise; the elapsed part is a flat muted track. The wave animates gently. Flatten the wave in the last ~3 % and when animations are disabled.
- Center: countdown "24:18" and "Ends at 11:42 PM".
- **Shake card** (surfaceContainerLow, rounded, `vibration` icon in primary): "Still awake? Shake your phone to add 10 minutes. Works with the screen off." The minutes value comes from the *Extend by* setting. Hide the card when Shake to extend is off or the device has no accelerometer.
- **Pipeline line** (onSurfaceVariant): "Fade out 15 s › Stop playback › Screen off" — built from the active settings; omit steps that are off. Mirror the chevrons in RTL.
- **Bottom row**: "− 5 min" (wide tonal pill, ≈111×56 dp) · **Stop** (same big button, `stop` icon) · "+ 10 min" (pill; amount = *Extend by*).
  - "− 5 min" is disabled when 5:00 or less remains.
- Transitions idle ⇄ running use shared-element/morph motion from `MotionScheme.expressive()`. No layout jumps.
- ➕ **Finished state**: when the timer ends while the app is visible, show "Playback stopped" in the center for ≈3 s, then return to idle.

### 2.4 Settings (`03-settings.png`)

A large collapsing top app bar ("Settings", back arrow), then sections. Each section header is a small label in `primary`. Rows are grouped into **segmented cards**: large outer corners (≈24 dp) on the first and last row, small inner corners (≈4 dp), ≈2 dp gaps, surfaceContainer fill, leading icon, title, optional supporting text, trailing control.

| Section | Row (title · supporting text) | Trailing | Behavior |
|---|---|---|---|
| Ending | Fade-out · "Lower the volume before stopping" | value "15 s" + slider below | §5.1 |
| | Turn screen off · "After playback stops" | switch | §5.3 |
| | Restore volume · "Back to where it was before the fade" | switch | §5.2 |
| Extending | Shake to extend · "Works with the screen off" | switch | §5.4 |
| | Extend by | "10 minutes" › | picker §5.5 |
| | One-minute warning · "So you can still extend" | "Soft vibration" › | picker §5.6 |
| Starting | Nightly schedule · "Starts itself at 11:00 PM" | switch | §5.7 |
| | Quick Settings tile | "Added" ✓ / "Add" | §5.8 |
| | Home-screen widget | "Add" | §5.9 |
| Appearance | Dynamic color · "Use colors from your wallpaper" | switch | §5.11 |
| | Theme | connected buttons Light / Dark / System | §5.11 |
| | Dim screen while running · "Lowest brightness, no flashing" | switch | §5.12 |
| Reliability | Run a 10-second test · "Check that your player really stops" | — | §5.13 |
| | Battery optimization · "Keeps the timer alive overnight" | "Unrestricted" ✓ | §5.14 |
| | Notification access · "Not required to stop playback" | "Off" / "On" | §5.14 |

The design shows a configured state. Defaults are in §4.6.

### 2.5 Android surfaces (`04-android-surfaces.png`)

- **Live Update** (status-bar chip "⏱ 24:18" + promoted notification): header "Sleep timer · now", title "Ends at 11:42 PM", text "24 min left · fades out over 15 s", progress bar with tracker dot, actions "+10 min" and "Stop". Details and fallback in §5.16.
- **Quick Settings tile**: "Sleep timer / Off" (inactive) and "Sleep timer / 24 min left" (active). §5.8.
- **Widgets**: (a) square dial widget, "30 / Tap to start" idle and "24:18 / Running"; (b) presets widget 15 / 30 / 45 / 60. §5.9.
- **App icon**: adaptive icon (moon) that works as circle, squircle and **themed (monochrome)**. §5.17.
- **Long-press shortcuts**: "Start 30 min", "Start 60 min", "Custom time". §5.17.

### 2.6 Color roles (never hard-code colors)

Prefer component defaults; override only where the design clearly differs.

| Element | Role |
|---|---|
| Screen background | `surface` |
| Settings rows, shake card | `surfaceContainer` / `surfaceContainerLow` |
| Section headers, dial arc, thumb, chip icons | `primary` |
| Selected preset / theme segment | `primary` / `onPrimary` |
| Unselected presets, Custom, Nightly, −/+ pills | `secondaryContainer` / `onSecondaryContainer` |
| Start / Stop button | `primaryContainer` / `onPrimaryContainer` |
| Dial track, elapsed track | `secondaryContainer` or `surfaceContainerHighest` (match design) |
| Countdown | `onSurface`; "Ends at", pipeline, supporting text: `onSurfaceVariant` |
| Chip outline | `outline` / `outlineVariant` |

**Fallback scheme** (Dynamic color off, or Android < 12): generate light and dark schemes with Material Theme Builder (Tonal Spot, the same algorithm Android uses for wallpaper colors) from a muted violet seed that reproduces the design. The design's dark samples are background `#0F0D12`, rows `#1B191E`, primary `#CDC0EC`, Start button `#574D72`, tonal buttons `#3D384C`, outline `#7A747E`. Verify visually against `/design`.

---

## 3. Tech stack

| Area | Choice |
|---|---|
| Language | Kotlin (latest stable, K2), coroutines + Flow |
| UI | Jetpack Compose (latest stable BOM), single Activity |
| Design system | **Compose Material 3 1.5.x** — the Material 3 Expressive APIs (`MaterialExpressiveTheme`, `MotionScheme.expressive()`, `ButtonGroup`/toggle buttons, `CircularWavyProgressIndicator`, flexible top app bars, expressive slider, `MaterialShapes`) live in the 1.5 line, which was in beta when this was written. Use the newest 1.5.x (stable if available, otherwise the latest beta/RC), override it above the BOM, keep `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` contained in `:core:designsystem`, and log the version in `DECISIONS.md`. Don't hand-roll a component that M3 provides. |
| Adaptive | `material3-adaptive` (`currentWindowAdaptiveInfo()`, window size classes), `androidx.window` for fold posture |
| Navigation | Navigation 3 if stable, otherwise type-safe Navigation Compose. Two destinations plus sheets. |
| DI | Hilt (KSP) |
| Storage | DataStore with kotlinx.serialization: one store for settings, one for the timer snapshot. Versioned schemas, corruption handlers. |
| Widgets | Jetpack Glance (appwidget + material3), RemoteViews interop where Glance can't do it |
| Background | `AlarmManager` + foreground service + short scoped wake locks (§4.3). No WorkManager for timing. |
| Icons | **Material Symbols Rounded**, imported as vector drawables, only the ones used. Don't depend on `material-icons-extended`. |
| Font | System default (`FontFamily.Default`). No bundled fonts. |
| Splash | `core-splashscreen` |
| Testing | JUnit4, Truth, Turbine, kotlinx-coroutines-test, Robolectric, Compose UI test, Roborazzi screenshot tests, Macrobenchmark + Baseline Profile plugin |
| Quality | Android Lint (key checks as errors), ktlint via Spotless, detekt, Compose lint rules, LeakCanary (debug only), StrictMode (debug only) |
| Build | Gradle Kotlin DSL, version catalog, convention plugins in `build-logic`, configuration cache, R8 full mode, resource shrinking, AAB |
| SDK levels | `minSdk 26`, `compileSdk` and `targetSdk` = latest stable API (37 at time of writing; Google Play already requires ≥ 36). The architecture in §4.3 must be compliant with Android 17's background-audio rules either way. |

No Firebase, analytics, ads, crash-reporting SDKs, or network libraries. Crash and ANR data come from Play Console Android vitals.

---

## 4. Architecture

### 4.1 Modules

```
app/                    Application, MainActivity, nav graph, manifest wiring, DI graph
build-logic/            convention plugins
core/model/             pure Kotlin: TimerState, Settings, durations, formatting rules
core/timer/             TimerEngine (state machine), commands, Clock abstraction, TimerService (FGS)
core/data/              DataStore sources + repositories, schema migrations
core/playback/          PlaybackStopper, VolumeFader, PlaybackProbe, MediaSessionBridge (optional listener)
core/system/            AlarmScheduler, WakeLockManager, ShakeDetector, Haptics, ScreenLocker (device admin),
                        PermissionState, OemBatteryGuide, Diagnostics log
core/designsystem/      theme, fallback schemes, typography, shapes, motion, Dial, SegmentedListGroup, etc.
feature/home/           idle + running screens, Custom sheet, Nightly sheet
feature/settings/       settings, readiness sheet, test flow
surface/notification/   notification builder (Live Update + fallback), channels
surface/tile/           TileService
surface/widget/         Glance widgets
baselineprofile/        baseline profile generator
benchmark/              macrobenchmarks
tools/testplayer/       debug-only media app for automated and manual stop tests (§11)
```

### 4.2 TimerEngine — the single source of truth

- One `@Singleton` engine owns timer state and is the only writer. Every entry point (UI, notification actions, tile, widgets, shortcuts, alarms, shake, boot/time receivers) sends a **command**. Commands are processed **serially** (a single-consumer channel or Mutex), and each is idempotent.
- **States**: `Idle(selectedDuration)` → `Running(runId, endElapsed, totalDuration, startedBy, hasWhileInUse)` → `Fading(…, fadeStartElapsed, originalVolume, outputDevice)` → `Stopping` → `Finished(result)` → `Idle`. Plus `Deferred(reason)` (call/alarm in progress, §6.E). The test (§5.13) is a flag on a run, not a separate engine.
- **Commands**: `Start(duration, source)`, `Stop(source)`, `Extend(delta)`, `Reduce(delta)`, `StartTest`, `ShakeDetected`, `AlarmFired(kind, runId)`, `Reconcile(trigger)`, `SettingsChanged`.
- **Time**: all countdown math uses `SystemClock.elapsedRealtime()` through an injectable `Clock`. Wall-clock time is used only to *display* "Ends at" and for the nightly schedule. Never use `currentTimeMillis` for the countdown and never use coroutine `delay()` (uptime-based, it stops during deep sleep) as the source of truth.
- **Persistence**: every state change writes a snapshot (`runId`, state, `endElapsed`, `totalDuration`, `Settings.Global.BOOT_COUNT`, fade bookkeeping, `pendingVolumeRestore`, schema version). On process start, the engine restores from it before anything reads state.
- **runId**: each run gets a new id. Alarms and PendingIntents carry it; anything from a previous run is ignored.
- **Output**: `StateFlow<TimerUiState>` observed by UI, notification, tile and widget controllers. Controllers never mutate state.

### 4.3 Execution model (the most important section — read twice)

**Platform facts this design is built on** (verify against current docs):

- Apps targeting Android 15+ get `AUDIOFOCUS_REQUEST_FAILED` unless they are the top app or running a foreground service.
- On Android 17, *all* apps need a visible activity or a non-`shortService` foreground service for audio focus **and volume changes** (`setStreamVolume` etc. fail silently). Apps targeting 37 additionally need the FGS to have **while-in-use (WIU)** capability when in the background. WIU is granted when the FGS starts while the app is visible or from a user interaction such as a notification click or widget tap; FGS started from the background (e.g., by an alarm) generally don't get it.
- Apps receive continuous sensor events in the background only while running a foreground service.
- Allow-while-idle alarms are rate-limited in Doze, so several alarms a few seconds apart near the end are unreliable.
- `delay()`/`Handler` timing is uptime-based and freezes in deep sleep unless a wake lock is held.

**Therefore:**

1. **Start the FGS at the moment the user starts the timer**, from a user-visible or user-interaction context (activity, notification action, widget tap, app shortcut). It runs for the whole timer. Type `specialUse` with `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` (Play declaration needed, §10). Call `ServiceCompat.startForeground` immediately in `onStartCommand`. Catch `ForegroundServiceStartNotAllowedException` and degrade gracefully (§6.B). Record whether the run has WIU (`hasWhileInUse`).
2. **One exact alarm per run** at the start of the "final window" = `end − max(60 s warning, fade, 0) − 5 s safety`, via `setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP)`. When it fires, acquire a **partial wake lock with a timeout** (window + 2 min) and run warning → fade → stop **in-process** on an elapsed-realtime scheduler. Release the wake lock in `finally`. Add one backup alarm at `end + 30 s` that runs the stop if the run is still active.
3. **Every change** (extend, reduce, settings change, time change) cancels and reschedules that run's alarms.
4. **No exact-alarm permission** (`canScheduleExactAlarms() == false`): ask once with a rationale (§5.15). If declined, hold a partial wake lock for the whole run (capped at duration + 2 min) so the stop is still on time, and show "Exact alarms off" in the readiness sheet. Never silently degrade.
5. **Shake detection** runs in the FGS. Use the wake-up accelerometer (`getDefaultSensor(TYPE_ACCELEROMETER, true)`) if present. Otherwise listen whenever the screen is on, and with the screen off only during the last 5 minutes plus the final window, holding a partial wake lock during that period. Document the battery trade-off.
6. **Runs without WIU** (nightly schedule fired by an alarm, and possibly Quick Settings tile — verify): audio focus and volume may fail on Android 17 when targeting 37. For these runs, skip the fade (detect by reading back the volume), and stop via media-session pause (if notification access is granted) and media-key dispatch, which are not audio-focus APIs. Verify on an Android 17 emulator with `adb shell cmd audio set-enable-hardening throw`, and log results in `DECISIONS.md`.
7. **Stop the FGS** once the stop pipeline and post-stop watchdog (§4.4) finish. Abandon audio focus first. Release every sensor and wake lock.

### 4.4 Stop-playback pipeline

Run in order, checking `PlaybackProbe` (`isMusicActive()` + `getActivePlaybackConfigurations()`) every ~250 ms for up to 3 s after each step. Stop escalating as soon as playback is inactive.

1. **Pre-checks.** If a call is active (`AudioManager.mode` ∈ IN_CALL / IN_COMMUNICATION / RINGTONE) or an alarm/ringtone is sounding (`USAGE_ALARM`, `USAGE_NOTIFICATION_RINGTONE` in active configs) → `Deferred` and retry when it ends (mode listener on API 31+, polling under a wake lock below that, capped at 2 h). Never request focus over an alarm. Doing so could silence the user's wake-up alarm.
2. **Media sessions** (only if notification access is granted): for every active `MediaController` whose state is PLAYING or BUFFERING → `transportControls.pause()`. This also covers remote/cast sessions.
3. **Audio focus**: `requestAudioFocus(AUDIOFOCUS_GAIN, USAGE_MEDIA)`. Handle GRANTED, DELAYED and FAILED explicitly.
4. **Media key**: `dispatchMediaKeyEvent` with **`KEYCODE_MEDIA_PAUSE`** down + up. **Never** use `PLAY_PAUSE` (it would *start* playback if the player is already paused). Send only if playback is still active.
5. **Verify**: record which step worked (for the test result and diagnostics). If playback is still active, retry 4 once, then report failure.
6. **Post-stop watchdog** (30 s, screen off only): if playback resumes on its own (some players auto-resume after focus loss) → pause again via 2/4. If the screen turns on, assume the user is acting intentionally and end the watchdog. Hold focus during the watchdog, then abandon it.
7. Then volume restore (§5.2), then screen off (§5.3), then `Finished`.

If nothing was playing at the end, skip fade/focus/keys entirely and finish quietly (screen-off still applies).

### 4.5 Manifest components and permissions

| Item | Notes |
|---|---|
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | timer service |
| `POST_NOTIFICATIONS` | runtime, Android 13+; app still works if denied |
| `SCHEDULE_EXACT_ALARM` | user-granted special access. **Do not** declare `USE_EXACT_ALARM` (Play reserves it for alarm/calendar apps). |
| `WAKE_LOCK`, `VIBRATE`, `RECEIVE_BOOT_COMPLETED` | |
| `POST_PROMOTED_NOTIFICATIONS` | Live Updates (normal permission) |
| `BIND_QUICK_SETTINGS_TILE` | TileService |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | optional media-session listener |
| `BIND_DEVICE_ADMIN` + `force-lock` policy | optional screen-off |
| **Not declared** | `INTERNET`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `USE_EXACT_ALARM`, any AccessibilityService |

Receivers: alarm receiver (not exported), `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`, `LOCALE_CHANGED`, `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`. Export only what the system requires. Every `PendingIntent` is `FLAG_IMMUTABLE` and explicit.

### 4.6 Settings defaults

| Setting | Default | Range |
|---|---|---|
| Last duration | 30 min | 1 min – 12 h |
| Fade-out | 15 s | Off (0), 5–60 s in 5 s steps |
| Turn screen off | Off (needs device-admin opt-in) | |
| Restore volume | On | |
| Shake to extend | On (hidden if no accelerometer) | |
| Extend by | 10 min | 5, 10, 15, 20, 30 min |
| One-minute warning | Soft vibration | Off, Soft vibration, Strong vibration |
| Nightly schedule | Off, 11:00 PM, every day, last duration | |
| Dynamic color | On (Android 12+; row hidden below) | |
| Theme | System | Light, Dark, System |
| Dim screen while running | On | |

Back up settings with Auto Backup (data extraction rules). Never back up the timer snapshot.

---

## 5. Feature behavior

**5.1 Fade-out.** At `end − fade`, if music is active and the volume is adjustable (`!isVolumeFixed`): record the `STREAM_MUSIC` index and the current output device, then step down to `getStreamMinVolume` (not mute) over the fade duration with an ease-out curve, without system UI (`flags = 0`). After each step, read the volume back. If it differs from what you set, the user touched the volume (or the platform blocked it) → abort the fade, don't restore, continue to stop at end. If the output device changes mid-fade → abort (see §6.D). Effective fade = `min(fade, remaining)`. During the test, cap fade at 5 s.

**5.2 Restore volume.** Only after playback is confirmed stopped for ≥ 1 s, wait ~1.5 s (players finish their own fade-out on focus loss), then restore the original index on the original device. **Never restore while music is active.** If playback is still active at the end of verification, keep the volume low and set `pendingVolumeRestore`; restore later when playback becomes inactive while the service is still alive (≤ 2 min), else drop it and log. If the process died mid-fade, on the next start restore only if music is inactive and the original device is the current output. Restores may be capped by hearing-safety limits (CSD/safe media volume) — read back and log; don't fight the system.

**5.3 Turn screen off.** Uses `DevicePolicyManager.lockNow()` via a device-admin receiver with only `force-lock`. Turning the switch on opens a clear explanation sheet, then the system "Activate device admin" screen. Tell the user plainly: *"After the timer locks your phone, the next unlock needs your PIN, pattern or password instead of fingerprint or face."* Turning the switch off offers to remove the admin. Settings must make uninstall possible (explain that the admin must be deactivated, and provide a button). Lock only after the stop pipeline, only if the screen is interactive, not during a call and not during the test. If the admin was revoked externally, flip the switch off and flag it in readiness. Don't use an AccessibilityService for this.

**5.4 Shake to extend.** Linear acceleration (gravity removed), ≥ 3 direction-alternating peaks above a threshold within ~1.2 s, 3 s cooldown. Tune on real devices with recorded traces so rolling over in bed doesn't trigger it, and unit-test the detector against those traces. On detection: `Extend(extendBy)`, confirmation haptic (two short pulses), update notification/tile/widget. If shaken during the fade: cancel the fade and ramp the volume back to the original over ~1 s. Hide the setting and card if there's no accelerometer.

**5.5 Extend by.** Radio picker (dialog or bottom sheet): 5, 10, 15, 20, 30 minutes. Drives "+ N min" on the running screen, the notification action, the shake card text and shake extension. Total remaining is capped at 12 h; show a brief message if capped.

**5.6 One-minute warning.** At `end − 60 s`, only if the run is ≥ 2 min. Soft = short, low-amplitude double pulse (falls back to a short tick without amplitude control); Strong = longer pattern. Previews when selected. Skip during calls. It must still work in Bedtime/DND with default settings, since that's exactly when this app is used: pick `VibrationAttributes` usage deliberately, test under Silent, Vibrate, DND and Bedtime mode, and document the choice. No sound option. A sound would wake partners.

**5.7 Nightly schedule.** Sheet with switch, start time (M3 TimePicker, respects 24-hour setting), duration (presets + custom), ➕ repeat days (connected toggle buttons, default every day). The next occurrence is computed with `java.time` in the current zone, so DST gaps and overlaps are handled. Scheduled with an exact alarm; rescheduled after it fires, on boot, time/zone change, app update and permission change. At fire time: if a timer is already running, do nothing; if the user stopped tonight's scheduled run, don't restart it. Start the run even if nothing is playing, so it can stop audio started later. Runs started this way have no WIU (§4.3.6). If notification access is off, the Nightly sheet recommends enabling it for reliable nightly stops.

**5.8 Quick Settings tile.** Label "Sleep timer", subtitle "Off" / "N min left" (plurals; "Less than 1 min" under a minute), state active/inactive, moon icon. Tap: inactive → start last duration; active → stop. Long-press → app (`ACTION_QS_TILE_PREFERENCES`). Works on the lock screen without unlocking. Keep the subtitle fresh while listening (`requestListeningState` on state changes). Track added/removed via `onTileAdded`/`onTileRemoved` for the Settings row. Settings → "Add" uses `StatusBarManager.requestAddTileService` (API 33+) or shows instructions below. Verify whether a tile-started FGS has WIU on Android 17; if not, start through `startActivityAndCollapse(PendingIntent)` to a no-UI trampoline activity.

**5.9 Widgets (Glance).**
- *Dial widget* (2×2, resizable): idle = ring with last duration + "Tap to start" → starts timer; running = ring + countdown + "Running" → opens app (avoid accidental stops).
- *Presets widget* (4×1, resizable): connected 15/30/45/60. Tap → start that duration (or restart if running). The selected item = current run's duration, or last used.
- Constraints: **no per-second widget updates**. The countdown uses a RemoteViews `Chronometer` in countdown mode; the ring updates at most once a minute, pushed by the engine (not `updatePeriodMillis`). Dynamic color via GlanceTheme, system corner radius, responsive sizes, content descriptions, generated previews (API 35+) plus a static preview fallback, dark mode. Settings → "Add" uses `requestPinAppWidget`.

**5.10 Custom duration.** Bottom sheet with hours (0–12) and minutes (0–59) input in M3 style, quick chips (90 min, 2 h, 3 h), and a Start button. Validates 1 min – 12 h. Remembered as last duration.

**5.11 Dynamic color and theme.** Dynamic color on by default (Android 12+). Light/Dark/System via `UiModeManager.setApplicationNightMode` on API 31+ (so the splash screen matches with no flash), in-app fallback below. Apply to widgets too. Both light and dark must look right — the design only shows dark.

**5.12 Dim screen while running.** While the running screen is visible: set the window brightness override to the minimum, applied before the first frame (so it doesn't flash bright), restored on leave or stop. Doesn't keep the screen on. No bright flashes anywhere at night: splash, transitions and dialogs must follow the theme.

**5.13 10-second test.** From the home chip or Settings. If nothing is playing, explain: "Start something in your player, then come back — or start the test and switch to your player within 10 s." Run the real pipeline with a 10 s timer (fade ≤ 5 s, no screen-off), then show a result dialog/notification: ✓ "Your player stopped" (and which method worked), or ✗ "Your player is still playing" with the fix (enable notification access; check battery settings; known limitation for cast). Store the last result for readiness. Disabled while a timer is running.

**5.14 Reliability rows.**
- *Battery optimization*: show "Unrestricted ✓" / "Optimized". Tap → `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` or app details. Don't use `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Add OEM-specific instructions for Samsung, Xiaomi/Redmi/POCO, OnePlus, Oppo/Realme, Vivo, Huawei/Honor, Motorola (autostart, "sleeping apps", background lock) — detected via `Build.MANUFACTURER`, with a link to dontkillmyapp.com opened in the browser.
- *Notification access*: optional. Explain that it's used only to pause media sessions directly, which is more reliable for some players and cast. Deep-link to the listener detail settings (API 30+) or the list. Show "On"/"Off".

**5.15 Readiness.** The home chip shows "Ready to stop playback" ✓ when nothing critical is wrong. Otherwise it shows a warning state, e.g. "1 thing to fix", and taps open a sheet listing: Exact alarms (critical), Notifications (recommended), Battery unrestricted (recommended), Last test result, Notification access (recommended if the test failed), Screen-off admin (if the switch is on but the admin is inactive). Each item has a one-tap fix. Permissions are requested in context: start the timer first (so the FGS gets WIU), *then* ask for notifications; ask for exact alarms with a short rationale the first time. Never block the user from starting a timer.

**5.16 Notification and Live Update.**
- Channel "Timer running": low importance, no sound/vibration/badge, public visibility.
- Content per the design. The status chip/countdown uses `setUsesChronometer(true)` + `setChronometerCountDown(true)` + `setWhen(endWallClock)`; re-post when wall time changes (time set, zone change). "N min left" and progress update once a minute (`setOnlyAlertOnce`). Actions: "+N min", "Stop". Tap → app.
- **Android 16 QPR1+**: request promotion (`setRequestPromotedOngoing` / `EXTRA_REQUEST_PROMOTED_ONGOING`), `ProgressStyle` with a tracker icon, `setShortCriticalText` where helpful. Check `canPostPromotedNotifications()`. No custom RemoteViews, not a group summary.
- **Android 8–16.0** (including the owner's Android 15 phone): a regular ongoing notification with the same content, chronometer countdown and standard progress bar. No status chip exists on these versions; that's expected.
- If the user dismisses it: don't re-post until the next state change (use `setDeleteIntent`). The timer keeps running.
- If notifications are denied: the timer still works; readiness shows it.
- ➕ Channel "Alerts" for test results and "timer interrupted" notices.

**5.17 Icon and shortcuts.** Adaptive icon with foreground (moon), background and **monochrome** layer; also a monochrome notification icon and tile icon. Static shortcuts: Start 30 min, Start 60 min, Custom time. They open MainActivity with an action (which starts the FGS with WIU); validate inputs; if a timer is running, replace it and show a snackbar with Undo. Report shortcut usage.

---

## 6. Edge cases — every one needs a unit test, instrumented test or an entry in the manual test plan

**A. Time and clock**
- Manual clock change, NTP correction, timezone change and DST during a run: countdown unaffected; "Ends at" and notification `when` recomputed.
- DST gaps and overlaps for nightly schedule (test Asia/Kolkata, Europe/London, America/New_York, Australia/Lord_Howe).
- Runs crossing midnight; 12/24-hour setting and locale changes while running.
- Remaining-time rounding: "24 min left" at 24:18; "Less than 1 min" under a minute; plurals in all locales.

**B. Lifecycle and process**
- Process death mid-run (`adb shell am kill`): engine restores; alarms still fire; FGS restarts sticky.
- Reboot mid-run: snapshot `BOOT_COUNT` mismatch → discard run (playback stopped anyway); reschedule nightly.
- Late stop: if the process was dead past `end`, run the stop only if ≤ 30 min late and the screen is off; otherwise post "Your timer was interrupted" with a link to battery fixes. Use `ApplicationExitInfo` to explain why where possible.
- Force-stop (Android 15 also cancels pending intents): detect on next launch and explain.
- App update mid-run: reconcile and reschedule on `MY_PACKAGE_REPLACED`.
- `ForegroundServiceStartNotAllowedException`: fall back to alarm-only mode and tell the user to open the app.
- `startForeground` must be called within the system deadline in every code path.
- Stale alarms or PendingIntents from previous runs (runId mismatch) are ignored.
- Rapid repeated taps on start/stop from different surfaces: serialized, idempotent, no duplicate services.
- DataStore corruption → defaults, logged; schema migration tested.
- Low memory, Battery Saver, App Standby "restricted" bucket.

**C. Playback**
- Nothing playing at end. Multiple players. A player that ignores focus. A player that auto-resumes (watchdog). Video apps in the foreground. Browser tab audio. Cast/remote playback (needs notification access; otherwise documented limitation).
- Headphones unplugged mid-run (`AUDIO_BECOMING_NOISY` — the player pauses itself, the timer continues).
- The user starts playback again after the stop (must not be fought outside the watchdog).

**D. Volume**
- User changes volume during fade → abort, no restore.
- Output device changes mid-fade (Bluetooth connects/disconnects) → abort; restore the original device later only when it's the active output and nothing is playing; drop after 12 h.
- Fixed-volume devices, Bluetooth absolute volume lag, few volume steps, min volume > 0, hearing-safety caps on restore.
- Process death mid-fade → safe restore rules in §5.2.

**E. Interruptions**
- Phone/VoIP call active, ringing, or starting during fade → defer and resume after.
- Wake-up alarm ringing at end time → defer; never request focus over it.
- DND, Bedtime mode, Silent mode → warning vibration behavior per §5.6.

**F. Screen off**
- Admin revoked externally; screen already off; in a call; during the test; work-profile installs.

**G. Shake**
- No accelerometer; false positives in bed; shake during fade; shake within the cooldown; shake after stop (ignored); screen-off delivery with and without a wake-up sensor.

**H. Permissions and system settings**
- Notifications denied or channel disabled; exact alarms denied then granted (and revoked mid-run → fall back to wake lock); notification access revoked; promoted notifications disabled by the user; battery "Restricted".

**I. Surfaces in sync**
- App, notification, tile and both widgets always show the same state within 1 s of a change; this holds after process death, locale change and theme change. Widgets/tile after "clear data".

**J. Nightly**
- Fires while a timer is running; fires when the phone was off (skip); user stopped tonight's run; time changed after scheduling; permission missing.

**K. Devices and OEMs**
- Aggressive battery managers (Samsung, Xiaomi, Oppo/Realme, Vivo, OnePlus, Huawei/Honor). Devices without vibrator or with no amplitude control. Android Go / low-RAM devices.

**L. UI and input**
- Rotation and fold/unfold mid-drag; multi-window and resizing; process death with a sheet open (`rememberSaveable`); predictive back; keyboard and D-pad on the dial; TalkBack; Switch Access; 200 % font; display size "largest"; RTL.

---

## 7. Adaptive layout and form factors

- **Edge-to-edge** (enforced since targeting 35) with correct insets for status bar, cutouts, gesture and 3-button navigation, and IME (Custom sheet).
- **Compact portrait** (the design): dial size = min(width × 0.75, available height × 0.42, 320 dp). Countdown text auto-sizes to fit the dial (`BasicText` auto-size or equivalent) and never truncates `H:MM:SS`.
- **Narrow widths (320–359 dp) and large fonts**: chips wrap; preset labels may drop the " min" suffix visually (keep it in content descriptions); bottom buttons keep ≥ 48 dp targets. Never clip or overlap.
- **Compact height** (landscape phones): two panes — dial left, controls right.
- **Medium/expanded** (tablets, unfolded foldables, desktop windows): centered content with max widths; consider a two-pane home. Settings: single column, max ≈ 840 dp.
- **Foldable tabletop posture**: dial in the top half, controls in the bottom half.
- Support every orientation and resizability. Android 16+ ignores orientation/resizability restrictions on large screens for apps targeting 36+, so don't lock orientation.
- Predictive back for screens and sheets.

---

## 8. Accessibility and localization

- Dial: slider semantics with range info, step = 1 min, custom actions (+1, −1, +5, −5 min), keyboard arrows ±1 and Page Up/Down ±5. Content description like "30 minutes. Ends at 11:42 PM."
- Countdown: don't announce every second. Running state description is read on focus; announce start, extend, warning and stop events once.
- 48 dp touch targets; contrast ≥ 4.5:1 for text in both themes and with dynamic color; full TalkBack traversal order matching the visual order; headings for section titles.
- Respect "Remove animations" (`ValueAnimator.areAnimatorsEnabled()`): no wave motion, instant transitions.
- Haptics respect system settings.
- All strings in resources with plurals; no string concatenation for sentences; locale-aware time formats (`DateFormat.getBestDateTimePattern`) and digits; per-app language support (generated locale config). Pseudo-locales (en-XA, ar-XB) must pass screenshot tests.

---

## 9. Performance and battery budgets

- Cold start to first frame < 500 ms on a mid-range device with a baseline profile.
- No dropped frames during dial drag, countdown and wave animation (Macrobenchmark `FrameTimingMetric`).
- Per-second UI tick recomposes only the countdown text, aligned to the next whole second of elapsed time. UI ticking stops when not visible (`collectAsStateWithLifecycle`).
- No disk or binder work on the main thread (StrictMode in debug).
- **Battery**: with the screen off, the CPU may sleep for the whole run except the final window and the shake window. Every wake lock has a timeout and a `finally` release. Play's Android vitals tracks excessive partial wake locks — keep them short and log their durations in diagnostics.
- Download size target < 5 MB. No memory leaks (LeakCanary clean).

---

## 10. Privacy, security and Play policy

- No `INTERNET`. No analytics, ads or third-party SDKs that collect data. Data safety form: no data collected or shared.
- ➕ Local diagnostics: a ring buffer of timer events (no personal data, no app names unless the user shares it) that the user can export via the share sheet from Settings › Reliability. Helps support at scale.
- Components not exported unless required; permission-protected system bindings; explicit, immutable PendingIntents; validate every intent extra (durations within bounds, known actions only).
- Play submission doc (`docs/PLAY_SUBMISSION.md`): `specialUse` FGS declaration text and demo-video script; `SCHEDULE_EXACT_ALARM` justification; device-admin and notification-listener prominent-disclosure copy; privacy policy text; 16 KB page-size compliance check; target API compliance.

---

## 11. Testing and QA

- **Unit** (pure JVM, fake `Clock`): every engine transition and command; extend/reduce/fade overlaps; runId staleness; reboot detection; late-stop policy; formatting and rounding; nightly next-occurrence across DST; shake detector on recorded traces; volume-restore safety rules.
- **Robolectric**: receivers, alarm scheduling, notification content per API level, tile state, settings persistence.
- **Compose UI tests**: dial semantics and drag; presets; button enable/disable rules (− 5 min); sheets; readiness states.
- **Screenshot tests (Roborazzi)**: every screen and state × light/dark × dynamic off × font scale 1.0/1.3/2.0 × widths 320/360/390/600/840 dp × RTL × pseudo-locales. Compare the idle and running screens against `/design` and list any visible differences in `PROGRESS.md`.
- **`tools:testplayer`** (debug-only app): plays a tone with a MediaSession and simulates good, focus-ignoring and auto-resuming players. Use it in instrumented tests of the stop pipeline.
- **Instrumented tests on emulators** API 26, 29, 31, 33, 34, 35, 36, 37: FGS start paths, exact alarm under forced Doze, stop pipeline with the test player, Android 17 hardening mode.
- **Macrobenchmarks** for startup and frame timing; generate and ship the baseline profile.
- **`docs/MANUAL_TEST_PLAN.md`**: step-by-step scripts for my Android 15 phone with real apps (Spotify, YouTube Music, YouTube, a podcast app, Audible, VLC, Chrome tab audio), screen-off overnight runs, Bluetooth headphones, calls during fade, alarm at end time, Bedtime mode, battery "Optimized" vs "Unrestricted". Include helpful adb commands: `dumpsys deviceidle force-idle`, `dumpsys alarm`, `dumpsys activity services <pkg>`, `dumpsys media_session`, `dumpsys audio`, `cmd appops set <pkg> SCHEDULE_EXACT_ALARM deny`, `am kill <pkg>`, `cmd audio set-enable-hardening throw` (Android 17).

---

## 12. Release readiness

- R8 full mode with verified keep rules (Hilt, Glance, DataStore, serialization); release build smoke-tested.
- Signing via environment/Gradle properties, never committed. Play App Signing, AAB.
- versionCode/versionName scheme; changelog.
- Staged rollout plan (1 % → 10 % → 50 % → 100 %) gated on crash-free rate, ANR rate and wake-lock vitals.
- Store-listing screenshot plan generated from screenshot tests.

---

## 13. Phases and Definition of Done

Each phase is done only when: it builds, all tests and lint pass, `PROGRESS.md` is updated, and you've given me device test steps.

| Phase | Scope | Done when |
|---|---|---|
| **0 Foundation** | `PLAN.md`, `DECISIONS.md`, build-logic, modules, version catalog, theme (dynamic + fallback, expressive motion), icons, splash, edge-to-edge, navigation skeleton, CI-style Gradle tasks | Themed empty screens run on API 26 and 37 emulators; lint clean |
| **1 Reliability core** | model, engine, persistence, AlarmScheduler, WakeLockManager, TimerService (WIU-aware), basic notification, stop pipeline, fade/restore, testplayer, unit + Robolectric tests | On a device: a 2-min timer with screen off and forced Doze fades and stops Spotify within 1 s of the end; restore is safe; all §6.B tests pass |
| **2 Home UI** | idle and running screens per design, dial, presets, wavy ring, Custom and Nightly sheets, transitions, haptics, accessibility semantics | Screenshot tests match the design; TalkBack and keyboard operate the dial |
| **3 Settings and features** | every Settings row, readiness chip and sheet, 10 s test, shake, warning, screen-off admin, dim, theme, battery and notification-access flows | Every row works end-to-end; §6.D–H covered |
| **4 System surfaces** | Live Update (36.1+) with fallback, QS tile, both widgets, shortcuts, adaptive/themed icon | All surfaces stay in sync (§6.I) |
| **5 Hardening** | full edge-case matrix, adaptive layouts, i18n/RTL, OEM guidance, diagnostics, process-death/reboot/late-stop handling | `MANUAL_TEST_PLAN.md` complete; every §6 item has a test or manual entry |
| **6 Release** | R8, baseline profile, macrobenchmarks, `PLAY_SUBMISSION.md`, final QA pass | Release AAB builds; budgets in §9 met and measured |

---

## 14. Don'ts

- Don't hard-code colors, use custom fonts, or add brand styling.
- Don't use XML views for app UI (RemoteViews for widgets/notifications only where required).
- Don't use `currentTimeMillis` or `delay()` as the countdown source of truth.
- Don't send `KEYCODE_MEDIA_PLAY_PAUSE`. Don't request focus over a ringing alarm or during a call.
- Don't restore volume while anything is playing.
- Don't declare `INTERNET`, `USE_EXACT_ALARM`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` or an AccessibilityService.
- Don't update widgets every second or hold wake locks without a timeout.
- Don't re-post a notification the user dismissed until the state changes.
- Don't block starting a timer behind a permission dialog.
- Don't claim a test, build or device behavior you didn't actually run.
