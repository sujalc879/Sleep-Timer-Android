# Run Sleep timer on your phone

Connect your unlocked phone using a USB data cable. Keep USB debugging enabled and accept any **Allow USB debugging** prompt.

## Normal use: build, install and open

Run these commands in your Mac's Terminal:

```sh
cd /Users/sujalsunilchaudhary/coding/projects/sleepTimer
bash ./scripts/run-phone.sh
```

The script selects the existing SDK and JDK, builds the debug APK, installs it and opens the app.

## Only if the phone is not detected

Restart ADB from your Mac's Terminal:

```sh
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
adb kill-server
adb start-server
adb devices -l
```

- If the phone shows `unauthorized`, unlock it and accept the debugging prompt.
- If the list is empty, reconnect directly to the Mac, bypassing any USB hub. Try another USB data cable if needed.
- Once the phone appears with status `device`, run the normal-use commands above again.

You do not need to restart ADB every time.

## Reopen the installed app

Tap **Sleep timer** in your phone's app drawer. Rebuilding is only needed when you want to install your latest code changes.
