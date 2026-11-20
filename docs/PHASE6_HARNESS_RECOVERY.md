# Phase 6 harness recovery and frame capture audit

November 19, 2026 (Asia/Kolkata). Working instrumentation changes on `71d5ec5`; app feature code and existing profiles are unchanged. Changes remain uncommitted for review.

## Fixture correction

The shared fixture now brings the isolated activity forward, waits for enabled Home controls, cancels any restored timer and verifies idle. Final cleanup runs that recovery before force-stopping the process; force-stop still runs in finally if recovery fails. Recovery also returns from Settings. Construction requires the exact isolated application ID before permission, UI or process actions are available.

Frame, startup and baseline-profile journeys use this recovery before/after their work. Frame setup additionally cancels a restored run after launching the activity. The eight-second wave, extend/reduce/cancel interactions and five-iteration count are unchanged. No app data is cleared, no normal-package commands are sent, and no profiling source is regenerated.

## Verification

Full `verifyRelease :app:assembleBenchmarkRelease` passes in nine seconds: **223 host tests**, zero failures/errors/skips, **15 unchanged image comparisons**, lint, Spotless/ktlint, detekt, build-logic, nine artifact-verifier tests and optimized APK/AAB audit. Both instrumentation runners compile.

The Moto g54 5G / Android 15 regression run reports **OK (4 tests)** in 26.921 seconds:

| Regression | Result |
|---|---|
| Force-stop a running timer, recover, then cold-launch again and verify idle persists | PASS |
| Leave a timer behind the launcher, clean up twice, reopen and verify idle | PASS |
| Recover from Settings to idle Home | PASS |
| Reject the normal app package before fixture actions | PASS |

These tests use the real persisted timer and activity lifecycle. They collect no performance metrics. Evidence is under `build/reports/release/harness-recovery-2026-11-19/validation-fc64109d6d/`, including exact commands, working patch, source/APK hashes and device conditions.

The initial performance attempt stopped before measurement with Benchmark's LOW-BATTERY error: **12%**, minimum **25%**. No error suppression or simulated battery state was used. The owner paused to charge. The first reconnection attempt exposed repeated native USB read failures (e00002ed); one server refresh and a session-only LIBUSB attempt did not restore a transport, and the original default NATIVE configuration was restored. After debugging reconnection, the authorized phone returned at **32%**. The benchmark-only retry completed below. The four regression results were reused after verifying unchanged source and APK hashes; they were not rerun.

## Oversized first raw sample list

