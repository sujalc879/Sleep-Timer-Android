# Phase 6 notification intent reuse

November 17, 2026 (Asia/Kolkata). Production change `3149031`; diagnostic queries `cfb6734`.

**Physical follow-up complete:** five iterations per build confirm 60 → 15 intent lookup transactions. The comparison recorded 17 → 12 dropped frames; overall main-thread binder wall time was essentially unchanged. The normal app is updated and open, and temporary resources are removed. The frame budget remains open.

The running notification now reuses its app-opening PendingIntent and one bounded pair of Extend/Stop tokens keyed by run ID and Extend by. A settings or run change rebuilds the pair. The revision-bearing dismissal token and visible notification content still rebuild normally. Explicit immutable intents, URI identities, flags, per-delivery command IDs and immediate foreground promotion are unchanged. Posting remains synchronous, preserving lifecycle ordering.

## Initial source and trace audit

Inspected retained `FrameBenchmarks_countdownAndWave_iter002_2026-11-16-18-37-55.perfetto-trace`, SHA-256 `23bd72878ffd31e8a3100a8d2c1ca45e65124a2b98bb1283ac9f0d5e30764802`, from the preceding formatter build. The main-thread window 8.00–8.45 s covers extension/reduction/cancellation after eight seconds of wave observation.

| Synchronous main-thread binder calls | Count | Total wall time, ms | Maximum, ms |
|---|---:|---:|---:|
| All calls in the inspected window | 21 | 33.48 | 6.95 |
| Code 0x40 | 12 | 12.04 | 5.69 |
| Code 0x07 | 3 | 13.86 | 6.95 |

The cached Android 15 framework maps 0x40 to getIntentSenderWithFeature and 0x07 to enqueueNotificationWithTag. Four token lookups followed by one post agree with the running builder's content/delete/extend/stop factories. This is a source-supported interpretation of this trace, not a stable mapping for every platform/OEM. Repeated steady-run refreshes can now reuse three of those four tokens; the dismissal lookup and notification post remain.

The same window includes composition up to 9.77 ms, measure/layout up to 9.83 ms, render work and buffer waits. Nested slice totals overlap and are not exclusive CPU costs. Token reuse does not establish the cause of every drop or prove that remaining posting/rendering costs meet the frame budget. The earlier full frame results remain in [PHASE6_COUNTDOWN_FORMATTING.md](PHASE6_COUNTDOWN_FORMATTING.md).

The read-only queries are `tools/performance/frame-control-binder-calls.sql` and `frame-control-thread-cost.sql`. They require one measureBlock and examine the fixed 8.00–8.45 s control window of the current journey. Interpret the raw binder arguments against the tested platform; do not treat an absent/mismatched window as a zero-cost result.

## Physical comparison

The preserved baseline is checkout `f940ca8` (app source `03887c5`); candidate is `3149031`. Both use the same unchanged FrameBenchmarks#countdownAndWave method, five iterations and required-profile partial compilation. Both manual instrumentation executions report **OK (1 test)**. Installed baseline/candidate hashes were verified before measurement; runner and normal-app hashes were also checked. No benchmark suppression, profile regeneration, refresh-rate, animation or global power-setting change was made.

Moto g54 5G, Android 15/API 35, Benchmark 1.5.0. Both runs were USB powered, thermal status 0, with zero benchmark thermal-throttle sleep. Baseline started at 88% / 36 °C and ended at 89% / 37 °C; candidate remained 89% / 37 °C. These charging-device diagnostics do not establish final battery-powered performance budgets.

| Measured-window FrameTimeline, five iterations each | Before | After |
|---|---:|---:|
| Records | 5,220 | 5,237 |
| Dropped Frame | 17 | 12 |
| App Deadline Missed | 1 | 1 |
| Contains Buffer Stuffing | 4,974 | 4,994 |
| Dropped frames per iteration | 3 / 4 / 2 / 6 / 2 | 2 / 1 / 4 / 1 / 4 |

Drops occurred 8.05–8.31 s before and 8.12–8.46 s after, around the controls. The baseline app-deadline miss was at 0.23 s; the candidate miss was at 8.47 s. All ten measured windows were classified; no empty/missing result was counted as a pass.

| Raw FrameTimingMetric | Before | After |
|---|---:|---:|
| Samples | 5,321 | 5,616 |
| CPU duration P50 / P99, ms | 12.64 / 22.33 | 12.19 / 18.80 |
| CPU duration maximum, ms | 38.79 | 29.79 |
| Overrun P50 / P99, ms | 2.48 / 19.33 | 2.10 / 14.05 |
| Overrun maximum, ms | 33.67 | 25.27 |
| Positive overruns | 3,707 | 3,830 |

