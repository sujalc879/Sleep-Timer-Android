# Phase 3 — Settings and features

Updated November 5, 2026 (Asia/Kolkata). Host results are recorded in PROGRESS.md. ADB reported no connected devices during this phase; none of the physical checks below is a claimed pass.

## Install and open

From the project root, connect the phone, enable USB debugging and accept its authorization prompt:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$HOME/Library/Android/sdk/platform-tools:$PATH"
adb devices -l
./gradlew :app:installDebug
adb shell am start -W -n com.sujal.sleeptimer.debug/com.sujal.sleeptimer.MainActivity
```

A line ending in `device` must appear before installDebug can install. The build and unit checks do not need a device. No emulator, SDK component, JDK or global CLI installation is required by this task.

## Settings walkthrough

1. Open Settings with the tune icon while idle. Check the grouped cards against design/03-settings.png. Scroll to the end, back out and reopen. Repeat in landscape and with the system font at 200%; labels and controls must remain reachable.
2. Move Fade-out to Off, 5 s and 60 s. Toggle Restore volume. Reopen the app and confirm persistence. These settings use the existing guarded pipeline; uncertain routing still skips native fade/restore rather than guessing another app's output.
3. Choose Extend by → 5, 10 and 30 minutes. Start a timer; the Home extension button, shake text and notification action must agree. Stop cancels the timer and leaves playback running.
4. Choose each One-minute warning option. Soft/Strong preview a vibration; Off does not vibrate. Set Soft afterward if that is your preference.
5. Open Nightly schedule. Configure time, duration and repeat days. Enabling it requires exact-alarm access and uses the real scheduler from Phase 2. The Settings switch opens this configuration sheet rather than silently enabling an unreviewed schedule. Confirm the saved time in its supporting text.
6. Use Quick Settings tile → Add. Accept the Android tile prompt if desired; below API 33 the app gives edit-tile instructions. The row shows Added after the system reports membership. Tap the tile to start the last duration; tap it again to cancel. Long-press opens the app. Test lock-screen delivery separately; Android 17 WIU remains unverified.
7. Use Home-screen widget → Add and complete the launcher's pin prompt. The supporting Glance widget starts the last duration while idle, and opens the app while running. Its native countdown advances without per-second app updates. The full ring, responsive previews and presets widget remain Phase 4.
8. Toggle Dynamic color, and choose Light/Dark/System. Reopen the app in each theme. Android 12+ uses the persisted application night-mode override; System clears it. On Android 8–11, the starting window deliberately uses a dark surface until preferences can draw; check that explicit Dark never flashes light and Light transitions to its chosen screen.
9. Set Dim screen while running on, then start a timer. Home must dim before its first displayed running frame; app switching and stopping must restore the previous window override. It must not change global brightness or keep the screen awake. Disable it and repeat.
10. Open Battery optimization: read the detected manufacturer guidance, open system settings, and optionally open its browser guide. No battery exemption is requested automatically. Return and check the actual status. Open Notification access, read the optional-access disclosure, then grant/revoke it in Android and confirm the row updates.

## Real 10-second test and readiness

Start media in a player and return to Settings → Run a 10-second test. It returns to Home and uses the real foreground service and pipeline. Confirm the result, including the successful method. Repeat without playing anything: the app must explain that no player was verified, rather than claim success. A running timer disables the Settings test.

On a fresh installation without exact access, starting a timer must happen first, then the notification request, then the exact-alarm explanation. Declining exact access must leave the timer running under the bounded wake-lock fallback. Open readiness to revisit access and the durable last-test result. Grant and revoke exact access during a run and check reconciliation; a request is never a prerequisite for starting.

Notification access is optional, but remote/cast verification and conservative fade eligibility depend on observable media sessions. Record local-only results as local-only; do not infer a cast stop from local audio inactivity.

## Screen-off consent and removal

Turn screen off defaults to off. Enabling it explains that the next unlock after a timer lock needs the PIN, pattern or password rather than biometrics, then opens Android's device-admin activation screen. Cancelling must leave the preference off. If you choose to activate it, inspect that only screen locking is requested.

Run a one-minute timer while interactive. After playback is stopped and the watchdog/restore work finishes, the screen may lock. Repeat a 10-second test: it must never lock. Repeat with a call or ringing alarm and with the screen already off. Revoke admin access externally, return to the app and check that the preference is off with a readiness explanation. Disable the setting and use Deactivate administrator; uninstall must then be possible. Work-profile behavior remains a separate device check.

## Shake, warning and edge-case evidence

The host shake traces are synthetic. They establish alternating-peak/window/cooldown rejection, not real bed-motion tuning. Record real traces locally on representative devices before release; no sensor stream is collected or exported by this app.

| Brief area | Procedure / evidence still needed on a device |
|---|---|
| 6.D Volume | Follow PHASE1_TESTING.md: user volume changes, route changes, fixed-volume and hearing-safety caps, process death during fade. Existing host playback tests guard writes/restores; physical behavior remains pending. |
| 6.E Calls and alarms | Start a call/VoIP session or ringing wake-up alarm before the deadline and during fade. The app must defer without stealing focus; end the interruption and verify stop. Check the bounded deferral wake lease. |
| 6.E Vibration modes | Repeat a two-minute run with Soft/Strong under normal, Silent, Vibrate, DND and Bedtime; note whether Android's alarm-vibration policy permits it. Restore your original system modes. The app uses ALARM usage with no bypass flags and respects system haptics. No physical mode pass is claimed. |
| 6.F Screen lock | Consent cancelled/granted, admin revoked, already off, call active, test run, work profile, deactivate/uninstall — procedures above. |
| 6.G Shake | Shake three alternating peaks; check one extension, two-pulse confirmation and three-second cooldown. Shake during fade to exercise guarded ramp recovery; after stop nothing must happen. Roll over in bed without a deliberate shake and record false positives. |
| 6.G Sensor delivery | On a wake-up accelerometer device test screen-off delivery through a long run. On an ordinary accelerometer device check screen-on delivery and screen-off delivery during the last five minutes; early screen-off shake is intentionally unavailable. Check registration and wake release after cancel, expiry and service destruction. |
| 6.H Permissions | Deny/revoke notifications and exact alarms, disable a notification channel, revoke notification access during a run, select Restricted battery, and return to readiness. No critical state should be presented as fixed solely because a settings screen was opened. |

The ordinary-accelerometer fallback opens the final alarm window five minutes before expiry (plus safety) and uses timed wake leases. Wake-up sensor delivery can also cost power; overnight/OEM measurements are pending. With exact access denied, the existing whole-run lease is a deliberate reliability/battery trade-off. Do not describe these as measured battery results.

The Phase 1 Spotify screen-off/forced-Doze timing gate remains pending. Android 17 hardening, system-surface polish, release budgets and broad OEM testing are later gates; this Settings phase does not satisfy them automatically.
