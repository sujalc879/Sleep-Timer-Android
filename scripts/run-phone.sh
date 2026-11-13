#!/bin/bash
set -euo pipefail

repo_dir="$(cd "$(dirname "$0")/.." && pwd)"
sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -f "$repo_dir/local.properties" ]]; then
    while IFS= read -r line; do
        case "$line" in sdk.dir=*) sdk_dir="${line#sdk.dir=}" ;; esac
    done < "$repo_dir/local.properties"
fi
adb_path="$sdk_dir/platform-tools/adb"
if [[ ! -x "$adb_path" ]]; then
    echo "Android SDK not found. Set sdk.dir in ignored local.properties or ANDROID_HOME." >&2
    exit 1
fi

serials=()
listing="$("$adb_path" devices)"
while read -r serial state rest; do
    [[ -z "$serial" || "$serial" == "List" || "$serial" == emulator-* ]] && continue
    serials+=("$serial")
done <<< "$listing"

selected="${ANDROID_SERIAL:-}"
if [[ -z "$selected" ]]; then
    if [[ ${#serials[@]} -eq 0 ]]; then
        echo "No phone detected. Connect an unlocked phone with a USB data cable," >&2
        echo "enable Developer options > USB debugging, and accept Allow USB debugging." >&2
        echo "Then rerun: ./scripts/run-phone.sh" >&2
        exit 1
    fi
    if [[ ${#serials[@]} -ne 1 ]]; then
        echo "Multiple phones detected. Choose one: ANDROID_SERIAL=SERIAL ./scripts/run-phone.sh" >&2
        "$adb_path" devices -l
        exit 1
    fi
    selected="${serials[0]}"
fi
state="$("$adb_path" -s "$selected" get-state 2>/dev/null || true)"
if [[ "$state" != "device" ]]; then
    echo "Phone is not authorized/online. Unlock it, accept USB debugging, and reconnect if needed." >&2
    "$adb_path" devices -l
    exit 1
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
    mac_jdk="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
    if [[ -x "$mac_jdk/bin/java" ]]; then
        export JAVA_HOME="$mac_jdk"
    elif [[ -x /usr/libexec/java_home ]]; then
        export JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
    fi
fi
if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/java" ]]; then
    echo "Set JAVA_HOME to an existing compatible JDK (the project uses JDK 17)." >&2
    exit 1
fi

cd "$repo_dir"
./gradlew :app:assembleDebug
"$adb_path" -s "$selected" install -r "$repo_dir/app/build/outputs/apk/debug/app-debug.apk"
"$adb_path" -s "$selected" shell am start -W -n com.sujal.sleeptimer.debug/com.sujal.sleeptimer.MainActivity
