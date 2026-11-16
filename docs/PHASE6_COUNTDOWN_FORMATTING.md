# Phase 6 countdown formatting comparison

November 17, 2026 (Asia/Kolkata; trace filenames use November 16 UTC).

The dial now owns locale-specific number formatters instead of constructing them on every update. The hours formatter initializes only for hour durations. Numeric text is remembered by rounded seconds; a locale change replaces the formatter. Rounding, localized digits, padding, layout and the Material 3 wave remain unchanged. End-time formatting retains its existing timezone and 12/24-hour handling. This removes repeated construction by source inspection; allocated bytes and a frame-time improvement have not been established.

Production change: `03887c5`. Separate test-runtime update: `215a5f0`, Robolectric 4.18. Baseline checkout `5c8928e` contains the preceding app source `5cb3ae5` plus diagnostics/docs and the owner's phone commands; both runs used the unchanged `147188c` interaction harness and existing required app profile. The Robolectric update affects host tests only.

## Verification

- Complete host/release verification passes: **214 tests**, zero failures/errors/skips, **15 image comparisons** against unchanged baselines, lint, Spotless/ktlint, detekt, build-logic and nine artifact-parser tests. Regression coverage includes repeated hour/minute transitions with localized digits and US → Arabic → US composition changes.
- Both physical benchmark executions pass: one method, five iterations each; before 2 min 28 s, after 2 min 18 s. This is runner duration, not startup or interaction latency.
- Latest optimized unsigned production APK: **3,143,023 bytes**; AAB: **6,625,013 bytes**. Artifact/native/permission audit passes. These are artifact sizes, not Play download measurements.

The first verification correctly rejected the newly outdated Robolectric 4.17 pin. Maven Central metadata/POM confirmed stable 4.18. The next run passed tests but reused the old catalog finding in generated lint analysis; restoring that analysis from Gradle cache repeated the stale result. Removing only `app/build/intermediates/lint_partial_results/debug/lintAnalyzeDebug` and recomputing with `--no-build-cache` passed. Failed logs are retained; no lint baseline, suppression, image baseline or global analyzer setting changed.

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew verifyRelease :app:assembleBenchmarkRelease --no-build-cache --continue --console=plain
ANDROID_SERIAL=YOUR_DEVICE_SERIAL JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.sujal.sleeptimer.macrobenchmark.FrameBenchmarks#countdownAndWave' --console=plain
```

## Physical comparison

Moto g54 5G, Android 15/API 35, Benchmark 1.5.0; isolated benchmark package, required-profile partial compilation, five iterations. The journey observes eight seconds of running countdown/wave, then extends, reduces and cancels. No refresh-rate, animation, brightness, permission or setting change was made to the normal app. No timer was allowed to expire.

Both runs were charging at 36 °C battery temperature with thermal status 0 and zero benchmark thermal-throttle sleep. Battery was 86% before baseline, 93% before candidate and 94% after candidate. These are diagnostic charging-device samples, not final battery-powered budget evidence.

| Raw FrameTimingMetric | Before | After |
|---|---:|---:|
| Samples | 5,292 | 5,273 |
| CPU frame duration P50 / P99, ms | 15.76 / 24.33 | 14.97 / 24.51 |
| CPU frame duration maximum, ms | 34.77 | 44.37 |
| Frame overrun P50 / P99, ms | 9.87 / 19.62 | 9.90 / 19.79 |
| Frame overrun maximum, ms | 32.51 | 42.88 |
| Positive overrun samples | 4,534 | 4,084 |

Median and P99 overruns are essentially unchanged, with a worse maximum after the change. Fewer positive samples alone do not establish a clear performance gain. Five sequential iterations on one charging phone do not establish causality or satisfy the no-dropped-frame budget.

Measured-window FrameTimeline classification is complete for all ten traces after reconnection:

| Five measured windows per source | Before | After |
|---|---:|---:|
| FrameTimeline records | 5,188 | 5,161 |
| Dropped Frame | 13 | 12 |
| App Deadline Missed | 0 | 0 |
| Contains Buffer Stuffing | 4,943 | 4,978 |
| Drop offsets from measure start, seconds | 8.10–8.39 | 8.10–8.35 |

Before drops per iteration: 5 / 1 / 3 / 2 / 2; after: 2 / 3 / 3 / 3 / 1. Both sources still drop frames around the controls. A difference of one drop in these short sequential samples does not establish a clear improvement. FrameTimeline counts and raw FrameTimingMetric sample counts use different matching rules and must remain separate. The no-dropped-frame budget is still open.

## Evidence and remaining work

Ignored evidence: `build/reports/release/countdown-formatting-2026-11-16/`. Before/after folders retain five traces each, benchmark JSON, connected-test XML/logs, source/APK checksums and device conditions. The root retains host XML totals, release artifact audit, dependency metadata/POM, metric comparison and successful/failed verification logs. Read-only classification queries remain in `tools/performance/`; `frame-summary.json` and per-trace CSVs retain completed analysis. Power-rail import warnings are preserved; no power measurements are claimed.

The phone disconnected after both benchmarks passed. Reconnection on November 17 allowed all saved traces to be classified using the existing dependency's processor. Benchmark/instrumentation packages were absent on reconnection, so their installed checksums cannot be confirmed retrospectively; the host APK hashes and completed test reports are retained. No SDK, JDK, emulator or global tool was installed.

All four owned temporary phone files were removed and absence verified: `/data/local/tmp/sleep-timer-trace-processor`, `/data/local/tmp/sleep-timer-countdown-comparison.trace`, `/data/local/tmp/sleep-timer-countdown-jank.sql` and `/data/local/tmp/sleep-timer-countdown-events.sql`. Final audit found no normal/benchmark TimerService, no test-player service and an active wake-lock list of size zero. Acquisition/release history is not an active wake lock.

With no user timer active, `bash ./scripts/run-phone.sh` built the normal debug app (1 s, 270 tasks, 1 executed / 269 up-to-date), installed with data preserved and opened MainActivity. The installed APK hash matches the local build. Android CLI layout and an immediately reviewed screenshot confirm idle Home, readiness/test controls, selected 45 min and a correctly rendered 45:00 countdown. No timer/test was started during this follow-up. Installation/layout/cleanup/checksum evidence and the Home image are retained in the after folder. Final ordinary-cache `formatAll verifyRelease` passes again (1 s, 1,431 tasks, 24 executed / 1,407 up-to-date).

Next: inspect the control interactions at the retained drop offsets, classify dial frames and test presentation/dimming hypotheses. Real-player screen-off/Doze, fade/restore/routes, overnight/OEM, API 37 hardening, actual 16 KB runtime, final controlled budgets and external identity/signing remain open. Phase 6 is not complete.
