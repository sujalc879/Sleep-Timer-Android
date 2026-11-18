# Phase 6 cancellation notifications

November 19, 2026 (Asia/Kolkata). Working changes on `8cc6dc5`, left uncommitted for review.

Manual Stop cancels the timer and leaves playback running. The engine durably marks this transition as STOPPING plus CANCELLED before cleanup returns to Idle. When no fade record exists, the notification controller now retains the ongoing notification until the service removes it, avoiding an unnecessary replacement titled Stopping playback. Cancellation with fade recovery still posts a status update, now labeled Canceling timer; Home uses the same distinction. Natural expiry continues to show Stopping playback.

The shared cancellation predicate moved from core/timer to a pure model extension with identical logic. The notification presentation key includes cancellation so a Stop received during the existing STOPPING pipeline can also update recovery status. No serialized field/schema, timer command, alarm, audio pipeline or foreground-service lifetime changed. Posting and service removal remain synchronous; no asynchronous notification worker was added.

## Host verification

Full `verifyRelease :app:assembleBenchmarkRelease` passes: **223 host tests**, zero failures/errors/skips, **15 unchanged image comparisons** (11 Roborazzi images and four widget preview comparisons), lint, Spotless/ktlint, detekt, build-logic and nine artifact-verifier tests. New regressions verify no-fade cancellation, visible fade recovery, natural-expiry updates, foreground-promotion copy on API 26/35 and cancellation/expiry Home text. Existing engine recovery/cancellation tests also pass.

The first combined format/check pass reported two overlong test lines before formatting completed; formatting corrected them and the full rerun passed. After the same-phase presentation-key correction, lint's Kotlin FIR analyzer crashed on unchanged DiagnosticSharingTest.kt; an identical full rerun passed in one second, including that analysis task. Both failures are retained. No baseline or analyzer threshold changed. Optimized unsigned APK: **3,143,435 bytes**; AAB: **6,625,763 bytes**. Binary alignment/permission/dependency checks pass; these sizes are not Play download measurements.

## Physical comparison procedure

Use the unchanged FrameBenchmarks#countdownAndWave method, five iterations per source and required-profile partial compilation on the Moto g54 5G, Android 15/API 35. Baseline app source is `3149031`; candidate is the working change above. Preserve APKs, source patch, hashes, raw metrics and traces. Verify installed target hashes before measurement. Install only isolated benchmark packages with the already verified SDK non-streaming fallback, preserving the owner's normal app/settings/timer. No extra wake-key inputs during measured intervals, profile regeneration, benchmark suppression or global power/animation setting changes.

The whole-control-phase binder query covers eight seconds through measureBlock end. Framework transaction code 0x40 is interpreted as intent lookup and 0x07 as notification posting against the inspected Android 15 framework; those codes are platform specific. FrameTimingMetric samples and FrameTimeline records have different denominators. Buffer Stuffing and positive overrun samples are not automatically dropped frames. Trace import warnings remain with the evidence; no power measurement is claimed.

Ignored evidence root: `build/reports/release/cancel-notification-2026-11-19/`. The before/after APK archives, source metadata, candidate patch, host logs/results and preserved comparison/analysis scripts record this task. Final release performance, real-player/Doze, fade/routes, overnight/OEM, Android 17/WIU, actual 16 KB runtime, identity and signing gates remain open.

## Physical results

Successful pair: `comparison-e487b906ed`. Both manual instrumentation executions report **OK (1 test)**, five iterations each. Before/after installed target checksums match the preserved APKs; the unchanged runner checksum is also verified. Both runs were USB powered, thermal status 0, battery temperature 36 → 37 °C, with charge 68 → 69% before and 69 → 70% after. Benchmark thermal-throttle sleep was zero for both.

| Measured result | Before | After |
|---|---:|---:|
| FrameTimeline records in measureBlock | 5,221 | 5,215 |
| Dropped Frame records | 10 | 12 |
| App Deadline Missed records | 0 | 1 |
| Buffer Stuffing records | 5,011 | 4,956 |
| Raw frame CPU P50/P99, ms | 13.08/21.66 | 14.59/23.81 |
| Raw frame overrun P50/P99, ms | 3.28/18.69 | 9.77/19.17 |
| Raw maximum overrun, ms | 27.97 | 36.74 |