Audited the retained candidate iteration 000 from [the cancellation comparison](PHASE6_CANCELLATION_NOTIFICATIONS.md). Its SHA-256 is recorded in audited-trace.json. The pinned [Benchmark 1.5.0 source artifact](https://dl.google.com/dl/android/maven2/androidx/benchmark/benchmark-macro/1.5.0/benchmark-macro-1.5.0-sources.jar), SHA-256 `2e2ca0ab1534b8a69bffb94cf404a874a2f8976b1d54f3ea3e7e9b1ed6b4f627`, shows that FrameTimingMetric calls FrameTimingQuery.getFrameData over the capture without a measureBlock time restriction. Its matching pass combines UI, render, expected and actual frame slices.

The new read-only `tools/performance/frame-capture-window.sql` requires exactly one measureBlock and groups slices by their start time. Running it with the existing pinned trace processor produces:

| Frame records | Before measurement | In measurement | After measurement |
|---|---:|---:|---:|
| Actual FrameTimeline | 3,577 | 1,045 | 0 |
| Expected FrameTimeline | 3,578 | 1,044 | 0 |
| RenderThread DrawFrame | 3,564 | 1,042 | 3 |
| Main-thread doFrame | 3,565 | 1,041 | 3 |

The capture starts **30.149 s before** measureBlock. The measured interaction lasts **8.907 s**, while the capture spans **39.103 s**. Slice starts determine the regions; some slices cross a boundary.

The first raw metric list's 4,600 matched samples therefore has a large pre-measure frame population available to it. This explains its larger denominator without a longer measured interaction. Capture-boundary behavior still needs a tooling investigation. Raw FrameTimingMetric samples and the measured-window FrameTimeline classifications remain separate; their counts differ because they use different matching rules. All original raw values and traces are retained. This audit does not close the frame budget.

The offline query used task-owned files under /data/local/tmp and the processor already shipped in Benchmark. No SDK/JDK/emulator/global tool installation was required. Query CSV, stderr/import warnings and source copies remain in the ignored evidence root. No power measurement is claimed.

## Completed benchmark retry

Successful evidence: `validation-6874826c2c`. Instrumentation reports **OK (1 test)** in **125.832 s**, completing five iterations of the unchanged countdown/wave/extend/reduce/cancel interaction. Required-profile partial compilation remains enabled. Installed target/runner hashes match the saved APKs and working source patch. This is validation of the recovered fixture, not a production optimization comparison.

The Moto / API 35 was USB powered, battery **32% before and after**, temperature **36 → 37 °C**, thermal status **0**. Benchmark thermal-throttle sleep is **0 s**. Inspection instrumentation was closed during measurement. No profile regeneration, benchmark suppression or global power/animation/accessibility setting change was made.

| Result | Observed |
|---|---:|
| FrameTimeline records in measureBlock | 5,172 |
| Dropped Frame classifications | 14 |
| App Deadline Missed flags | 3 |
| Buffer Stuffing records | 4,951 |
| Raw frame CPU P50/P99, ms | 15.98/24.88 |
| Raw frame overrun P50/P99, ms | 10.07/19.86 |
| Raw maximum overrun, ms | 26.98 |
| Raw frame samples / positive overruns | 5,268 / 4,536 |

Drops per iteration are **2/4/3/2/3**. All drop classifications occur at **8.099–8.362 s**, during the controls after the eight-second wave. The three app-deadline flags are in iteration 000 at **8.316–8.345 s**; two have incomplete duration fields, which are retained without using them as latency estimates. The frame budget remains open.

Raw sample counts are **1,052/1,067/1,054/1,048/1,047**. Window audits show **23–26 pre-window render slices** per trace and measured durations **8.890–9.048 s**. The earlier large target-frame population is absent in this run. Trace bounds and actual target-frame populations are both retained; raw metrics and measured-window classifications keep their separate matching rules.

The control query records two source-mapped notification-post transactions and two dismissal lookups in every iteration, consistent with the preceding cancellation change. Totals: **49 synchronous main-thread binder calls / 98.58 ms**; notification posts **10 / 50.53 ms**, maximum **7.42 ms**. Framework transaction-code interpretation remains specific to the inspected Android 15 implementation. This does not establish a frame improvement.

## Cleanup and delivery

Cold-launched the isolated app after the benchmark and verified Start/Settings with no Stop control: cleanup persisted idle across process restart. After retaining/checking trace and installed APK hashes, removed both task-owned output roots and all three temporary analysis files, checked their absence and uninstalled only the isolated target/runner. Failed-attempt outputs/logs remain on the host.

The normal app's installed APK already matched the verified debug build, so it was reopened without replacement or data clearing. Its final UI hierarchy confirms idle Home. The owner's normal app and test player remain installed. Final audit: no normal TimerService, no test-player service, active wake-lock list size **0**, default **NATIVE** backend. The final inspection session was closed. No normal timer or playback test was started by this task. Working edits remain uncommitted.

## Next work

Investigate the remaining control-phase waits/layout/render work using the retained event/binder traces, then refresh final profiles and repeat the full performance suite. Capture-boundary behavior remains a tooling investigation. Production performance, real-player/Doze, volume/routes, overnight/OEM, Android 17/WIU, actual 16 KB runtime, identity and upload-signing gates remain open. Phase 6 is not complete.

To rebuild/update/open the normal app with authorized USB debugging:

```bash
cd /Users/sujalsunilchaudhary/coding/projects/sleepTimer
bash ./scripts/run-phone.sh
```
