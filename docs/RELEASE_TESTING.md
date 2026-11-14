# Release and performance validation

Updated November 14, 2026 (Asia/Kolkata). Phase 6 host checks and real baseline/startup profile collection pass. Connected-device evidence, including startup/readiness fixes, preliminary timings and the final controlled playback smoke, is recorded in [PHASE6_DEVICE_RESULTS.md](PHASE6_DEVICE_RESULTS.md). Frame overruns remain unresolved. Carry every pending case in [MANUAL_TEST_PLAN.md](MANUAL_TEST_PLAN.md) into the release decision.

## Host checks

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew verifyRelease --continue --console=plain
```

This includes debug assembly/JVM tests, lint, formatting/detekt/build-logic checks, existing native image comparisons, profile/benchmark APK compilation, release lint/APK/AAB, native artifact inspection and parser regression tests. No device, emulator or SDK download is started. Reports are in `build/reports/release/artifacts.json` and the usual module build report directories. R8 mapping/configuration are in `app/build/outputs/mapping/release/`.

The report distinguishes an inherited library binary profile from **app-generated profile source**. An existing baseline.prof from dependencies is not proof that Sleep timer's journeys were collected. Real collected sources now pass `--require-app-profile`; the stricter `verifyDistributionArtifacts` gate still requires valid external release signing. See [PLAY_SUBMISSION.md](PLAY_SUBMISSION.md) for signing, version inputs and owner decisions.

Initial host ELF checks found both `libandroidx.graphics.path.so` and `libdatastore_shared_counter.so` for arm64-v8a, armeabi-v7a, x86 and x86_64. All initial PT_LOAD alignments are 16384; APK native offsets also pass Build Tools verification:

```sh
"$HOME/Library/Android/sdk/build-tools/36.0.0/zipalign" -c -P 16 4 app/build/outputs/apk/release/app-release-unsigned.apk
```

Use the signed filename from output-metadata.json when signing is configured. This is binary inspection, not a 16 KB runtime test or Play split-APK verification. The standard-library verifier needs no NDK installation.

## Install and open the normal app on the phone

Connect an unlocked phone with a USB data cable, enable Developer options → USB debugging, and accept the authorization prompt. From the repo root:

```sh
./scripts/run-phone.sh
```

The helper checks the phone before building, selects the existing local SDK/JDK, builds the debug APK, updates that selected device while preserving app data, and opens MainActivity. It does not grant optional app permissions or start a timer. If no phone is detected, it prints the connection steps instead of running Gradle installation. For multiple phones:

```sh
ANDROID_SERIAL=YOUR_DEVICE_SERIAL ./scripts/run-phone.sh
```

Equivalent direct commands:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
adb devices -l
./gradlew :app:installDebug
adb shell am start -W -n com.sujal.sleeptimer.debug/com.sujal.sleeptimer.MainActivity
```

Require status `device`; `unauthorized` needs the phone's prompt. An empty list needs cable/port/debugging correction. The phone reconnected for the November 13 profile/performance work. Start music and use Home → 10 s test, then the two-minute screen-off/Doze procedure in the manual plan. A successful installation alone is not a playback pass.

## Baseline profile collection

