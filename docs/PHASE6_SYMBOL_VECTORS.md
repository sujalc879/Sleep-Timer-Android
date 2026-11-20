# Phase 6 symbol vector retention

November 19, 2026 (Asia/Kolkata). Working change on `71d5ec5` plus the verified recovery harness; edits remain uncommitted. The same hardened runner is used for both builds.

The theme now retains the parsed data for lazily used Material Symbols across short-lived screen branches. In the physical comparison, control-phase XML opens fall **35 → 5**. Dropped-frame classifications remain **10 → 10**; the frame budget is still open.

## Trace and source evidence

Audited validation-6874826c2c iteration 000 around cancellation. The main-thread frame at 8.275 s lasts **28.242 ms**, with **11.654 ms** recomposition, **10.218 ms** measure/layout and a nested **2.058 ms** AssetManager XML open. The preserved APK resource table maps res/AA.xml to drawable/ic_check_circle. These overlapping wall-time slices are not exclusive CPU costs.

The cached Android 15 framework maps transaction 75 to setServiceForeground and 50 to stopServiceToken. The inspected trace has one of each, agreeing with normal foreground removal/service stop. No evidence supported deleting these required lifecycle calls as duplicate work. Their IDs are platform specific.

The pinned [Compose UI 1.12.1 source artifact](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-android/1.12.1/ui-android-1.12.1-sources.jar), SHA-256 `8ddde80690da97cd840c6f8f59c608701f9e42c245f12f0eabc1434db57e0273`, shows weak ImageVectorCache entries in painterResource. Returning to an idle composition can reload a vector. Public vectorResource and rememberVectorPainter APIs provide the same parsed-vector/standard-painter path.

## Change and limits

SleepTimerTheme provides a lazy EnumMap bounded by the **20 existing symbols**. It retains ImageVector data until the theme's resources, Android theme or LocalConfiguration changes, or its composition ends. Each Icon keeps the standard vector painter, live tint, content description and mirroring. The cache stores no Context, Resources or Theme references; those inputs are only composition keys and loader arguments. No process singleton, bitmap substitution or preload was added. Outside SleepTimerTheme, SleepTimerIcon keeps its original painterResource fallback.

Layout, colors, motion, countdown math, timer/service lifetime, audio and volume behavior are unchanged. The toolbar Tune icon uses a separate painterResource call and remains outside this cache. Remaining text/layout/render and buffer waits need further work.

## Host verification

`formatAll`, then `verifyRelease :app:assembleBenchmarkRelease` pass. Full verification: **224 host tests**, zero failures/errors/skips; **16 image comparisons** (12 Roborazzi comparisons against 11 existing PNG references, plus four widget preview comparisons), lint, Spotless/ktlint, detekt, build-logic, nine artifact-verifier tests and optimized artifact audit. The new dark → running → light idle regression reuses home-idle-light.png. No reference image or threshold changed.

Full check: **1 min 38 s**, 1,440 tasks (165 executed / six from cache / 1,269 up-to-date). Recorded unsigned APK: **3,143,435 bytes**; AAB: **6,620,425 bytes**. These are artifact bytes, not Play download measurements. Existing Gradle 10 deprecation notices remain.

## Physical comparison

Successful pair: `comparison-f1fa41b1d7`. Both instrumentation executions report **OK (1 test)**, five iterations each, with required-profile partial compilation and the same unchanged wave/extend/reduce/cancel interactions. Installed target/runner hashes match the retained APKs; existing profile sources were not regenerated. No benchmark suppression or global power, refresh-rate, animation or accessibility setting changed.

Moto g54 5G / Android 15/API 35, USB powered. Baseline charge **43 → 44%**, temperature **36 → 36 °C**; candidate **44 → 45%**, **36 → 37 °C**. Both endpoints report thermal status **0**, and benchmark thermal-throttle sleep is zero.

| Control-phase work across five iterations | Before | After |
|---|---:|---:|
| Main-thread XML opens | 35 | 5 |
| Reopens of each of six cached symbol resources | 5 | 0 |
| Toolbar Tune reopens | 5 | 5 |
| Largest recomposition slice, ms | 11.28 | 9.26 |
| Largest Compose:recompose slice, ms | 7.39 | 4.72 |
| Largest measure/layout slice, ms | 10.80 | 12.44 |
| Largest postAndWait slice, ms | 17.33 | 13.31 |

Each baseline cancellation reopens Check circle, Timer, More, Schedule, Play, Bedtime and Tune. The candidate only reopens Tune. The six cached-symbol reopens are absent in **every** candidate iteration. Fresh baseline check-circle opens total **1.032 ms** across five iterations, with a **0.449 ms** maximum; the earlier audited 2.058 ms event is a separate capture. XML-open slices exclude additional parser/vector construction work. The confirmed result is reduced repeated loading, without an overall frame-budget pass.

| Frame result | Before | After |
|---|---:|---:|
| FrameTimeline records in measureBlock | 5,173 | 5,135 |
| Dropped Frame classifications | 10 | 10 |
| App Deadline Missed flags | 1 | 0 |
| Buffer Stuffing records | 4,971 | 4,938 |
| Raw frame CPU P50/P99, ms | 15.96/24.79 | 15.88/24.97 |
| Raw frame overrun P50/P99, ms | 10.20/19.64 | 10.06/19.76 |
| Raw maximum overrun, ms | 27.63 | 29.49 |

Drop counts are **3/2/2/2/1 in both builds**. Baseline raw sample lists are **4,593/1,070/1,051/1,059/1,044**; candidate **1,061/1,052/1,058/1,045/1,037**. The large first baseline list again requires the capture/window distinction in [the preceding audit](PHASE6_HARNESS_RECOVERY.md). Raw CPU/overrun samples and FrameTimeline records use different matching rules. All values and outliers remain retained. This sequential pair confirms the loading change; it does not establish a general frame improvement. Layout maximum is worse, and buffer waiting remains significant.

## Evidence and delivery

Ignored root: build/reports/release/control-cost-2026-11-19/. It retains source/API audit copies, APK archives and hashes, both instrumentation commands/logs, device conditions, ten raw traces, original metric JSON, per-trace classifications/events/binder/window/resource/cost CSVs, import warnings and complete analysis summary. New read-only queries are frame-control-resource-loads.sql and frame-control-presentation-cost.sql; the historical fixed-window query remains unchanged. No power measurement is claimed.

Cold launch after the candidate benchmark verifies persisted idle. After checking retained and installed hashes, the isolated target/runner, unique comparison output root and three owned analysis files are removed and their absence checked. The owner's normal app and test player are preserved. The verified normal debug update is delivered through the actual scripts/run-phone.sh build/non-streaming install/open path, preserving app data. Its installed SHA-256 matches the built APK, and final UI inspection confirms idle Home. No normal timer/playback test was started. Final audit: no normal TimerService, no test-player service, active wake-lock list size zero and default NATIVE backend; the inspection session is closed. These checks are retained with the evidence.

Next: inspect the remaining control-phase text/layout and render-buffer costs, then refresh final profiles and repeat the full performance suite. Real-player/Doze, safe volume/routes, overnight/OEM, Android 17/WIU, actual 16 KB runtime, production identity and upload signing remain open. Phase 6 is not complete.

## Phone check

```bash
cd /Users/sujalsunilchaudhary/coding/projects/sleepTimer
bash ./scripts/run-phone.sh
```

Switch between idle/running, return from Settings and change Light/Dark/System: icons should keep the same shapes, colors and descriptions. Play audio, start a timer, then Stop: the timer cancels and audio should continue. The separate 10 s test checks expiry stopping playback; this performance task does not replace that real-player test.
