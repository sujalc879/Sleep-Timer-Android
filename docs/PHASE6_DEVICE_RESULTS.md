# Phase 6 device evidence — November 13–14, 2026

Baseline/startup profile collection passed on the owner's Moto g54 5G, Android 15 (API 35). All five benchmark methods completed across the reruns described below. Profiled cold startup was under 500 ms in all ten samples, but the charging-device run is preliminary. Frame overruns were measured, especially on the running screen: the no-dropped-frames target is **not met**. Phase 6 remains open.

Later [frame analysis](PHASE6_FRAME_ANALYSIS.md) refines the interpretation: ten steady-wave measured windows have no drop/app-deadline classification; most latency is buffer stuffing. The mixed control journey has five drops. The original evidence below remains intact; the overall frame gate is still open.

Profile collection used application source `e5bce13`; the frame setup correction is `7c427f2`. The optimized startup fix is `acbd97d`; generated profile source is committed as `1c8c3ab`. The countdown fixture correction is `bc93b8b`. November 13 timing results predate the November 14 execution-readiness fix and must not be presented as measurements of that later change. Profile rules were not hand-edited.

## Device conditions

Observed before collection: unlocked/interactive, en-IN, font scale 1.0, Battery Saver off, animation scales 1.0 and peak refresh setting 120 Hz. Battery was 64%, charging (AC powered), 37 °C. Before/after the benchmark suite it was 65%/68%, still charging at 37 °C; thermal status was 0 with no override. Benchmark JSON reports cpuLocked=true, sustained-performance mode off and zero thermal-throttle sleep for every completed method. These conditions do not qualify timings as final battery-powered performance evidence. No power/animation/locale settings were changed.

Firmware: `motorola/cancunf_g_sysq/cancunf:15/V1TDS35H.83-20-5-14/24971a-330658:user/release-keys`; security patch `2026-09-01`; Benchmark/plugin 1.5.0, UI Automator 2.4.1.

No normal debug-app TimerService was active before the run. The existing optional notification listener was bound. The producer installed only the isolated benchmark target and instrumentation package; it granted exact-alarm/notification access only to that target. It did not clear or change the normal app's settings.

## Profile collection

Command, using the existing JDK 17 and the selected connected phone:

```sh
ANDROID_SERIAL=YOUR_DEVICE_SERIAL JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :app:generateBaselineProfile --console=plain
```

**PASS**, 10 min 23 s; 325 tasks, 7 executed / 318 up-to-date. Both instrumentation tests passed, zero failures/errors/skips. Startup reached profile stability at iteration 7; Settings/dial/running/extend/reduce/cancel reached stability at iteration 11. The pinned Benchmark library explicitly reported both profiles stable. Running journeys used 60 minutes and canceled before expiry; the fixture's final cleanup force-stopped the isolated benchmark app. This is not a test of the expiry pipeline.

| Generated source | Rules | App-package rules | Bytes | SHA-256 |
|---|---:|---:|---:|---|
| `app/src/main/generated/baselineProfiles/baseline-prof.txt` | 18,413 | 1,977 | 2,192,369 | `46985becdba0fdd194eb2608d9c077407afcdd5d4ba38fa2978d848af103263b` |
| `app/src/main/generated/baselineProfiles/startup-prof.txt` | 17,565 | 1,129 | 2,070,944 | `8ff2c41e184e9835165e0456341255cb3bd3439d13349318a894106a478c4170` |

App-package counts match rules containing `Lcom/sujal/sleeptimer/`. The generated sources also contain exercised dependency rules. Preserve the generated output; neither the rule count nor profile stability proves a performance budget.

Raw evidence remains in ignored build output:

- `baselineprofile/build/reports/androidTests/connected/nonMinifiedRelease/index.html`
- `baselineprofile/build/outputs/androidTest-results/connected/nonMinifiedRelease/TEST-moto g54 5G - 15.xml`
- `baselineprofile/build/outputs/androidTest-results/connected/nonMinifiedRelease/moto g54 5G - 15/logcat-com.sujal.sleeptimer.baselineprofile.BaselineProfiles-startup.txt`
- The sibling `logcat-com.sujal.sleeptimer.baselineprofile.BaselineProfiles-settingsDialAndRunningTimer.txt`
- `baselineprofile/build/outputs/connected_android_test_additional_output/nonMinifiedRelease/connected/moto g54 5G - 15/`
- `build/reports/release/device-2026-11-13/` retains the Gradle collection/build logs and artifact report from this run.

