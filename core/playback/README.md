# Playback safety and platform limits

`AndroidPlaybackBackend` uses only public AudioManager and MediaSession APIs. `PlaybackCoordinator` delegates to a cancellable stop pipeline and volume controller using the engine's elapsed clock and effect generation. The engine must hold the scoped wake lease while these effects use coroutine delays to wait for elapsed deadlines.

The optional `MediaSessionBridge` is a notification-listener binding used solely to obtain active media controllers. It does not inspect or persist notification content. Without access, local focus/PAUSE stopping still works; unknown cast state is never reported as verified remote cessation. An initially silent run reports no known local playback rather than claiming a successful cast stop.

## Deliberately conservative fade eligibility

Public `AudioPlaybackConfiguration.getAudioDeviceInfo()` was never populated and is deprecated from API 36. `getAudioDevicesForAttributes()` (API 33) predicts routing and explicitly cannot prove which route a third-party player selected. The connected-device list also does not identify active routing.

The native backend therefore returns a known route only when the public output topology contains exactly one sink and the API 33 media prediction selects that same device. Multiple connected outputs, an unavailable prediction and all API 26–32 routes remain unknown. In addition, fading requires observable media sessions so unknown remote playback cannot be mistaken for the silence required for an upward restore. These restrictions can skip fading on ordinary phone/Bluetooth configurations; they preserve the original volume and do not disable playback stopping. Do not claim universal fade support from a successful stop test.

The platform-neutral fader fully implements ease-out lowering, persisted bookkeeping before writes, readback/route/user-change aborts, settled-silence restoration and explicit cancellation ramps. Native eligibility can be expanded only with a verified public route strategy, not hidden APIs or extra privileged routing access.

## Execution guarantees and boundaries

- Mutations run within the engine's effect gate; invalidating a generation prevents subsequent stale writes.
- Every automatic increase requires an unchanged route/index, observable remote state, no call/alarm, continued silence for 1 second plus 1.5 seconds of settling, and the original persisted expiry. Current foreground audio capability is rechecked in the native setter.
- Only explicit user cancellation/extension permits a one-second ramp while media continues, capped at the captured original index and using the same route/user guards.
- Focus is owned by run ID. Stale cleanup cannot abandon a newer run's request. Focus is abandoned before automatic restoration, because a player can resume on abandonment.
- A screen-off watchdog pauses resumed playback for at most 30 seconds and ends on screen interaction. Calls/alarms defer. No PLAY_PAUSE key is used.
- The API 26–27 fallback uses a non-muting floor of index 1 only when lowering is possible. API 28+ uses the public stream minimum. No mute operation is called.
- The 2-minute restoration expiry is never renewed by process recreation. Expired/ambiguous restoration is dropped.

Verified sources:

- [AudioManager routing prediction and volume APIs](https://developer.android.com/reference/android/media/AudioManager).
- [AudioPlaybackConfiguration routing limitation](https://developer.android.com/reference/android/media/AudioPlaybackConfiguration#getAudioDeviceInfo()).
- [MediaSessionManager listener access](https://developer.android.com/reference/android/media/session/MediaSessionManager#getActiveSessions(android.content.ComponentName)).
- [Android 17 background audio hardening](https://developer.android.com/about/versions/17/changes/bg-audio).

Signatures were additionally checked in the installed API 37 SDK's `android-stubs-src.jar`. API 37 behavior still requires an Android 17 runtime test; compiling against that SDK is not runtime evidence.
