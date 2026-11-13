# Play submission preparation

Prepared November 13, 2026 (Asia/Kolkata). **Draft; not submitted or approved.** Phase 6 remains open until the device, performance, profile and signing gates in [RELEASE_TESTING.md](RELEASE_TESTING.md) and the [manual matrix](MANUAL_TEST_PLAN.md) have evidence.

## Owner inputs before any upload

| Input | Current state |
|---|---|
| Permanent Play application ID | Candidate `com.sujal.sleeptimer`; owner must confirm before first publication |
| Publisher/legal identity and support contact | Not provided; required for the public listing/privacy policy |
| Public privacy policy URL | Not provided; publish the reviewed policy under the confirmed publisher identity |
| Play Console account, app record, upload certificate | Not configured by this task |
| Upload keystore/passwords/alias | External inputs only; no key created or committed |
| Distribution version | Development default 0.1.0 / code 1; choose and reserve a new code before upload |
| Device QA/profile/performance results | Pending; do not describe host compilation as a release smoke test |
| FGS declaration video and review | Script below; recording/upload/reviewer approval pending |

## Build, retention and signing

`release` uses R8 optimization/obfuscation and resource shrinking. AGP's default full mode is retained; no compatibility-mode flag or blanket keep is added. Hilt-generated references, explicit serializers, library consumer rules and manifest-generated rules supply retention. Keep `app/build/outputs/mapping/release/` with each shipped version, especially mapping.txt and configuration.txt. Building successfully is not a runtime test of Hilt, Glance callbacks, DataStore/serialization or all system receivers; exercise these in the optimized benchmark/signed release smoke run.

`verifyRelease` builds/audits an **unsigned development candidate** without requiring a connected device. `verifyDistributionArtifacts` additionally requires app-generated profile source and externally supplied signing. Neither task uploads anything or replaces manual QA. See [R8 guidance](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization).

Supply all four environment inputs from a local secure environment or CI secret store:

- `SLEEP_TIMER_STORE_FILE`: absolute path to the existing upload keystore.
- `SLEEP_TIMER_STORE_PASSWORD`: keystore password.
- `SLEEP_TIMER_KEY_ALIAS`: upload-key alias.
- `SLEEP_TIMER_KEY_PASSWORD`: key password.