## Rebuild and packaging

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew verifyRelease :app:assembleBenchmarkRelease --continue --console=plain
python3 scripts/verify_release.py --require-app-profile
"$HOME/Library/Android/sdk/build-tools/36.0.0/zipalign" -c -P 16 4 app/build/outputs/apk/release/app-release-unsigned.apk
```

The initial profiled host build passed, then the optimized phone run exposed a runtime crash that host checks had not caught: Room's reflective WorkDatabase_Impl constructor had been removed. The narrow constructor rule fixed initialization/profile installation in production and benchmark builds. The post-fix November 13 host build passed in 47 s (1,440 tasks, 47 executed / 2 from cache / 1,391 up-to-date); the subsequent fixture verification passed in 6 s (1,386 tasks, 34 executed / 1,352 up-to-date). Those runs had 209 host tests, zero failures/errors/skips, plus required lint/formatting/detekt, 15 image comparisons and nine artifact-parser tests. The November 14 readiness correction is verified separately below.

| Unsigned artifact | Bytes | SHA-256 |
|---|---:|---|
| November 13 post-retention-fix APK | 3,143,023 | `f34e78b019fe0ff8d4e4e7c6f0794fafd77157ec4b7f85c5ed17e3677ece986c` |
| November 13 post-retention-fix AAB | 6,622,495 | `1a3895e5d8e9f65316d744ff53fbae77c9f7e89193d2afb516e5894b0d8f6447` |

These are this build's artifact bytes/hashes, not Play download estimates. All eight native ELF entries pass APK/AAB identity and 16 KB alignment inspection; Build Tools also accepts the APK ZIP alignment. Production permission/debug-tooling checks pass. Signing, actual 16 KB runtime and Play-generated APK verification remain open.

## November 13 performance results

```sh
ANDROID_SERIAL=YOUR_DEVICE_SERIAL JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest --console=plain
```

The first run failed all five methods before measurement because of the Room constructor crash (21 s). After the retention fix, four methods passed and countdown setup failed with StaleObjectException (suite 9 min 54 s). The bounded setup requery fix then passed the affected method, five iterations, in 2 min 55 s:

```sh
ANDROID_SERIAL=YOUR_DEVICE_SERIAL JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.sujal.sleeptimer.macrobenchmark.FrameBenchmarks#countdownAndWave' --console=plain
```

No error suppression or timing dry-run was used. CompilationMode.Partial required successful installation of the real baseline profile. Completed methods have zero thermal-throttle sleep; initial failures remain archived separately.

| Cold first-frame timing, 10 launches each | Minimum ms | Median ms | Maximum ms |
|---|---:|---:|---:|
| No compilation/profile | 493.24 | 525.75 | 583.92 |
| Required baseline profile | 436.95 | 441.65 | 482.67 |

Samples in launch order (ms, rounded to two decimals):

- No profile: 583.92, 528.31, 524.20, 501.64, 527.30, 493.24, 542.41, 507.55, 527.94, 516.80.
- Required profile: 479.05, 465.44, 452.98, 440.54, 436.95, 442.37, 438.16, 482.67, 440.93, 440.86.

| Frame journey, 5 iterations each | CPU P50 / P99 ms | Overrun P99 / maximum ms | Positive overrun samples / total |
|---|---:|---:|---:|
| Settings scroll | 4.21 / 9.93 | −0.02 / 22.02 | 23 / 2,216 |
| Dial drag | 4.89 / 13.73 | 3.53 / 31.17 | 195 / 1,348 |
| Countdown/wave + extend/reduce/cancel | 15.83 / 23.93 | 19.26 / 44.49 | 6,979 / 8,382 |

Positive counts come from every raw frameOverrunMs sample in benchmark JSON, not percentile inference. They are not an exact count of physically dropped frames. The wave journey has a large first-iteration frame-count spread (4,141 versus 1,056–1,065 in later iterations); inspect trace durations and UI work before optimizing. No zero-jank, text-only invalidation or hidden-ticker claim is made. R01 needs controlled battery-powered confirmation; R02 remains unmet and R03 needs trace analysis. All 35 measurement traces are retained, without claiming they have been fully inspected.

Raw runs under `build/reports/release/device-2026-11-13/`:

- `benchmark-before-room-fix/`: all five setup failures, crash/mapping/usage evidence.
- `benchmark-after-room-fix/`: four passing methods, the stale countdown failure, benchmark JSON and 30 Perfetto traces.
- `countdown-after-stale-fix/`: passing countdown rerun, benchmark JSON and five traces.

## Normal-app playback smoke

On November 13, `bash ./scripts/run-phone.sh` built successfully, installed with data preserved and opened the normal debug app. Its probe first reported no local/remote playback. With the silent GOOD test player running, Home Start → Stop produced CANCELLED/NONE, returned idle, and the probe still reported local playback active. This supports the selected cancellation behavior.

The first November 14 controlled expiry test had a captured 10,000 ms duration. The player logged paused 59 ms after its deadline; the final stored MEDIA_KEY result attempted at +310 ms and completed at +350 ms. Fade was skipped because the route was unsafe/unavailable. The log also exposed repeated service-ready reconciliation restarting verification; D060 documents the correction and its regression. This first result is retained separately from the corrected rerun. It establishes no physical fade/restore, Doze or overnight result.

## November 14 execution-readiness correction

Source `5cb3ae5` makes both the service-promotion announcement and the alarm's ensureForeground intent use ExecutionReady. Repeated readiness preserves the current effect and alarm plan; actual loss/restart/access/time-change reconciliation retains its behavior. An initial change covering only the promotion announcement still restarted verification on the phone; that failed intermediate run is retained. The final fix covers the intent decoder too.

Final host commands:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew formatAll --console=plain
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew verifyRelease --continue --console=plain
python3 scripts/verify_release.py --require-app-profile
"$HOME/Library/Android/sdk/build-tools/36.0.0/zipalign" -c -P 16 4 app/build/outputs/apk/release/app-release-unsigned.apk
```