Drops per iteration are 2/4/0/3/1 before and 4/2/2/1/3 after. Raw FrameTimingMetric samples total 5,330 before and 8,853 after (4,223 / 6,350 positive overruns). The candidate's first raw metric run contains 4,600 samples; its other runs have 1,059–1,068. Keep these raw values and the separate measured-window classifications; do not treat the sample-count difference as extra dropped frames or discard the large first run. The aggregate frame results do **not** show an improvement, and this one sequential pair does not establish causality. The frame budget remains open.

| Synchronous main-thread binder work across five control phases | Before | After |
|---|---:|---:|
| All calls | 60 | 50 |
| All wall time, ms | 127.02 | 100.85 |
| Intent lookup calls (0x40) | 15 | 10 |
| Intent lookup wall time, ms | 9.77 | 7.51 |
| Notification posts (0x07) | 15 | 10 |
| Notification post wall time, ms | 77.35 | 49.44 |

Every iteration has three → two notification posts and three → two dismissal-token lookups: the transient cancellation refresh is removed. Observed post wall time is lower, while the candidate's remaining post maximum is 9.36 ms. This verifies the targeted work reduction; it does not satisfy the frame budget or attribute every stall to notifications. Foreground promotion/removal calls still occur as required.

The offline analysis reads the original on-device trace files after validating each SHA-256 against the pulled host evidence, avoiding a duplicate USB transfer. The source patch, production-file hashes, full raw metrics/traces, per-trace CSVs/import warnings, analysis summary and executed scripts are retained under the successful pair. Failed attempts below are excluded from these results.

## Failed first execution

`comparison-b31b89bedd` failed on the baseline's final iteration because the + 10 min node was missing; instrumentation reports one failure. All five traces, the failure log and app exit/screen diagnostics are retained. It is not passing timing evidence. Post-failure diagnostics show an awake, unlocked phone and only the harness's expected force-stop exit reasons, which do not establish why the control was missing. The first 60-second trace transfer timed out; a subsequent transfer retained all five files. No phone setting or benchmark harness change was made for the retry; host-only logging now streams iteration output.

`comparison-af555eee78` failed at idle setup. Inspection confirmed that the first failed journey's force-stop cleanup had left its active timer persisted in the isolated app. Opened that package, canceled its timer using the observed Stop control, and confirmed idle Start/60 min controls. The owner's normal timer/settings were untouched. A later retry, `comparison-53e4899d46`, failed before any iteration because the Android CLI inspection instrumentation still owned UiAutomation. Stopped only the identified com.android.cli.interact.instrumentation session and verified no active instrumentation before the next pair. No accessibility setting was changed. Keep inspection instrumentation closed throughout measured benchmarks; improve failed-journey persisted-timer cleanup in a separate harness change.

## Delivery and remaining work

The normal debug app was updated through the actual scripts/run-phone.sh helper using non-streaming replacement, preserving app data. Its installed SHA-256 matches the built APK. The final UI hierarchy confirms idle Home with readiness, 10 s test, presets, Custom, Start, Nightly and Settings controls. No normal timer or playback test was started in this task.

Verified retained trace/APK hashes before cleanup. Removed the four owned benchmark output roots and three trace-analysis paths, checked their absence, and uninstalled only the two isolated benchmark packages after verifying their installed hashes. The owner's normal app and existing test player remain. Final audit: no normal/benchmark TimerService, no test-player service, active wake-lock list size zero. The CLI inspection session was closed after final UI inspection. No global power/animation/accessibility or shell-profile setting changed; no SDK/JDK/emulator/global tool setup was needed.

Next: harden failed-journey cleanup and inspection ownership, investigate the first raw metric sample-list discrepancy and remaining control stalls, then refresh profiles and repeat the full performance suite. Real-player/Doze, volume/routes, overnight/OEM, Android 17/WIU, actual 16 KB runtime, production identity and upload signing remain open. This is a verified development update; Phase 6 is not complete.

## Manual phone check

With USB debugging authorized, rebuild/update/open the normal app with:

```bash
cd /Users/sujalsunilchaudhary/coding/projects/sleepTimer
bash ./scripts/run-phone.sh
```

Play audio, start a timer, then tap Stop: the timer should cancel and audio should continue. Cancellation without a fade should remove the ongoing notification without a Stopping playback replacement. During fade recovery, cancellation uses Canceling timer. Separately, use the 10 s test while audio is playing to check that expiry still pauses the player. This task's benchmark timers are canceled well before expiry and do not replace the remaining real-player/fade/Doze checks.