Use an unlocked **API 33+** physical phone (the owner's Android 15 phone qualifies), English UI, normal font/display scale, no battery saver, stable temperature, and system animations enabled. Keep it on a test surface; the running screen may dim. Do not measure an actively charging/hot/throttled device as a final performance result. Record battery/power/refresh-rate/thermal state and installed firmware.

The producer/benchmark targets use **com.sujal.sleeptimer.benchmark**, labelled Sleep timer benchmark, signed locally for testing. Instrumentation packages are separate. These journeys never clear the normal debug/release app's data. They grant exact-alarm/notification access only to the benchmark app and use English selectors; consent UX is tested separately. Begin with a fresh benchmark test install if its settings were manually changed. Do not reuse the benchmark app as a personal overnight timer.

```sh
./gradlew :app:generateBaselineProfile --console=plain
```

The producer records startup and Settings/dial/running/extend/reduce/cancel journeys. Both passed on November 13 and reached profile stability; see the evidence report for counts/hashes. Each running journey uses 60 minutes and cancels before ending; final cleanup force-stops only the isolated package. Inspect and commit the generated `app/src/main/generated/baselineProfiles/` files after a real successful run. Require nonempty app-package rules and preserve the source commit/device/run log. Do not hand-write a broad profile or copy dependency profiles to satisfy the gate. Build again after collection:

```sh
./gradlew :app:assembleBenchmarkRelease :app:assembleRelease :app:bundleRelease
python3 scripts/verify_release.py --require-app-profile
```

Profile compilation/packaging and a final runtime smoke remain required. Normal release assembly has automatic device generation disabled; the explicit collection task is the only collection request. [Profile generation reference](https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile).

## Startup and frame measurements

After app profile generation and rebuilding the benchmark target:

```sh
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest --console=plain
```

The startup class compares ten cold launches with CompilationMode.None and a required baseline profile. Three frame tests collect dial gestures, Settings scrolling and eight seconds of visible countdown/wave with extension/reduction/cancellation. Frame setup explicitly kills/relaunches before preparing the screen; it does not use cold-start mode to kill the prepared state after setup. The tests are self-instrumenting in their own process; the target is optimized/non-debuggable/profileable.

November 13 all five methods completed across reruns. Required-profile startup median was 441.65 ms with all ten samples below 500 ms, while charging. Settings/dial/running all recorded positive frame overruns; the frame target is not met. These measurements precede the November 14 readiness fix. Inspect traces, fix the cost and repeat the suite on final source under controlled battery-powered conditions; do not reuse these results as a final budget pass.

Keep the connected-test report, benchmark JSON and Perfetto traces from `benchmark/build/outputs/` and `benchmark/build/reports/androidTests/`. Capture tool-reported paths instead of assuming a trace was generated. Required-profile failure, tracing setup failure, thermal warnings or an incomplete journey are failures/inconclusive runs, never timing passes. No timing error suppressions or dry-run results qualify for a budget. [Macrobenchmark reference](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview).

For interrupted tests, ensure cleanup of the isolated app:

```sh
adb shell am force-stop com.sujal.sleeptimer.benchmark
```

## Evidence gates

These release gates remain **OPEN**; see the device evidence report for partial results. Fill one row per actual configuration and retain raw evidence. Do not derive an all-frames guarantee from a good percentile alone. The first optimized-device run exposed a reflective WorkManager database constructor removed by R8; the constructor-specific fix applies to both production and benchmark builds. A passing host build had not caught that runtime crash.

| ID | Gate | Required evidence |
|---|---|---|
| R01 | Cold first frame <500 ms on a mid-range phone with app profile | Actual required-profile cold-launch samples and traces; report every sample, median and spread; investigate any sample exceeding the target |
| R02 | No dropped frames in dial/countdown/wave | FrameTimingMetric results plus trace inspection for missed deadlines; report positive overruns/jank, not just P50/P95/P99 |
| R03 | Clock composition and background ticking | Compose/system trace confirms isolated dial updates, no unrelated screen/overlay work each second and no hidden UI ticker; host recomposition regression is supporting evidence only |
| R04 | No main-thread disk/blocking binder work or leaks | StrictMode/debug journeys and LeakCanary observation over repeated navigation/runs; optimized production trace and retained-object review; do not treat an unobserved leak as clean |
| R05 | Screen-off battery/wake ownership | Natural Doze/3 h/overnight traces, wake leases and sensor lifecycle across exact allowed/denied and wake-up/ordinary sensor hardware; keep the existing conservative shake-window cost visible |
| R06 | Download target <5 MB | Optimized APK bytes and per-device Play/bundletool compressed split estimates; the AAB includes metadata and is not the download measurement |
| R07 | Optimized runtime/retention and 16 KB | Signed candidate smoke, Hilt/settings persistence/Glance actions/receivers/shortcuts/test pipeline, actual 16384-byte runtime and generated split APK alignment |
| R08 | Android/OEM reliability | Critical manual matrix, including Android 17 WIU/hardening, call/alarm/volume, surface latency, force-stop/reboot and real players; carry every unsupported/unavailable case explicitly |

Record date, source commit, APK/profile checksums, device/API/firmware, benchmark version, locale/font/theme, thermal/power/refresh-rate state, access/permission state, exact command, expected vs observed values and report/trace paths. Performance improvements require a like-for-like before/after comparison.

The UI now keeps a distinct layout projection and reads ticking state at the dial. The −5 min control observes only its five-minute threshold. The stricter brief target of text-only tick invalidation, actual frame cost and startup time still require device tracing; no zero-jank claim is made from that source refactor.

## Final signed-candidate smoke

After the owner supplies the permanent identity/upload signing, install the signed optimized candidate on a test device and repeat startup, Settings changes across process recreation, Custom/Nightly, test player stopping, both widgets, tile, shortcuts/Undo, permission denial/revocation, optional admin deactivation and profile verification. Verify the signed release manifest and checksums from the same build that will be uploaded. Review diagnostics locally. Finish every critical pending manual case before rollout; unresolved signing/profile/performance/device gates keep Phase 6 open.