Raw metric samples and measured-window FrameTimeline records use different matching rules; their counts are not interchangeable. The lower percentiles/drop total in this sequential pair do not establish causality across devices/settings or satisfy the zero-drop target.

The new binder query covers the **entire control phase**, from eight seconds to measureBlock end, rather than the initial audit's fixed 450 ms window. It counts synchronous calls on the target's main thread:

| Main-thread binder work across five control phases | Before | After |
|---|---:|---:|
| All synchronous calls | 107 | 60 |
| All synchronous wall time, ms | 116.58 | 116.62 |
| Intent lookup calls (0x40) | 60 | 15 |
| Intent lookup wall time, ms | 32.76 | 14.89 |
| Notification posts (0x07) | 15 | 15 |
| Notification post wall time, ms | 47.44 | 63.72 |

Every iteration has 12 → 3 lookup calls while retaining three posts: **75% fewer lookups**. Other call-count differences are not attributed to the cache. Remaining dismissal/post/lifecycle waits still matter: candidate lookup maximum was 8.63 ms and post maximum 8.05 ms. Overall synchronous wall time is effectively flat. This confirms the targeted repeated lookups were removed, not a general main-thread or frame-budget pass.

## Verification and phone state

- Host `verifyRelease :app:assembleBenchmarkRelease` passes: **218 tests**, zero failures/errors/skips, **15 unchanged image comparisons**, lint, Spotless/ktlint, detekt, build-logic, nine artifact-parser tests and optimized artifact audit. API 26/35 regressions cover changed extension amounts, retained old/replacement-run action identities and fresh dismissal revisions.
- Initial full check: 1 min 8 s, 1,440 tasks (100 executed / 3 from cache / 1,337 up-to-date). Final ordinary-cache formatAll/verifyRelease passes in 2 s (1,431 tasks, 24 executed / 1,407 up-to-date). Shell syntax and the actual updated phone helper build/install/open path also pass. No baseline or analyzer threshold was relaxed.
- Recorded unsigned APK: **3,143,023 bytes**; AAB: **6,625,472 bytes**. These are artifact sizes, not Play download measurements.
- The normal app was installed using non-streaming replacement, with data preserved and its installed hash matching the verified debug APK. No normal timer/test was started by this follow-up. The final Home layout has readiness/test controls and Start; the updated app is open.
- The three owned controls-analysis files and the unique benchmark output directory were removed and absence checked. The abandoned installer session 47782822 was successfully abandoned. Both isolated benchmark/runner packages were removed after preserving outputs and verifying hashes; the owner's normal app and existing test player remain.
- Final audit: no normal/benchmark TimerService, no test-player service, and active wake-lock list size zero. ADB is back on its original NATIVE backend. No SDK/JDK/emulator/global tool or shell-profile change occurred.

## Connection recovery and retained evidence

Earlier attempts failed before measurement: first no connected devices, then a CLI runner install lost ADB at install-commit after a 48.81 MiB transfer. Native USB read errors were recorded while macOS still saw the phone. A session-only LIBUSB retry also failed and was reverted. After USB debugging was restarted, standard non-streaming SDK installs succeeded for both the normal app and runner. This sequence does not isolate the root cause of the prior transport failure.

The existing SDK's `adb install --no-streaming -r` separates file transfer from Package Manager commit. Temporary wake-key inputs were used only during installation/offline analysis; no extra inputs were injected into measured intervals, and no global screen-timeout/power setting changed. See the [ADB command reference](https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/docs/user/adb.1.md) and [instrumentation/output guide](https://developer.android.com/topic/performance/benchmarking/benchmarking-in-ci).

Ignored evidence root: `build/reports/release/control-frames-2026-11-17/`. Successful comparison: `comparison-583f90b38e/`, selected by `latest-comparison.json`. It retains both instrumentation logs/commands, conditions, ten traces with hashes, raw metric JSON, per-trace classifications/events/binder CSVs/import warnings, the complete analysis summary, installed APK hashes and cleanup audits. The normal-app update log/hash and filtered final Home layout are retained. The failed `comparison-fca6f671e1/` and earlier no-device/native/backend logs remain; they are not timing evidence.

Before/after APKs and their unchanged runner remain under `before/` and `after/`; `run-preserved-comparison-nonstreaming.py` and `analyze-preserved-comparison.py` retain the executed procedures. The whole-control-phase read-only query is `tools/performance/frame-control-binder-phase.sql`; fixed-window queries remain useful for the original audit. Power-rail import warnings are preserved; no power measurement is claimed.

Next: investigate remaining notification-post/service-teardown waits and text/layout work around the controls, then dial/presentation/dimming behavior. Refresh final profiles and repeat the full suite under controlled conditions after changes. Real-player/Doze, volume/routes, overnight/OEM, API 37/16 KB, identity/signing and other release gates remain open. Phase 6 is not complete.
