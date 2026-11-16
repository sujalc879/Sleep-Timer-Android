# Frame analysis — November 14, 2026

The measured latency is dominated by queued presentation buffers, not demonstrated expensive wave geometry. A steady-wave comparison records no dropped-frame or app-deadline-miss classification across ten measured intervals (9,567 FrameTimeline records). The mixed wave/extend/reduce/cancel journey records five drops after extension/reduction begins. Frame budgets remain open; no production rendering or frame-rate override was changed in this task.

Application source is `5cb3ae5`. Diagnostic benchmark/query implementation is `147188c`. The first two-method comparison preceded target-package selector scoping; a separate steady run verified that scoping. Final reusable-selector copying was checked against the pinned UI Automator library and the full host build. No forced stale-node event is claimed in the final physical validation.

## Method and interpretation

Read all four design PNGs, current dial/Home source and Material 3 1.5.0-rc01 sources. The supplied CircularWavyProgressIndicator already caches geometry/drawing. Use the existing Benchmark 1.5.0 trace_processor_shell_aarch64 from the Gradle dependency, temporarily on the phone; no SDK/JDK/global tool was installed. Its SQLite export produced no usable database, so analysis used the supported read-only query interface. No trace was uploaded.

Initial retained capture: `FrameBenchmarks_countdownAndWave_iter001_2026-11-13-17-00-50.perfetto-trace`, SHA-256 `e583ec5c0e7d1e301c8d26debb6230651b3977a42e8f29bb0401bf6a8a445230`.

Across that whole capture, AndroidOwner:draw averaged 0.318 ms; RenderThread dequeueBuffer averaged 5.127 ms; the main thread waited 6.009 ms in postAndWait. These are wall-time slices, with overlapping nested totals, not exclusive CPU time. Of 1,079 app FrameTimeline records, 994 were Buffer Stuffing-only late presents; four were Dropped Frame; one combined App Deadline Missed and Buffer Stuffing. Whole-capture totals include frames outside measurement and are not interchangeable with FrameTimingMetric samples.

