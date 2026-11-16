# Phase 6 countdown formatting comparison

November 17, 2026 (Asia/Kolkata; trace filenames use November 16 UTC).

The dial now owns locale-specific number formatters instead of constructing them on every update. The hours formatter initializes only for hour durations. Numeric text is remembered by rounded seconds; a locale change replaces the formatter. Rounding, localized digits, padding, layout and the Material 3 wave remain unchanged. End-time formatting retains its existing timezone and 12/24-hour handling. This removes repeated construction by source inspection; allocated bytes and a frame-time improvement have not been established.

Production change: `03887c5`. Separate test-runtime update: `215a5f0`, Robolectric 4.18. Baseline checkout `5c8928e` contains the preceding app source `5cb3ae5` plus diagnostics/docs and the owner's phone commands; both runs used the unchanged `147188c` interaction harness and existing required app profile. The Robolectric update affects host tests only.

## Verification

- Complete host/release verification passes: **214 tests**, zero failures/errors/skips, **15 image comparisons** against unchanged baselines, lint, Spotless/ktlint, detekt, build-logic and nine artifact-parser tests. Regression coverage includes repeated hour/minute transitions with localized digits and US → Arabic → US composition changes.
- Both physical benchmark executions pass: one method, five iterations each; before 2 min 28 s, after 2 min 18 s. This is runner duration, not startup or interaction latency.
- Optimized unsigned production APK: **3,143,023 bytes**; AAB: **6,625,012 bytes**. Artifact/native/permission audit passes. These are artifact sizes, not Play download measurements.

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

Measured-window FrameTimeline classification completed for all five baseline traces: **5,188 records, 13 Dropped Frame, zero App Deadline Missed, 4,943 containing Buffer Stuffing**. Drops occurred 8.10–8.39 s into measurement, around the controls. Candidate analysis completed two traces (2,036 records, five drops) before the phone disconnected. The remaining three traces are retained locally and unclassified; do not compare that partial total against the full baseline or claim zero missed deadlines for unavailable traces.

## Evidence and remaining work

Ignored evidence: `build/reports/release/countdown-formatting-2026-11-16/`. Before/after folders retain five traces each, benchmark JSON, connected-test XML/logs, source/APK checksums and device conditions. The root retains host XML totals, release artifact audit, dependency metadata/POM, metric comparison and successful/failed verification logs. Read-only classification queries remain in `tools/performance/`; `frame-summary.json` and per-trace CSVs retain completed analysis. Power-rail import warnings are preserved; no power measurements are claimed.

The phone disconnected after both benchmarks passed, while using the existing dependency's trace processor. It briefly reappeared in ADB, then disappeared before the resumed analysis/installed-checksum checks could run. Candidate installed-APK checksum confirmation, remaining analysis, temporary-file cleanup and normal debug installation still require a stable connection. No SDK, JDK, emulator or global tool was installed. Normal app data has not been cleared, and no installation was attempted while disconnected.

Resume analysis with the retained `analyze-traces.py after` script in the evidence directory. Owned temporary phone paths requiring removal are `/data/local/tmp/sleep-timer-trace-processor`, `/data/local/tmp/sleep-timer-countdown-comparison.trace`, `/data/local/tmp/sleep-timer-countdown-jank.sql` and `/data/local/tmp/sleep-timer-countdown-events.sql`. Check service/wake ownership after cleanup; no final device audit was possible while disconnected. Final ordinary-cache `formatAll verifyRelease` also passes (2 s, 1,436 tasks, 25 executed / 1,411 up-to-date).

Next: finish these phone actions, inspect the control interactions at the retained drop offsets, classify dial frames and test presentation/dimming hypotheses. Real-player screen-off/Doze, fade/restore/routes, overnight/OEM, API 37 hardening, actual 16 KB runtime, final controlled budgets and external identity/signing remain open. Phase 6 is not complete.
