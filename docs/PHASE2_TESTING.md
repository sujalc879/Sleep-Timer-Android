# Phase 2 Home testing

Updated 2026-11-04, Asia/Kolkata.

## Install

Connect the phone with USB debugging authorized, then run from the repository root:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$HOME/Library/Android/sdk/platform-tools:$PATH"
adb devices -l
./gradlew :app:installDebug
adb shell am start -W -n com.sujal.sleeptimer.debug/com.sujal.sleeptimer.MainActivity
```

Home now has working controls; the previous debug-only start command is no longer required. The Settings destination still has its Foundation app bar. Its complete rows arrive in Phase 3.

## Home checks

1. Tap each preset. Only the matching duration should be selected. Drag the circular thumb; each minute should tick, with no wrap from 60 to 1 across twelve o'clock.
2. Choose Custom. Try zero, 12 h 1 min and an empty field; Start must be disabled. Try 90 min, 2 h and 3 h quick choices. Starting a valid duration must run that actual duration, with hours shown for long timers.
3. Tap Start. The settings action disappears, the remaining-time wave replaces the dial, and Stop/−5/+Extend appear. The settings-derived pipeline excludes unavailable screen locking. The shake card stays hidden until real shake monitoring lands in Phase 3.
4. With more than five minutes remaining, −5 min reduces the deadline. At five minutes or less it is disabled. Separate +10 min taps add separately, capped at 12 hours remaining.
5. Start music, start a long timer, then tap Stop promptly. **Stop cancels the timer and leaves music running.** It does not pause immediately. This checks cancellation without waiting for expiry.
6. Tap the idle 10 s test chip with music playing. It runs the real stop pipeline. If nothing is playing, an explanation appears first. Result copy distinguishes stopped, local-only confirmation, no playback and failure. A completed test stays in readiness after an ordinary run; cancelling a test does not replace it.
7. Lock the screen manually for an expiry test. Use the existing [Phase 1 test procedure](PHASE1_TESTING.md) for the two-minute Spotify/forced-Doze timing gate; a Home UI test is not reliability evidence.
8. Rotate, use a tablet/landscape window, and test a 320 dp window at 200% font scale and RTL. Content must scroll to reveal controls without truncating copy. On an expanded window, dial and actions use two columns.
9. Enable TalkBack manually. The idle dial must expose its duration, a 1–60 minute range and increment/decrement actions. With a keyboard, focus it and use arrows, Home and End. The running countdown must not announce every second. Physical TalkBack verification remains pending.

## Readiness and Nightly

Tap the readiness chip to inspect actual exact-alarm, notification, battery and notification-access states. Each access row opens system settings. No battery-exemption permission is declared. The complete permission explanations, OEM guidance and Settings flow remain Phase 3.

Nightly opens a functional schedule sheet. Choose a time (clock or keyboard), repeat days and duration, enable it and save. Enabling requires Alarms & reminders access; return from the system screen and save again. Notification access is recommended for background media-session pause.

Only one Nightly occurrence starts per local date. An existing timer is left running. Cancelling tonight's scheduled run must not let that same occurrence restart. Repeat days, configuration changes, time/zone changes, reboot and app update reschedule it. DST gaps move forward by the gap; overlaps use the earlier offset once. Kolkata, London, New York and Lord Howe math is host-tested; actual background delivery is not yet device-verified. Android 17 scheduled starts have no assumed WIU capability.

## Automated evidence

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug spotlessCheck detekt :feature:home:verifyRoborazziDebug
```

Observed: **150 passed tests, no failures/errors/skips**, with the full command passing. The three committed native-rendered baselines cover idle dark/light and configured running dark at 390 dp. The configured screenshot explicitly enables shake/screen-lock presentation; production Home checks actual capabilities and currently hides those future features. Baselines were visually reviewed against `/design` and verified against a fresh rendering. They are regression images, not a claim of pixel equality to the supplied mockups.

Home tests cover preset selection, keyboard and TalkBack semantic range actions (including NaN rejection), thumb dragging, Start/Stop callbacks, −5 cutoff, Custom validation, narrow 200%-font RTL scrolling and expanded layout. Separate tests cover bounded drag math, locale digits, duration bounds, DST, durable selection, Nightly deduplication and persistent last-test evidence.

The final verified Phase 2 APK installed and opened successfully on the connected Moto g54 5G / Android 15 after an idle-state check. Idle Home and dynamic colors were visually inspected. No scripted native Start/Stop, Spotify expiry, Doze, actual volume restoration or physical TalkBack pass is claimed.
