# Sleep timer — hardening test plan

Updated November 12, 2026 (Asia/Kolkata). Covers every edge case in AGENTS.md §6 and the adaptive/accessibility requirements in §§7–8. Stop cancels a timer and leaves playback running. Expiry runs the guarded playback-stop pipeline.

## Evidence and result recording

Every device case below starts **PENDING**. Automated references identify existing coverage, not a device pass. Record a case as PASS or FAIL only after observing it; include date, APK commit, Android/API, device, player/version, route, exact-alarm/notification access, battery mode, expected deadline, first stop attempt, confirmed stop time and any diagnostic event codes. Record NOT AVAILABLE with a reason for hardware/platform cases the phone cannot exercise. Keep failures and retests as separate entries.

Current evidence is in [PROGRESS.md](PROGRESS.md). Historical Android 15 installation and repeated explicit Custom launches do not establish surface latency or background reliability. No phone was connected at the start of Phase 5. Do not infer API 37 behavior from Robolectric API 35.

Use one result row per case/player/route/configuration:

| Date / commit | Case / configuration | Expected / observed | Result | Diagnostics / notes |
|---|---|---|---|---|
| — | — | — | PENDING | — |

## Prepare and install

From the repository root on macOS, connect and authorize USB debugging:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$HOME/Library/Android/sdk/platform-tools:$PATH"
export TIMER_PKG=com.sujal.sleeptimer.debug
adb devices -l
./gradlew verifyFoundation :feature:home:verifyRoborazziDebug :feature:settings:verifyRoborazziDebug
./gradlew :app:installDebug :tools:testplayer:installDebug
adb shell am start -W -n "$TIMER_PKG/com.sujal.sleeptimer.MainActivity"
```

Preserve any timer you rely on before installation, force-stop, reboot, clear-data or access-revocation tests. Use a spare test device for destructive state tests. Keep media at a comfortable level; an audible tone is optional and the debug test player starts silent. Do not grant screen-lock admin unless testing F; its next unlock needs a PIN, pattern or password.

Start with the in-app 10 s test, then the two-minute real-player run below. Set fade 15 s, restore on, screen-off off and the intended warning/shake settings. Allow notifications, grant exact alarms through readiness and note whether battery is Optimized or Unrestricted. Notification access is optional; repeat player failures with it enabled. Record the initial music volume and route.

## Core run and forced Doze procedure

1. In the chosen player, start media, then start a two-minute Sleep timer. Record the displayed end time and an independent monotonic stopwatch/video reference. Confirm the running notification and use the power button to turn the screen off.
2. For the Doze variant only, run the following after starting the timer. ADB charging can prevent natural idle, so simulate unplugged battery state:

```sh
adb shell dumpsys battery unplug
adb shell dumpsys deviceidle force-idle
adb shell dumpsys deviceidle get deep
adb shell dumpsys activity services "$TIMER_PKG"
adb shell dumpsys alarm
```

3. Observe fade and the actual player's stop, measuring against the recorded deadline. Require first stop attempt and playback cessation within the brief's one-second device gate; distinguish a timely attempt from a player taking longer to pause. Check safe restore only after settled silence, and verify that paused playback never starts again. Do not use Home's rounded text as a sub-second timing instrument.
4. Always restore the test overrides, including after a failed run:

```sh
adb shell dumpsys deviceidle unforce
adb shell dumpsys battery reset
```

5. Reopen the app. Confirm idle/finished state, no leftover timer service/partial wake lock/sensor, and Settings → Reliability → Share diagnostics. Review the report and cancel the Sharesheet unless intentionally sharing it. It contains current-process internal event codes and relative times; it does not contain player names/media metadata. Broad system dumps such as media_session/audio can contain other apps' information; inspect them locally and use the app's report for support.

Run this procedure with Spotify, YouTube Music, YouTube, a podcast app, Audible, VLC and Chrome tab audio. Record each separately, with speaker and Bluetooth as applicable, notification access off/on, battery Optimized/Unrestricted and screen-on/off. Repeat 30 min, 3 h and an overnight run under natural idle; a two-minute forced run is not overnight/OEM survival evidence.

## A — Time and clock

Automated references: `TimerEngineSchedulingTest.wallClockChangeDoesNotChangeElapsedDeadlineAndCloseInvalidatesEffects`, `NightlyScheduleTest`, `HomePresentationTest`. Device status for A01–A04: **PENDING**.

| ID | Exact procedure | Expected result |
|---|---|---|
| A01 | Start 30 min; note remaining; disable automatic time and move clock +1 h, then −1 h; restore automatic time. Repeat a network/NTP correction where available. | Elapsed remaining does not jump; Home end time and notification `when` refresh to the new wall clock. |
| A02 | During a run change zone Kolkata → London → New York → Lord Howe, then restore the original zone. For Nightly, use an emulator's DST gap and overlap dates in each DST zone. | Countdown unchanged; one next local occurrence, no duplicate overlap run; gap follows zone rules. Kolkata has no DST. |
| A03 | Start a run across local midnight; while running toggle 12/24-hour format and change app/system locale, then restore. | Correct next-day end display, locale digits and time format on Home/notification/widgets; same deadline. |
| A04 | Observe 24:18, 1:00, 0:59 and 0:01; repeat singular/plural locales and RTL. | 24 min left at 24:18, Less than 1 min below a minute, correct singular/plural and no negative display. |

## B — Lifecycle and process

Automated references: `TimerEngineRecoveryTest`, `TimerEngineCommandTest` and `TimerEngineSchedulingTest` recovery, late-stop, durable cancellation, stale-action, storage-failure and service-loss cases; `TimerServiceIntentsTest`; `AndroidAlarmSchedulerTest`; `JsonStoresTest`/snapshot validation. Device status for B01–B14: **PENDING**.

| ID | Exact procedure | Expected result |
|---|---|---|
| B01 | Start 30 min, background it, note PID, run `adb shell am kill "$TIMER_PKG"`, then recheck PID/service and reopen. If the FGS survives, mark the kill attempt inconclusive and repeat actual process-death testing on a debug emulator. | Restored original elapsed deadline, new process verified where applicable, one service, alarms retained/reconciled. `am kill` alone is not proof of death. |
| B02 | Start a run, reboot a spare device, unlock and open the app. | Old elapsed run discarded on BOOT_COUNT mismatch; readiness explains interruption and links to battery guidance; one silent alert if notifications are allowed; no stale stop/volume increase; Nightly gets a future occurrence. |
| B03 | On a debug emulator terminate the process with a run near expiry, keep screen off, restart within 30 min late; repeat >30 min late and with screen on. | Only ≤30 min late plus screen off may execute a stop; other cases show interruption and battery guidance. Exit reason is local diagnostics, not a player name. |
| B04 | Start a timer; `adb shell am force-stop "$TIMER_PKG"`; explicitly reopen. Repeat on API 35. | No promise of operation while force-stopped; no stale run resurrection; readiness explains interruption on next launch where startup history supports it; a dismissed recovery alert is not reposted on subsequent launches. API 35 pending intents may be cancelled. |
| B05 | Start 30 min and install a newly versioned compatible debug APK over it (`adb install -r ...`); reopen before deadline. | Original deadline survives and alarms/Nightly reconcile on package replacement. No duplicate service or test result replay. |
| B06 | Use the emulator's restricted-background/start-path scenario to deny a background FGS start, then open Home. Also test rejected foreground promotion. | No crash or deadline exception; alarm-only degradation is visible; opening app restores eligible foreground execution. Host test is not proof that an OEM allows the fallback. |
| B07 | Exercise activity, shortcut, widget, tile, notification action, Nightly, alarm and sticky restart; inspect `dumpsys activity services "$TIMER_PKG"`. | Every service entry promptly promotes before waiting for storage/engine; no ForegroundServiceDidNotStartInTimeException. No fabricated WIU on alarm/sticky starts. |
| B08 | Save an old notification action/alarm identity in an instrumented test, replace the run, then deliver the old action. | Old run/generation ignored; current run and deadline unchanged. Never replay arbitrary service intents against a user timer. |
| B09 | Rapidly start from Home/widget/shortcut, extend twice, cancel twice and tap the tile in a controlled test. | Serial accepted commands, distinct taps count once each, duplicate deliveries do not; stale inactive taps cannot replace a running timer; one service. |
| B10 | In an isolated debug install corrupt each DataStore file independently, reopen; repeat with unsupported schema/invalid semantic data and a previous schema-1 fixture. | Defaults/fail-closed recovery and a diagnostic code; valid supported fields preserved; no malformed deadline or unsafe volume restore. Reinstall/clear only the test install afterward. |
| B11 | Repeat the core run under low memory, Battery Saver and the restricted bucket; restore original bucket afterward. | No crash/ANR or leaked resources; record any OS-delayed/killed run as failure/degradation, never an inferred pass. |
| B12 | Start during simulated storage write failure, then separately fail storage after a start was accepted. | New unpersisted start rejected; an already-durable active timer still stops on time; no unbounded lease or blocked actor. |
| B13 | Extend/reduce/change fade near the final window, including several rapid changes in Doze and exact access off. | Old effects invalidated; bounded fallback protection preserves the new deadline; no premature old-alarm stop or repeated warning. |
| B14 | After every completion, cancellation, rejected start and exception path inspect service, sensors and wake locks. | Focus abandoned, service stopped, no sensor listener or held timer wake lock. Diagnostic acquire/release durations are bounded; automatic timeout is not counted as a manual release. |

Useful read-only inspection (system dumps remain local):

```sh
adb shell pidof "$TIMER_PKG"
adb shell dumpsys activity services "$TIMER_PKG"
adb shell dumpsys power
adb shell dumpsys sensorservice
adb shell am get-standby-bucket "$TIMER_PKG"
adb shell am set-standby-bucket "$TIMER_PKG" restricted
# After B11, restore the exact bucket recorded above, for example:
adb shell am set-standby-bucket "$TIMER_PKG" active
```

Do not use force-stop as a substitute for process-death testing: Android deliberately blocks further app execution until the user relaunches it.

## C — Playback

Automated references: `PlaybackPipelineTest`, `AndroidPlaybackBackendTest`, debug `tools:testplayer`. Device status for C01–C08: **PENDING**.

| ID | Exact procedure | Expected result |
|---|---|---|
| C01 | Expire with nothing playing, then repeat with optional screen-off enabled outside the test. | No fade/focus/media keys; quiet finish; screen locking only under its separate safety/consent guards. |
| C02 | Use each real player listed above, then two simultaneous players with notification access enabled/disabled. | Verified inactive playback; session pause/focus/PAUSE escalation stops when successful. Report actual method and any local-only uncertainty. |
| C03 | In debug test player select focus-ignoring mode and run 10 s test, then a normal screen-off run. | PAUSE down/up fallback; no PLAY_PAUSE; at most the bounded retry if still active. |
| C04 | In debug test player select auto-resume; expire screen off, then turn screen on during the 30 s watchdog. | Auto-resume is paused while screen off; screen-on ends watchdog and user playback is respected. Focus is eventually abandoned. |
| C05 | Run with foreground video (YouTube/VLC), browser-tab audio and background podcasts/Audible individually. | Each actual player stop measured; no toggle starts a player already paused. |
| C06 | Cast/remote playback, notification access off then on; repeat disconnect/reconnect near expiry. | Access on allows direct session pause; absent/vanished remote visibility never becomes a false verified remote success. Limitations are reported. |
| C07 | Unplug wired headphones or disconnect Bluetooth mid-run. | Player may pause itself; timer continues to original deadline; route safety prevents inappropriate fade/restore. |
| C08 | After watchdog completes, deliberately resume playback; also resume after user Stop. | No further timer pause outside watchdog; cancellation never stops playback. |

## D — Volume and route safety

Automated references: `VolumeFaderTest`, pending-restore tests in `PlaybackPipelineTest`, guarded backend tests. Device status for D01–D08: **PENDING**. Current native fade eligibility is deliberately conservative: API 26–32 and ambiguous/unobservable routes skip volume changes; see [playback README](../core/playback/README.md).

| ID | Exact procedure | Expected result |
|---|---|---|
| D01 | During fade press a volume key up/down; repeat software volume change from the player. | Readback mismatch aborts fade; no later automatic restore or upward correction; stop deadline remains. |
| D02 | Connect/disconnect Bluetooth during fade, reconnect original route after stopping and after the restore deadline. | Fade aborts; no raise on another route. Current fail-closed implementation drops a changed-route record rather than restoring hours later. No delayed loud playback. |
| D03 | Test fixed-volume output, few-step volume, nonzero stream minimum and Bluetooth absolute-volume lag. | No mute; never below minimum; fixed/uncertain output skipped; readback mismatch not fought. |
| D04 | Use a safe hearing-volume cap during restoration. | Read back capped result, log rejection/cap; do not fight system limits or repeatedly raise volume. |
| D05 | Kill process during an eligible fade; restart once while audio active, once silent on same output, once with output changed. | No increase while active or on changed/unknown output; safe same-route silent recovery only. |
| D06 | Expire with an uncooperative player still active for >2 min. | Keep volume low; pending restore expires/drops within the bounded window and never raises active media. |
| D07 | Shake/extend during fade, then cancel during a later fade. | Invalidated fade stops; allowed user-initiated recovery ramps gently to at most the original index, with fresh route/call/user-volume checks. |
| D08 | Start an alarm/call or change volume/route while a recovery ramp is in progress. | No further upward writes after guard changes; never request focus over the interruption. |

The brief's §6.D mentions retaining a route restore for 12 h, while §5.2 limits pending restore to 2 min. The implementation deliberately uses the shorter safety limit and drops changed-route restoration (D014/D030). Mark this known behavior, not a 12 h restoration pass.

## E — Interruptions and warning policy

Automated references: interruption/focus tests in `PlaybackPipelineTest`, deferral deadline/cleanup in `TimerEngineSchedulingTest`, backend alarm/call observation tests. Device status for E01–E05: **PENDING**.

| ID | Exact procedure | Expected result |
|---|---|---|
| E01 | Place/receive phone and VoIP calls before expiry; repeat ringing at end and a call beginning during fade. | Fade stops safely, no focus/PAUSE over call; Deferred state, original bounded retry deadline retained across reconcile. |
| E02 | Set a real wake-up alarm to ring at the timer's deadline; leave it ringing, then dismiss. | Alarm remains audible; no timer focus request over it. Stop retries after interruption ends. |
| E03 | Continue interruption beyond the two-hour retry cap on a test emulator, including process reconciliation. | Interrupted result, bounded cleanup; reconciliation never grants a fresh two-hour window. |
| E04 | For Soft/Strong/Off warning, repeat at normal, Silent, Vibrate, DND and Bedtime settings; note amplitude support and system haptic settings. | One warning per eligible run, no sound, no warning for 10 s test/<2 min, no vibration over call. Record actual system-policy suppression; do not claim a DND bypass. |
| E05 | Extend just before/after warning, including near the cutoff while screen off. | At most one persisted warning; accepted extension and updated deadline agree on all surfaces. |

## F — Screen off and brightness

Automated references: screen-lock guards in `TimerEngineCommandTest`, brightness/window tests, `AndroidScreenLocker`. Device status for F01–F06: **PENDING**.

| ID | Exact procedure | Expected result |
|---|---|---|
| F01 | Enable Turn screen off; read disclosure; decline, then accept Android admin activation. Complete a normal run. | Decline leaves off. Accepted screen lock follows quiet/successful stop only; disclosure correctly explains PIN/pattern/password requirement. |
| F02 | Revoke admin externally while enabled and return to Settings. | Switch off, readiness flags revoked permission, no crash at expiry. |
| F03 | Expire when already screen off; repeat active call/failed stop and 10 s test. | No inappropriate lock; test never locks; call/failed playback cannot be locked over. |
| F04 | In Settings deactivate admin, then uninstall a spare install. Repeat unsupported/work-profile device-policy paths. | Clear deactivation/uninstall path; managed-policy denial handled gracefully, no admin privileges beyond force-lock. |
| F05 | Enable dim; start while Home visible; navigate away, background, rotate, return, cancel and cold-launch in dark mode. | Minimum brightness before running content, restored on leave/stop, no keep-screen-on flag or bright flash. |
| F06 | Run Light/Dark/System and dynamic off/on on APIs 26/31/35; open dialogs/sheets at night. | Matching splash/system bars/window/dialog surfaces without white flashes. |

## G — Shake

Automated references: `ShakeDetectorTest` synthetic traces, `TimerSensorControllerTest` lifecycle/failure, engine overlap tests. Device status for G01–G06: **PENDING**. Recorded real-device trace tuning is still required; synthetic traces do not prove rejection of rolling over in bed.

| ID | Exact procedure | Expected result |
|---|---|---|
| G01 | Install on a no-accelerometer emulator/device. | Shake setting/card hidden; no sensor registration or shake lease. |
| G02 | Record real traces locally while rolling in bed, walking, putting phone down and intentional alternating shakes. | Ordinary movement rejected; ≥3 alternating peaks within the detector window accepted. Keep recordings local and remove personal context. |
| G03 | Shake twice within 3 s, after cooldown, during fade, and after Stop. | One extension inside cooldown; later valid extension accepted; fade safely cancelled/recovered; stopped run ignored. |
| G04 | On wake-up accelerometer hardware, run screen off before and inside final five minutes. | Foreground sensor delivers accepted shakes throughout; no ordinary-sensor wake lease. |
| G05 | On ordinary sensor hardware, repeat screen on/off before and inside final five minutes; include a >15 min timer under natural Doze. | Screen-on delivery; documented screen-off limitation before final window; bounded wake coverage in the final window, released after cancellation/finish. |
| G06 | Disable Shake during a run; test registration failure, service teardown and missing sensor service on an emulator. | Listener/lease released, card reflects actual monitoring; no false “screen-off shake works” claim. |

## H — Access and system settings

Automated references: alarm/intent/notification adapter tests, tile membership tests and Settings access presentation. Device status for H01–H07: **PENDING**.

| ID | Exact procedure | Expected result |
|---|---|---|
| H01 | Deny notification permission, disable Timer running channel, then re-enable each. Start normally each time. | Start is never blocked by permission prompt; FGS/system behavior recorded; readiness reflects missing visibility. Channel Alerts behaves independently. |
| H02 | Decline exact-alarm rationale/access, run timer, then grant access and return. | Visible warning and bounded full-run wake fallback; grant reconciles alarms. Nightly requires access and says so. |
| H03 | Revoke exact access mid-run using Settings or the command below; reopen. | Account for OS process termination and alarm cancellation. Reconcile on reopening, recover/degrade explicitly; never promise continuity across OS-killed/revoked execution. |
| H04 | Revoke optional notification access before fade/stop and while remote playback is active. | Fail-closed observability/volume handling; local key fallback only when eligible, honest remote limitation. |
| H05 | On API 36.1/37 disable promoted notifications; repeat with promotion allowed. | Same timer and ordinary fallback; promotion is system chosen, not assumed from request. |
| H06 | Battery Restricted/Optimized/Unrestricted and OEM autostart/sleeping-app settings; record and restore each. | Correct guidance/status and measured actual result, no unsupported permission request. |
| H07 | Tap Share diagnostics, cancel chooser, then explicitly share to a chosen local target; repeat after process restart. | No send before target selection; bounded text with event codes/relative times only; process-local history accurately described. No network/storage permission. |

Before H03 record the original mode:

```sh
adb shell cmd appops get "$TIMER_PKG" SCHEDULE_EXACT_ALARM
adb shell cmd appops set "$TIMER_PKG" SCHEDULE_EXACT_ALARM deny
# Restore the recorded mode, e.g. allow or default, then explicitly reopen:
adb shell cmd appops set "$TIMER_PKG" SCHEDULE_EXACT_ALARM default
adb shell am start -W -n "$TIMER_PKG/com.sujal.sleeptimer.MainActivity"
```

On an API 37 test emulator, also run `adb shell cmd audio set-enable-hardening throw`, then exercise Activity, shortcut, widget, notification, tile, Nightly and sticky/alarm starts. Check fade readback, focus failure and media-session/PAUSE fallback without WIU. Restore the emulator's original hardening setting afterward; command availability/default must be checked using that image's `cmd audio help`. This case is **PENDING API 37**, not covered by compiling the API 37 branch.

## I — Synchronized Android surfaces

Automated references: notification dismissal/configuration tests, `WidgetPresentationTest`, preview/manifest/input tests, actor idle-only start and replacement Undo tests. Device status for I01–I06: **PENDING**. See [PHASE4_TESTING.md](PHASE4_TESTING.md) for pin/add instructions and API promotion differences.

| ID | Exact procedure | Expected result |
|---|---|---|
| I01 | Pin both widgets/add tile, then start/extend/reduce/shake/cancel/expire from each supported entry point. Video Home, notification, tile and widgets together. | Same run/phase/deadline and preset selection within 1 s after a state change; native countdown seconds advance without per-second app pushes. Measure launcher/Glance latency. |
| I02 | Repeat after actual process death/restart, locale/time/zone/theme/dynamic-color changes and screen-off/on. | Fresh initialized engine wins over stale widget process/boot state; time changes never alter elapsed deadline. |
| I03 | On a spare install pin widgets/add tile; clear app data, reopen and inspect each surface. | Defaults/idle, no old timer resurrected; tile/widgets refresh. Re-pin only if launcher removes them. |
| I04 | Dismiss running notification; wait minute tick, recreate controller/process, then adjust current run; deliver stale dismissal in instrumentation. | No timer cancellation; dismissal respected until meaningful change; stale dismissal cannot hide newer revision. |
| I05 | Long-press launcher icon: fixed starts/Custom; replace running 30 min with 60 min and tap Undo; repeat expired/adjusted/old actions. | Correct package variant and real sheet; valid original future deadline restored under a new ID only; no playback start. Inspect adaptive/monochrome icon masks. |
| I06 | Tap active tile to cancel and inactive to start, including rapid taps and lock screen; long-press opens app. Resize widgets and inspect generated/static picker previews. | No unlock required by supported tile path; no stale inactive replacement; correct Added state, complete accessible labels and usable preview fallback. API 37 trampoline/WIU separately measured. |

## J — Nightly schedule

Automated references: `NightlyScheduleTest`, engine Nightly deduplication and `NightlyController` occurrence validation. Device status for J01–J05: **PENDING**.

| ID | Exact procedure | Expected result |
|---|---|---|
| J01 | Schedule two minutes ahead while a manual run is active. | Existing run retained; next occurrence scheduled without replacement. |
| J02 | Power off before occurrence, boot after it. | Missed run skipped; only next future local occurrence scheduled. |
| J03 | Allow scheduled start then Stop; reopen/reconcile repeatedly that night and replay the occurrence in a controlled host test. | Tonight's stopped occurrence never restarts, including after command-history rotation. |
| J04 | Change time/zone/repeat days/duration, reboot and update app; include DST gap/overlap. | Correct next future occurrence with current zone/repeat days, one delivery identity; no stale alarm start. |
| J05 | Revoke exact access before occurrence and leave notification access off, then grant access. | Clear exact requirement; Nightly recommendation for session access; no WIU assumption or unsafe volume/focus; no invented missed-run replay. |

## K — Devices and OEM battery behavior

Automated reference: OEM guide mapping and graceful no-sensor/no-vibrator adapter tests. Physical status: **PENDING for every row**. A Pixel/Motorola pass does not validate another vendor.

| Device group | Procedure and expected result |
|---|---|
| Samsung | Verify Unrestricted/background usage and sleeping/deep-sleeping lists; follow detected guide; repeat core, 3 h and overnight tests. |
| Xiaomi / Redmi / POCO | Verify autostart, background battery policy and recent-app lock where offered; guide opens correct vendor page; repeat long tests. |
| OnePlus | Verify battery optimization/background activity/autostart settings available on installed OxygenOS; repeat long tests. |
| Oppo / Realme | Verify startup/background activity and battery policies; repeat long tests. |
| Vivo | Verify autostart/high-background-power permissions where offered; repeat long tests. |
| Huawei / Honor | Verify app launch/manual management/background operation; repeat long tests. |
| Motorola | Verify background battery policy and unrestricted state; repeat long tests on owner's Android 15 phone. |
| Unknown OEM / Android Go / low RAM | Generic safe settings link, no crash for missing vendor page/settings; stress memory and background restrictions. |
| No vibrator / no amplitude control | Warning selection/preview must not crash; soft warning uses documented fallback; no audio introduced. |

Record exact firmware/version and which user settings were changed; restore original settings after testing. Device-specific settings vary, so the app opens a browser guide rather than relying on undocumented vendor component names.

## L — Adaptive UI, localization and input

Automated references: `HomeUiTest`, `SettingsUiTest`, dial drag/semantics tests and native screenshot regressions. Physical status for L01–L10: **PENDING**.

| ID | Exact procedure | Expected result |
|---|---|---|
| L01 | Rotate/fold/unfold mid-drag, including tabletop and a separating vertical hinge. | No illegal jump/wrap or crash; bounded value; dial and controls avoid hinge/occlusion and remain reachable in their panes. |
| L02 | Resize multi-window/desktop through 320, 360, 390, 600 and 840 dp; portrait/compact landscape; no orientation lock. | No clipped controls; centered bounded content; landscape two panes, tabletop dial above/controls below. |
| L03 | Repeat idle/running/finished, Settings and every sheet at font 1.0/1.3/2.0 and largest display size in both themes. | Text wraps, dial H:MM:SS fits, touch targets ≥48 dp, all actions reachable by scroll; no overlap or truncation. |
| L04 | Switch en-XA and ar-XB debug pseudo-locales, RTL and a locale with non-Latin digits; repeat L02/L03 plus 12/24-hour changes. | Resource strings/digits/plurals/time localized, chevrons and group corners mirror; no hardcoded sentence fragments. Record missing translations separately. |
| L05 | Open Custom/Nightly/readiness/pickers, background and actually kill the process, reopen; rotate while open. | Saveable sheet state/duration survives where designed; no duplicate Start command or replayed stale result. |
| L06 | Predictive back on Home/Settings and each modal sheet; repeat gesture and three-button navigation, cutout and IME open. | Correct destination/sheet dismissal and insets, no controls under system bars/keyboard. |
| L07 | Keyboard/D-pad: focus dial, arrows ±1, Page Up/Down ±5, Home/End limits; use TalkBack range/custom actions ±1/±5. | Same bounded whole-minute values, no wrap below 1/above 60; Start/Stop/presets reachable and correctly announced. |
| L08 | TalkBack and Switch Access through entire UI; listen through countdown/start/extend/warning/finish. | Visual traversal order/headings, complete labels, no second-by-second announcements; events announced once, no invisible duplicate transition controls. |
| L09 | Enable Remove animations and disable system touch haptics; repeat dial, start/stop and dialogs. | Flat/nonmoving wave and immediate transitions under system motion policy; haptics respect settings; no flashing. |
| L10 | Light/dark dynamic color with several low-contrast wallpaper palettes; inspect text contrast, widgets and icon masks. | Text ≥4.5:1 and readable system-role colors; no brand hex/custom font/gradient; monochrome icon works. |

The full rendering matrix is 5 widths × 3 font scales × 2 themes × LTR/RTL/pseudo-locales for each screen/state/sheet. Host snapshots cover the recorded subset in PROGRESS; remaining combinations must be recorded here, not inferred from a single narrow/RTL image.

## Release handoff

Phase 6 host release preparation began November 13. Carry every pending/failed case forward with evidence. Use [RELEASE_TESTING.md](RELEASE_TESTING.md) for optimized artifacts, real profile collection, startup/frame measurements, 16 KB and signed-candidate smoke; use [PLAY_SUBMISSION.md](PLAY_SUBMISSION.md) for owner inputs and policy preparation. An implementation and a complete test plan do not establish the unmeasured one-second, overnight, OEM, shake-trace or hearing-safety gates.
