# Phase 6 notification intent reuse

November 17, 2026 (Asia/Kolkata). Production change `3149031`; diagnostic queries `cfb6734`.

The running notification now reuses its app-opening PendingIntent and one bounded pair of Extend/Stop tokens keyed by run ID and Extend by. A settings or run change rebuilds the pair. The revision-bearing dismissal token and visible notification content still rebuild normally. Explicit immutable intents, URI identities, flags, per-delivery command IDs and immediate foreground promotion are unchanged. Posting remains synchronous, preserving lifecycle ordering.

## Source and trace evidence

Inspected retained `FrameBenchmarks_countdownAndWave_iter002_2026-11-16-18-37-55.perfetto-trace`, SHA-256 `23bd72878ffd31e8a3100a8d2c1ca45e65124a2b98bb1283ac9f0d5e30764802`, from the preceding formatter build. The main-thread window 8.00–8.45 s covers extension/reduction/cancellation after eight seconds of wave observation.

| Synchronous main-thread binder calls | Count | Total wall time, ms | Maximum, ms |
|---|---:|---:|---:|
| All calls in the inspected window | 21 | 33.48 | 6.95 |
| Code 0x40 | 12 | 12.04 | 5.69 |
| Code 0x07 | 3 | 13.86 | 6.95 |

The cached Android 15 framework maps 0x40 to getIntentSenderWithFeature and 0x07 to enqueueNotificationWithTag. Four token lookups followed by one post agree with the running builder's content/delete/extend/stop factories. This is a source-supported interpretation of this trace, not a stable mapping for every platform/OEM. Repeated steady-run refreshes can now reuse three of those four tokens; the dismissal lookup and notification post remain.

The same window includes composition up to 9.77 ms, measure/layout up to 9.83 ms, render work and buffer waits. Nested slice totals overlap and are not exclusive CPU costs. Token reuse does not establish the cause of every drop or prove that remaining posting/rendering costs meet the frame budget. The earlier full frame results remain in [PHASE6_COUNTDOWN_FORMATTING.md](PHASE6_COUNTDOWN_FORMATTING.md).

The read-only queries are `tools/performance/frame-control-binder-calls.sql` and `frame-control-thread-cost.sql`. They require one measureBlock and examine the fixed 8.00–8.45 s control window of the current journey. Interpret the raw binder arguments against the tested platform; do not treat an absent/mismatched window as a zero-cost result.

## Verification and current limit

- `formatAll` passes. `verifyRelease :app:assembleBenchmarkRelease --continue --console=plain` passes in 1 min 8 s: 1,440 tasks, 100 executed / 3 from cache / 1,337 up-to-date.
- Final ordinary-cache `formatAll verifyRelease --continue --console=plain` passes in 4 s: 1,436 tasks, 25 executed / 1,411 up-to-date.
- **218 host tests** pass, zero failures/errors/skips. The two new regressions run on API 26 and 35: changed extension amounts reach the action payload; replacement runs do not retarget retained old actions; dismissal revisions remain current. Existing run/dismissal/immutability checks remain enabled.
- **15 image comparisons**, lint, Spotless/ktlint, detekt, build-logic, nine artifact-parser tests and optimized release audit pass. No image baseline or analyzer threshold changed.
- Latest unsigned APK is **3,143,023 bytes**, AAB **6,625,470 bytes**; artifact bytes are not Play download estimates.

The phone disconnected before the fresh baseline could start. The connected task failed with **No connected devices**, zero benchmark iterations; this is not a benchmark pass. No after-change frame/binder measurement or normal-app update has occurred. No speedup, reduced drop count or completed performance budget is claimed.

Reconnection follow-up: after the owner replied Connected, SDK ADB 37.0.1 still listed no devices. There is no alternate ADB server socket/port environment override. Both the IOUSB plane and USB host-device inventory found no matching phone (the latter enumerated five USB devices). This needs a physical USB/data/debugging connection before another run; no comparison or installation was attempted during this follow-up. Try a data-capable cable/another port, keep the phone unlocked and accept any USB-debugging prompt, then require ADB status `device` before continuing.

Data-cable follow-up: the phone appeared authorized and unlocked, with no normal TimerService; USB powered, 74% battery, 37 °C, thermal status 0. The saved pre-change benchmark app installation command succeeded, but the 48.81 MiB runner transfer lost ADB at install-commit (owned session 47782822), before instrumentation. Zero new benchmark iterations ran; the normal app was not updated. macOS still detected the Motorola USB device while native ADB repeatedly logged `usb_read failed` / `e00002ed`. A documented session-only LIBUSB backend retry also failed; the original NATIVE backend was restored. No tool installation or shell-profile change occurred. The owner was asked to restart USB debugging and accept authorization, keeping the screen on.

Ignored evidence: `build/reports/release/control-frames-2026-11-17/`. This retains trace/processor hashes, queries/CSV/import warnings, Android 15 binder-code inspection, host XML totals/logs/artifact audit and both pre-change/candidate benchmark APKs plus their unchanged test runner. The pre-change checkout is `f940ca8` (app source `03887c5`); candidate is `3149031`. The normal phone app still has the preceding formatter build. Normal app data/settings and PHONE_COMMANDS.md were preserved; no SDK/JDK/emulator/global tool was installed.

The failed manual deployment is retained in `comparison-fca6f671e1/`, with conditions, command logs and error details; `latest-comparison.json` records failure. `deployment-recovery.json` and the before/alternate/restored ADB server status files record transport recovery attempts. No failed deployment is timing evidence. The retained comparison script is `run-preserved-comparison.py`; review the recovery steps below before retrying its deployment.

## Resume on a connected phone

Capture actual power/thermal/unlocked conditions, confirm no user timer is active and measure the preserved pre-change APK followed by the candidate with the same five-iteration countdownAndWave method, required profile and existing fixture. The [official instrumentation guide](https://developer.android.com/topic/performance/benchmarking/benchmarking-in-ci) documents running preserved APKs via am instrument and pulling output from the benchmark package's external media directory. Retain all failures/samples and classify frames plus the corresponding control binder calls before attributing an improvement.

Prioritize installing/opening the normal app once ADB recovers, after checking for an active user timer. The SDK's documented `adb install --no-streaming -r` separates file transfer from Package Manager commit and is an available fallback to the failed CLI streaming deployment; it has not yet been exercised here. Check whether the runner actually installed and verify its hash before retransferring 48.81 MiB. Abandon owned install session 47782822 only if it remains open. Keep the screen on during installation/analysis; no global screen-timeout or power setting was changed in this task. See [ADB backend selection](https://developer.android.com/tools/adb) for the existing SDK option tested above; changing backends did not resolve this connection failure.

Remove these owned temporary files after analysis: `/data/local/tmp/sleep-timer-controls-processor`, `/data/local/tmp/sleep-timer-controls.trace`, `/data/local/tmp/sleep-timer-controls-query.sql`. Final removal/service/wake audit is pending because the phone disconnected. Then update/open the normal debug app with `bash ./scripts/run-phone.sh`, preserving any user timer/data. Continue dial/presentation investigation and the real-player/Doze, volume/routes, overnight/OEM, API 37/16 KB, identity/signing gates. Phase 6 remains open.