Perfetto describes buffer stuffing as queued buffers adding presentation/input latency, potentially while motion remains smooth. It is distinct from a dropped-frame classification. Preserve both findings, including real drops; do not turn a good average or a reclassification into an overall pass. [FrameTimeline reference](https://github.com/google/perfetto/blob/main/docs/data-sources/frametimeline.md).

## Measured comparison

The original mixed benchmark remains. New `steadyCountdownAndWave` settles the Home transition for one second in setup, measures eight seconds of the running screen, and cancels outside measurement. Each next setup cancels the prior isolated run before killing/relaunching it. Required-profile compilation, five iterations and raw FrameTimingMetric collection remain enabled. Target-package selectors protect the normal app; By.copy preserves selector reuse during bounded stale-node retries.

Phone: Moto g54 5G, Android 15/API 35. Before comparison: no user timer, unlocked, battery 30%, charging, 36 °C, thermal status 0. Before scoped validation: 33%, charging, 37 °C, thermal status 0. Completed methods report zero thermal-throttle sleep. These are charging-device diagnostics, not final controlled performance-budget evidence. No system refresh, animation or brightness setting was changed; normal app data was preserved.

```sh
ANDROID_SERIAL=YOUR_DEVICE_SERIAL JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.sujal.sleeptimer.macrobenchmark.FrameBenchmarks#countdownAndWave,com.sujal.sleeptimer.macrobenchmark.FrameBenchmarks#steadyCountdownAndWave' --console=plain
```

**PASS**, two methods, zero failures/errors/skips; 5 min 58 s, 335 tasks (3 executed / 332 up-to-date). The package-scoped steady-only rerun also **PASS**, one method/five iterations; 3 min 8 s, 335 tasks (3 executed / 332 up-to-date).

`frame-jank-in-measure.sql` uses the single recorded measureBlock interval, verified in Benchmark 1.5.0 MacrobenchmarkPhase.kt. It counts FrameTimeline records starting within that interval. Its counts can differ from FrameTimingMetric, which also matches UI/render/expected frame IDs; report the raw metric independently.

| Measured windows | FrameTimeline records | Dropped Frame | App Deadline Missed | Contains Buffer Stuffing |
|---|---:|---:|---:|---:|
| Mixed journey, five iterations | 5,200 | 5 | 0 | 5,076 |
| Steady wave, first five iterations | 4,773 | 0 | 0 | 4,773 |
| Steady wave, package-scoped validation | 4,794 | 0 | 0 | 4,389 |

Other classifications remain in the raw CSVs, including compositor scheduling/GPU, display HAL, prediction and unknown flags. The combined steady result is a narrow ten-interval observation, not a guarantee of zero jank across devices/settings or an absence of input latency.

Raw FrameTimingMetric in the initial comparison still reports high positive overruns:

| Journey | Positive samples / all samples | Overrun P50 / P99 ms | Maximum ms |
|---|---:|---:|---:|
| Mixed | 6,844 / 8,857 | 9.88 / 19.30 | 31.37 |
| Steady | 8,133 / 8,432 | 9.97 / 19.22 | 28.51 |

All five mixed drops occurred 8,178–8,268 ms into measured intervals lasting about 8.88–8.98 s. The steady period is eight seconds, followed by extension/reduction/cancellation in the mixed test. This narrows the next investigation to those interactions; the data does not identify a specific composable or library defect. One compositor GPU deadline miss occurred at 7.55 s. Dimming/presentation-cadence A/B testing and richer interaction traces remain pending. Recorded vsync work-budget counters alone do not establish the cause, so no refresh-rate override was adopted.

## Reproduction and retained evidence

Read-only queries are in `tools/performance/`:

- `frame-thread-cost.sql`: main/render thread slice aggregates for the whole capture.
- `frame-jank-classification.sql`: whole-capture FrameTimeline classifications.
- `frame-jank-in-measure.sql`: classifications inside the single measured interval.
- `frame-deadline-events.sql`: measured drop/deadline offsets.

Run each separately with the already-bundled processor's `query -f QUERY.sql TRACE` interface. A missing or multiple measureBlock interval yields no measured-window rows and must be reported as unavailable, not zero drops. Some captures report `power_rail_empty_packet: 1`; power-rail analysis is not claimed.

Ignored evidence: `build/reports/release/frame-analysis-2026-11-14/`. This contains source/processor hashes, initial query CSVs, device conditions, host logs, the comparison's ten traces, the scoped rerun's five traces, JSON, raw query output/errors and per-trace measured-window classifications. Drop-event CSVs retain the affected offsets. Earlier evidence in PHASE6_DEVICE_RESULTS.md remains unchanged.

Final format/host verification **PASS**: verifyRelease in 3 s, 1,386 tasks (28 executed / 1,358 up-to-date), 212 host tests with zero failures/errors/skips; lint, Spotless/ktlint, detekt, build-logic, 15 image comparisons, nine artifact-parser tests and release audit pass. No image baseline, benchmark suppression or global analyzer threshold changed.

Temporary phone analysis files were removed. No normal/benchmark TimerService or owned wake lock remained; the normal debug app was reopened. Next: inspect extension/reduction and dial traces, test presentation/dimming hypotheses, then make and measure a targeted production change. The release's other reliability/signing/platform gates remain open.

November 17 continuation: [countdown formatter reuse and fresh before/after evidence](PHASE6_COUNTDOWN_FORMATTING.md). Full host checks pass, but no clear frame-time improvement is established: all ten new traces are classified, with 13 drops before vs 12 after. Analysis/cleanup completed after reconnection; the updated normal app is open on idle Home, with no timer/test-player service or active wake lock. The earlier measurements and cleanup observation above apply to November 14.
