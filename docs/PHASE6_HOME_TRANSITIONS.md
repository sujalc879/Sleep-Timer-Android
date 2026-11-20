# Home transition state — November 20, 2026

The outgoing Home branch previously received the incoming mode's layout and live dial state. A manually advanced regression first observes 24:18 → 24:17, then fails because 24:17 disappears during the cancellation exit. The retained failing XML identifies the intermediate-transition assertion, rather than the preceding clock tick. This is a reproduced presentation bug; it is not proof of a frame-budget improvement.

Home now passes the branch layout through AnimatedContent, keyed by running/idle mode. Each branch/run reads live dial state while its identity matches, then retains its last successfully composed dial state for its exit. SideEffect records that state after composition. A same-mode restart resets the cache. One lifecycle-collected UI State supplies both the dial and a structurally equal clock-free layout projection, replacing two independently scheduled flows. Shared bounds, expressive motion, existing outgoing accessibility suppression, commands and playback behavior are preserved.

## Host verification

- The failing intermediate-transition regression passes after the change.
- All **227 current module tests pass**, including three new transition/restart tests and the updated real layout-projection clock isolation test; zero failures, errors or skips.
- All **16 image comparisons** against the existing 15 references pass. References and thresholds are unchanged.
- Formatting/ktlint, detekt, compilation, debug assembly, release APK/AAB, profiling APKs, nine artifact-verifier tests and artifact audit pass.
- The first full run exposed missing incremental KSP/Hilt generated activity classes. Cleaning only app build outputs and regenerating resolves both affected manifest tests. No test assertions or classpaths were weakened.
- The final required `verifyRelease :app:assembleBenchmarkRelease --continue` run **fails only at app debug/release lint**, each with 14 dependency-update errors. No lint baseline, suppression or analyzer-threshold change was added.

Lint now flags these pinned versions:

| Dependency | Pinned | Version reported by lint |
|---|---|---|
| Gradle | 9.9.1 | 9.9.2 |
| Android Gradle Plugin | 9.5.1 | 9.5.2 |
| Compose BOM | 2026.09.00 | 2026.11.01 |
| Material 3 | 1.5.0-rc01 | 1.5.0 |
| Lifecycle | 2.11.0 | 2.11.1 |
| Navigation 3 | 1.2.1 | 1.2.2 |
| Robolectric | 4.18 | 4.18.1 |

These are detector findings, not a completed compatibility review or upgrade. Keep dependencies fixed for the transition comparison; refresh them in a separate reviewed step and rerun all checks. No SDK, JDK, emulator or global tool was installed.

The candidate unsigned APK is 3,143,435 bytes; the AAB is 6,622,644 bytes. These are artifact sizes, not Play download estimates.

## Phone comparison

Fresh comparison-ffc967660e completes both five-iteration executions with the same unchanged runner; installed APK hashes and all ten retained traces are verified. The Moto / API 35 is connected and charging: baseline 66% / 39 → 38 °C, candidate 67% / 39 → 39 °C, thermal status 0 and benchmark thermal sleep 0. Measured results remain mixed; the frame budget stays open.

| Measured-window / control statistic | Baseline | Candidate |
|---|---:|---:|
| FrameTimeline records | 5,221 | 5,237 |
| Dropped Frame classifications | 14 | 9 |
| App Deadline Missed flags | 0 | 3 |
| Buffer Stuffing classifications | 5,087 | 5,172 |
| Maximum recomposition slice | 7.600 ms | 7.910 ms |
| Maximum measure/layout slice | 10.897 ms | 9.432 ms |
| Maximum draw slice | 5.751 ms | 5.542 ms |
| Maximum RenderThread postAndWait | 15.617 ms | 13.506 ms |
| XML opens | 5 (Tune only) | 5 (Tune only) |

Nested wall slices overlap and are not exclusive CPU time. Baseline drop events occur at 8.100–8.297 s; candidate events span 5.529–8.409 s. All three candidate app-deadline flags occur in iteration 002 at 8.387–8.409 s; one has an incomplete duration. Every trace has exactly one measured block, 8.880–8.999 s. This single pair supports keeping the presentation correction, not claiming an overall frame improvement or completed budget.

Whole-capture CPU P50/P99 is 13.284/20.683 → 11.586/18.556 ms; overrun P50/P99 is 5.021/15.291 → 1.513/14.209 ms, maximum 23.722 → 23.922 ms. Raw sample populations differ: 8,904 baseline samples (4,648/1,064/1,073/1,058/1,061) versus 5,336 candidate (1,058/1,062/1,079/1,067/1,070). The first baseline includes 30.154 s and 3,620 actual FrameTimeline records before measurement, while the first candidate has 31. Raw percentile changes cannot be attributed directly to the fix.

Trace health remains explicit: all ten report power_rail_empty_packet; baseline iteration 000 reports two sequence-packet-loss groups, baseline 004 a systrace parse failure, and candidate 002 one negative timestamp discarded. These findings limit completeness; they are not suppressed or assumed to lie outside measurement.

Attempt comparison-ed6637bfc5 failed in baseline setup while awaiting Stop after Start. Inspection subsequently found another app in front; no isolated timer service remained. Attempt comparison-ec7cc89783 failed before setup because ADB lost the phone. A single default-backend server refresh did not restore it; the owner reconnected USB before the successful pair. Both failed attempts are retained and do not count as performance results. Existing measured frame-budget and reliability gates remain open.

## Delivery and cleanup

The normal debug APK is updated without clearing data, installed checksum verified, and opened on idle Home with Start/Open settings present and Stop absent. Its SHA-256 is `3d70ba6c44eadb5ee342d5229db44f0a4b532597ba3b978371f9926496520538`. Both isolated packages, all three attempt directories and scoped processor/query files are absent. Inspection instrumentation is closed, no timer/test-player service remains, active wake locks are zero and ADB uses unforced NATIVE. Owner PHONE_COMMANDS.md is untouched; local.properties remains ignored/untracked. Working edits remain uncommitted.

## Evidence

Ignored local evidence is under `build/reports/release/home-transition-2026-11-20/`: pinned official Compose animation/runtime 1.12.1 source artifacts, the failing regression XML, source/APK hashes, before/after APKs, host XML/results, formatting/full-check logs, dependency lint reports and failed comparison metadata. Current test totals exclude archived historical XML.

The preserved baseline APK SHA-256 is `0df23cd898cade6a9f7148ffc56522f60839780555ffd1a76ff234a660748b9d`; the candidate is `c10bfa4b7669449c223ad7b24e8c445a0958fe99dd57d7efc15389278452c374`. Both use runner `0da33f28e7ecfd6efc26987363190d69d8b3dfa61ee5b85fa4e211c201340849`.

## Phone checks

Run `bash ./scripts/run-phone.sh` from the repository root. Start a timer, extend/reduce it, then Stop. The outgoing dial should retain its own countdown while returning to idle. Repeat Start → Stop → Start quickly; the current run and controls should remain correct. Stop cancels the timer and leaves playback running. Physical accessibility, real-player/Doze, safe volume restore, overnight/OEM, API 37, final profiles, signed-candidate and actual 16 KB checks remain pending.
