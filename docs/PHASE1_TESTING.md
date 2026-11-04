# Phase 1 phone testing

Updated 2026-11-04, Asia/Kolkata. These are instructions, not recorded device passes. The phone was disconnected at the latest ADB check. Home still has Foundation app bars; timer controls arrive in Phase 2. The debug Activity commands below exercise the real engine and service. Release builds ignore them.

## Install and open

Enable Developer options → USB debugging, connect a data-capable USB cable, and accept the authorization prompt. From the repository root:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$HOME/Library/Android/sdk/platform-tools:$PATH"
adb devices -l
./gradlew :app:installDebug :tools:testplayer:installDebug
adb shell am start -W -n com.sujal.sleeptimer.debug/com.sujal.sleeptimer.MainActivity
```

`adb devices` must list the phone as `device`. `unauthorized` means accept the prompt on the phone. An empty list means installation cannot proceed. No emulator is required.

In Android Settings → Apps → Sleep timer, allow notifications if you want to see the timer and test results. The contextual permission UI arrives in Phase 3; it is not implemented yet. Do not grant permissions to an unrelated app.

## Quick test with your player

Start music in Spotify or another player, then run:

```sh
adb shell am start -W -n com.sujal.sleeptimer.debug/com.sujal.sleeptimer.MainActivity -a com.sujal.sleeptimer.debug.TEST
```

The real timer expires after 10 seconds. Music should stop. This does not prove overnight/Doze reliability. A result can take longer while the stop is verified; a screen-off run includes a 30-second resume watchdog. A silent initial run reports “Nothing was playing” rather than a successful player test. Without notification access, a stop result verifies local playback only.

For a two-minute timer:

```sh
adb shell am start -W -n com.sujal.sleeptimer.debug/com.sujal.sleeptimer.MainActivity -a com.sujal.sleeptimer.debug.START --el duration_millis 120000
```

Lock the phone manually after starting. The notification's **Stop** action cancels the timer and leaves music playing. **+10 min** extends the current run; separate taps add separately. An old notification must not affect a replacement run. Cancellation during an eligible fade may gently recover its original volume.

## Controlled local test player

Use this when you do not want the test to pause personal listening. Stop other media first. The test player loops a bundled local tone with silent player output by default; it never changes global music volume.

```sh
adb shell am start -W -n com.sujal.sleeptimer.testplayer/.TestPlayerActivity -a com.sujal.sleeptimer.testplayer.PLAY --es mode GOOD --ez silent true
adb shell am start -W -n com.sujal.sleeptimer.debug/com.sujal.sleeptimer.MainActivity -a com.sujal.sleeptimer.debug.TEST
```

Repeat with `--es mode IGNORE_FOCUS` to exercise a player that requires a pause command. `AUTO_RESUME` pauses on focus loss and resumes once after one second; test it with the screen off so the watchdog can pause it again. These modes exercise actual playback behavior, but the exact method used depends on available session access and platform observation.

To stop and release the test player:

```sh
adb shell am start -W -n com.sujal.sleeptimer.testplayer/.TestPlayerActivity -a com.sujal.sleeptimer.testplayer.STOP
```

## Forced Doze and timing evidence

This changes the phone's simulated charging/idle state temporarily. Use a controlled player, save the timer's deadline, and always restore the system afterward. Grant **Alarms & reminders** to Sleep timer in system special access to exercise the exact-alarm path; with it off, the engine uses the full-run wake-lock fallback. Do not revoke this access during an unrelated active run: Android can stop the app and cancel its alarms.

Start the two-minute timer above, then inspect its durable state:

```sh
adb shell run-as com.sujal.sleeptimer.debug cat files/datastore/timer.json
adb shell dumpsys activity services com.sujal.sleeptimer.debug
adb shell dumpsys alarm
```

Record `endElapsed`, run ID and settings. Lock the screen, then:

```sh
adb shell dumpsys battery unplug
adb shell dumpsys deviceidle force-idle
```

After expiry and watchdog/cleanup, inspect the snapshot and sanitized logs:

```sh
adb shell run-as com.sujal.sleeptimer.debug cat files/datastore/timer.json
adb logcat -d -s SleepTimer SleepTimerPlayer
adb shell dumpsys activity services com.sujal.sleeptimer.debug
```

Compare `lastResult.firstAttemptElapsed` with the saved `endElapsed`. `completedElapsed` includes verification, watchdog and restoration; it is not the moment playback first stopped. For Spotify, also verify cessation directly and record route, access permissions and exact-alarm state. A target within one second is unverified until measured on the device.

Always clean up, including after a failed test:

```sh
adb shell dumpsys deviceidle unforce
adb shell dumpsys battery reset
adb shell am start -W -n com.sujal.sleeptimer.testplayer/.TestPlayerActivity -a com.sujal.sleeptimer.testplayer.STOP
```

## Recovery checks still requiring runtime evidence

- While a two-minute run is active, terminate only this debug process with a suitable process-death method, then verify sticky service recreation, unchanged elapsed deadline and alarm identity. `am kill` may leave a foreground-service process alive; confirm actual death rather than counting command success as proof.
- Reboot during a run: the restored run must be interrupted/discarded because its boot count changed. Nightly scheduling is Phase 3.
- Force-stop the app, then reopen it: Android 15 startup history should identify the stopped package and preserve an interrupted result, without unexpectedly pausing current playback. Access to historical evidence varies on older versions.
- Update the installed debug APK during a run; verify reconciliation. Test both late-recovery policies: screen off and ≤30 minutes late may stop; screen on or >30 minutes late must interrupt instead.
- Test notifications denied, exact alarms denied, battery saver, restricted bucket, low memory, ringing calls and alarms, repeated extensions/cancellations, and stale actions. Host tests cover the reducer/adapters, not OS survival.
- Android 17 hardening and API 26 compatibility need approved runtime environments. No emulator or replacement JDK was installed.

## Current fade limitation

Fade/restore are deliberately skipped when route or remote-playback safety cannot be established. The native backend currently requires API 33+, one connected output agreeing with the media-route prediction, and notification-listener access. Ordinary speaker/Bluetooth configurations may therefore stop without fading. Never turn up the shared stream to compensate during a test. Host tests prove fader safety under controlled observations; they do not prove universal fade eligibility or physical hearing-safety behavior.

Screen-off admin, shake, permission explanations, settings controls and full test-result UI are Phase 3. Promoted notifications, tiles, widgets and shortcuts are Phase 4. Do not interpret the Phase 1 harness as those completed features.