All **PASS**. Final verification: 1 min 7 s; 1,386 tasks, 200 executed / 13 from cache / 1,173 up-to-date. Host XML: **212 tests**, zero failures/errors/skips, including the new suspended-verification regression and decoder cases on APIs 26/35. Existing 15 image comparisons and nine artifact-parser tests pass. No image baseline or analyzer threshold was changed.

The phone was idle before update installation; no personal playback was active. `bash ./scripts/run-phone.sh` installed/opened the updated normal app with data preserved. The final real 10-second test used the silent GOOD test player, a visible Activity and the captured monotonic deadline:

| Observation | Result |
|---|---|
| Stop attempts in the new app process | **1** |
| First stop attempt | Deadline +55 ms |
| Player's paused event | Deadline +66 ms |
| Persisted result | STOPPED / MEDIA_SESSION; completed deadline +331 ms |
| Selected duration | 45 min before/after the final test, unchanged |
| Fade/restore | Skipped: route unavailable/unsafe; physical volume behavior unverified |
| Cleanup | Normal app returned to Home; TimerService and TestPlayerService absent; no owned wake lock observed |

This is a narrow, screen-on controlled-player pass. It does not establish real-player/Doze/overnight, exact-alarm denial, physical volume or signed optimized-candidate reliability. The earlier frame-timing results are still a known failure requiring final-source retesting.

| November 14 unsigned artifact | Bytes | SHA-256 |
|---|---:|---|
| `app/build/outputs/apk/release/app-release-unsigned.apk` | 3,143,023 | `9d8de92dcd80b065f363c375c06bd50f1db1dcb685540c97ebfa53b819c162b3` |
| `app/build/outputs/bundle/release/app-release.aab` | 6,622,698 | `2dcd64d2bed09595a42485be77db6efc3783d27a97edba1c0403352110365c95` |

Latest artifact/permission/native checks pass and package the existing collected profile. Preserve `build/reports/release/device-2026-11-14/`: pre-fix and intermediate deadline/result/logs, final deadline/result/player/timer logs, host/phone-launch logs, artifact report and sanitized cleanup evidence. Profile collection and performance confirmation on the final distribution candidate remain required.

## Remaining device work

Investigate the measured running/dial frame cost using the retained traces and rerun controlled performance measurements on the final source. Physical fade/restore, real-player two-minute screen-off/Doze, long runs, Android 17 and other pending manual cases remain open. No signed candidate was installed or uploaded. Preserve source/profile/build hashes with every later result.

To install and open the normal app after reconnecting:

```sh
cd /Users/sujalsunilchaudhary/coding/projects/sleepTimer
bash ./scripts/run-phone.sh
```

Start audio, tap **10 s test**, and verify the result against actual playback. Stop cancels a timer and leaves playback running; timer expiry pauses playback. Do not count successful installation/profile generation as a playback pass.
