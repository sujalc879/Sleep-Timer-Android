# Phase 4 — Android surfaces

Updated November 7, 2026 (Asia/Kolkata). PROGRESS.md records observed host/device results. A procedure below is not a claimed pass unless the progress record says it was run.

Observed smoke check: current APK installed on Moto g54 5G / API 35; idle Home inspected; three repeated explicit Custom shortcut launches opened and dismissed the real sheet after the singleTop fix. No timer was left running. Native surface synchronization and the remaining procedures below are pending.

## Install

Connect and authorize USB debugging, then run from the project root:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$HOME/Library/Android/sdk/platform-tools:$PATH"
adb devices -l
./gradlew :app:installDebug
adb shell am start -W -n com.sujal.sleeptimer.debug/com.sujal.sleeptimer.MainActivity
```

Preserve any active timer before installing. Stop cancels the timer and leaves playback running; expiry performs the stop pipeline.

## Notification and promotion

1. Start 30 minutes from Home. On Android 15, expect an ordinary silent ongoing notification with end time, native countdown, remaining progress, configured fade text, Extend and Stop. There is no promoted status chip on this version.
2. Change Extend by/Fade-out while idle, start again and check the action/text. Extend from the notification and confirm Home's deadline and the countdown agree. Stop must cancel without pausing the player.
3. Dismiss the notification where Android permits it. Minute refreshes and controller recreation must respect that dismissal. Extending, reducing or changing a visible timer setting may post a changed notification. A stale dismissal must not hide a newer revision.
4. Deny notifications or disable the channel: the foreground timer must still run, with readiness reflecting missing visibility. No permission dialog should block the timer's start.
5. On Android 16 QPR1 (36.1) or Android 17, enable app promotion in Android settings if available. An eligible run requests promotion with standard ProgressStyle and a tracker dot. The system chooses whether to promote it. Disable promotion and verify the ordinary fallback. The chip uses the countdown `when`; the app does not post every second.

API 36.1/37 promotion and actual chip appearance remain device/emulator checks. Host coverage uses the existing JDK 17 and earlier Robolectric runtimes.

## Tile

Settings → Quick Settings tile → Add, or edit Quick Settings manually below API 33. Check Off, active minute count, and Less than 1 min. Counts use whole minutes like the notification. State changes are observed immediately while listening; local subtitle ticks occur only while listening.

Tap while inactive to start the remembered duration; tap while active to cancel it. Long-press must open Sleep timer. Repeat from the lock screen without unlocking. Rapid taps must not replace an already-started run with a stale inactive start. Check added/removed membership in Settings.

On API 37, the inactive tile uses a resumed transparent Activity before starting the FGS, preserving a user-interaction path for audio hardening. Verify this path with `adb shell cmd audio set-enable-hardening throw` on an Android 17 emulator, including focus and volume behavior, then restore the emulator's original hardening setting. No hardening pass is inferred from compiling the Activity.

## Both widgets

Settings → Home-screen widget → Add now offers Dial widget and Presets widget. Finish the launcher's pin flow for each.

- Dial: idle shows a ring, remembered minutes and Tap to start. Tap starts that duration. Running shows a native host countdown and Running; tapping opens the app without stopping/replacing it. A stale idle click must not replace a run that has already started.
- Presets: 15/30/45/60 are connected choices; the current duration is selected. Tap starts/restarts that duration. Values, colors and selection must agree with Home after an engine change.
- Resize both through supported sizes, including the narrowest size and a large home-screen grid. Repeat with 200% font, TalkBack, RTL, Light/Dark/System and Dynamic color off/on. The counter fits its ring; preset targets retain their height and complete spoken minute descriptions.
- Open the Android 15 widget picker and inspect generated previews. Older Android uses the native-rendered light/dark static PNG fallbacks. Generated-preview rate limiting must leave a usable fallback.
- The host chronometer counts seconds without app refreshes. Ring/projection pushes occur on meaningful changes and minute ticks. Provider updatePeriodMillis is zero. Check screen-off → screen-on, process restoration and locale/theme changes; old-process widget state must not override a fresh engine state.

## Shortcuts, Undo and icon

Long-press the launcher icon: Start 30 min, Start 60 min and Custom time must appear. Shortcuts target the current debug/release application ID, not a fixed package variant.

Custom time opens Home's real duration sheet. The two fixed starts use the visible Activity gateway. While a normal timer is active, launch a fixed shortcut and tap Undo: the previous future deadline returns under a new run ID. Undo must be unavailable after the previous deadline expires, another adjustment changes the replacement, or the process dies. A stale action must never cancel or restore over another run. Undo does not start playback.

Check the adaptive moon icon under circular/squircle masks and Android 13+ themed icons. Host tests verify the adaptive/monochrome layers and framework parsing of the static shortcut intents.

## Synchronization matrix — brief 6.I

For each entry point, observe Home, notification, active tile and both pinned widgets together. Record timestamps/video where possible and require state changes within one second on the tested device. Native Glance/launcher scheduling latency must be measured, not inferred from an enqueue call.

| Trigger | Check |
|---|---|
| Home/shortcut/widget/tile start | Same active run/deadline, correct preset selection and actions |
| Extend/reduce/shake | Same new deadline; old run/generation actions ignored |
| Cancel/expiry/deferred call | Consistent phase and eventual idle; no accidental playback start |
| Process death/restart | Restored snapshot before publication; no stale widget projection wins |
| Time/zone/locale change | Elapsed countdown unchanged; displayed wall end time/localized text refresh |
| Theme/dynamic-color change | Both widgets and app use the selected scheme; count/ring stay coherent |
| Clear app data, then reopen | Default idle state; no previous run resurrected; tile/widget view refresh |

Do not clear data or force-stop a timer you are relying on. Force-stop and reboot behavior must also follow PHASE1_TESTING.md. The Spotify forced-Doze timing, safe physical fade/restore, real shake traces, DND/Bedtime and OEM survival gates remain pending unless separately measured.