A partial configuration fails immediately. No inputs means unsigned release; production release never falls back to the debug key. Do not put passwords in command arguments, source files, shell history, logs or BuildConfig. Keystore patterns are ignored. Protect local/CI caches and retain the upload certificate fingerprint separately. Enroll in Play App Signing with the confirmed upload certificate; an AAB must be upload-key signed. [Signing reference](https://developer.android.com/studio/publish/app-signing).

After those inputs and real profile source exist:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew verifyDistributionArtifacts -PsleepTimer.versionCode=2 -PsleepTimer.versionName=0.1.0
```

Code 2 is an example, not reserved in Play. Allocate strictly increasing positive codes; do not reuse an uploaded code. Version names follow major.minor.patch, optionally with a prerelease suffix. Review [CHANGELOG.md](../CHANGELOG.md), archive artifact checksums, mapping, generated profile, dependency inventory and QA evidence under the exact source commit. Upload only after owner review.

## Foreground service declaration

Manifest type: `specialUse`, with `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE`. The subtype property explains a user-started elapsed-deadline sleep timer pausing external media.

Proposed declaration text:

> Sleep timer provides a user-configured countdown that pauses media playback at its deadline. The user starts it in the app or through a visible Android control; a separately enabled Nightly schedule is an explicit recurring user configuration. The ongoing notification shows its end time and provides extension and cancellation. During a run, the service owns deadline reconciliation, optional shake extension, warning/fade/stop safety and bounded cleanup. Delaying this work could leave media playing while the user sleeps. The service ends after cancellation or the bounded completion pipeline. The app does not play its own continuous media, record audio, track location or transfer data.

Attach the exact subtype and describe Nightly honestly, including its system start restrictions. Explain why mediaPlayback is not declared: this app controls other players and does not operate a media playback session of its own. Keep the elapsed alarm/wake window rationale and startup-path evidence available. `specialUse` is a review request, not a guarantee of Play eligibility. [Android type guidance](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use), [Play declaration requirements](https://support.google.com/googleplay/android-developer/answer/13392821).

Video script, recorded on the actual candidate after QA:

1. Show the app version/device and a player already playing at a comfortable level. Start a short normal timer from Home; show the immediate ongoing notification and end time.
2. Background the app, turn the screen off, then show the visible notification/lock-screen surface. Explain that shake support depends on sensor hardware and timing window; demonstrate only verified behavior.
3. Show extension updating the same run; demonstrate Stop **cancels the timer and leaves playback running**.
4. Start a new run, allow expiry and show the real player's paused state and safe volume outcome. Use an eligible route for fade; do not splice an untested fade into the recording.
5. Show the service/notification ending after completion/watchdog cleanup. Demonstrate the 10-second test result separately; it does not lock the screen.
6. Show Nightly as an optional, separately enabled schedule and its notification-access recommendation. Attach API 37 background/WIU evidence before making an Android 17 reliability claim.

## Exact alarms and other permissions

Proposed `SCHEDULE_EXACT_ALARM` rationale:

> Precisely timed playback stopping is the app's core user-requested function. One elapsed-time wake alarm opens the final warning/fade/stop window; one later backup protects an unfinished run. Extensions/settings changes replace those alarms. The user can decline special access and still start a manual timer, with a visible readiness warning and bounded higher-battery wake protection. Nightly requires exact-alarm access. The app requests access in context and does not declare USE_EXACT_ALARM.

Permission grant/revocation and OEM restrictions still require the manual cases. Do not claim execution survives force-stop or all permission-revocation process kills. [Alarm access reference](https://developer.android.com/develop/background-work/services/alarms).

`POST_NOTIFICATIONS` is requested after starting an eligible foreground run, with countdown/actions/results. Promotion is optional and system controlled. The final release has no INTERNET, AccessibilityService, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, recording, location or phone-state permission. ACCESS_NETWORK_STATE comes from Glance infrastructure; timer timing does not use WorkManager or network. Inspect the final merged artifact on every candidate.

## Prominent disclosures and uninstall path

Keep these in-context disclosures in the shipped UI and demonstrate consent before enabling either optional feature:

- **Screen locking:** “After the timer locks your phone, the next unlock needs your PIN, pattern or password instead of fingerprint or face.” Explain the force-lock-only administrator, then show Android's activation screen. Declining leaves the setting off. Settings → Manage screen-lock access provides deactivation before uninstalling. Test external revocation, work profiles and failed activation; no AccessibilityService substitute.
- **Notification access:** “Optional access lets Sleep timer pause active media sessions directly, including supported cast players. It does not read or store notification content.” Explain that focus/pause-key fallback remains available and some players ignore it. Open Android's listener settings only after the explanation; access is optional and revocable.
- **Diagnostics:** Settings explains local timer events without player names/media details. Sharing opens the system chooser; the user chooses a destination. The report is bounded and from the current process, not a durable history.

These describe the present implementation. Verify all consent/deactivation paths on the optimized candidate rather than relying solely on host tests.

## Privacy policy text for owner review

Sleep timer works on your device. It has no accounts, ads, analytics, tracking or developer-operated network service. The app does not collect or sell personal data, and its production package has no Internet permission.

Your settings are stored locally. Android may back up settings through your configured system backup service; active timer snapshots are excluded from that backup. Clearing app storage removes local settings and timer state. If optional screen-lock administration is active, deactivate it in Settings before uninstalling.

Optional notification access is used locally to pause active media sessions. The app does not read or store notification message content or include player names or media details in its diagnostic report. Optional accelerometer processing detects a shake during an active timer; sensor traces are not uploaded or retained by the app.

Local diagnostics contain a bounded set of internal event codes and relative times from the current app process. They leave the app only when you choose Share diagnostics and select a destination in Android's Sharesheet. Review the report before sending; the selected recipient handles the text under its own policies. External battery-guide links open your browser.

Crash, ANR and device-health information supplied by Google Play/Android may be available to the publisher through Play Console under those platform services' policies. The app includes no third-party crash-reporting SDK. Before publishing this policy, the owner must add the confirmed publisher identity, support contact, effective date and public URL; those have not been invented here.

## Data safety worksheet

Proposed app answers are **no developer data collection**, **no automatic data sharing**, **no ads**, **no account creation/login**. Local-only settings/sensor/media-session processing is not a developer upload. The chooser is an explicit user action, not background telemetry. Review the current user-initiated sharing exception and every final runtime dependency before submitting; declarations must describe the actual distributed artifact, backup behavior and support workflow. [Data safety definitions](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en-GB).

The owner must still complete the current Console questionnaire, content rating, app-access declaration and required public contact fields. No Console form has been submitted.

## Target API and 16 KB compatibility

Candidate minSdk 26, compile/target 37. The verified current Play requirement for new mobile submissions is API 36 or higher; recheck immediately before uploading. Compiling target 37 does not establish Android 17 runtime correctness. [Current target requirement](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en-en).

`verifyReleaseArtifacts` inspects every shipped native binary in APK/AAB and the uncompressed APK native offsets. The two AndroidX libraries across four ABIs pass the initial binary audit. Repeat on the final artifacts. The bundle itself is not installed: validate Play/bundletool-generated split APK ZIP alignment and run on a real/emulated 16 KB image (`adb shell getconf PAGE_SIZE` must report 16384). Those runtime/split tests remain pending. [16 KB guidance](https://developer.android.com/guide/practices/page-sizes).

## Listing and screenshots

Proposed title: **Sleep timer**. Proposed short description: **Set a sleep timer for your media, with optional fade and shake to extend.** Confirm that copy against device results; fade is deliberately skipped on unsupported/ambiguous routes and API 26–32. Do not promise universal player/cast, OEM, overnight or Android 17 reliability before it is measured.

Use the existing native screenshot tests to review Home idle/running and Settings in light/dark and adaptive text layouts. Final listing captures should come from the reviewed candidate, showing genuine configured states. Capture eligible notification fallback/promotion, both widgets and a tile on the platforms that actually support them. Use wallpaper-derived colors as examples; do not imply a purple brand palette. Crop no disclosure, unsupported state or failed result out of a claim. No real player metadata, account notifications or personal wallpaper in store images. Tablet/foldable images require corresponding device validation; pseudo-locales are QA assets, not localized store translations.

## Staged rollout and recovery

Internal testing precedes closed testing, then production 1% → 10% → 50% → 100%. Proposed internal gates: at least 24 h at 1%, 48 h at 10% and 72 h at 50%, with enough actual usage to assess the release; elapsed time alone never authorizes expansion. Owner approves each increase.

Hold expansion or halt rollout for any unsafe volume restoration, failed reproducible deadline test, crash/ANR/wake-lock regression or unresolved critical manual case. Proposed stricter internal vitals targets are user-perceived crash rate below 0.1% and ANR rate below 0.05%; these are project targets, not quoted Play thresholds. Inspect device/OS clusters and excessive partial wake locks in Android vitals, without adding an analytics SDK. Preserve the previous known-good source/signing artifacts; a corrective upload uses a higher code and the same established identity. Do not assume a store downgrade restores user state safely.

All rollout, listing, policy hosting, signing and upload actions remain owner-controlled future work.
