# Off-call recovery investigation and implementation (0.22.0)

## Evidence and hypothesis

The owner's v0.20.2 report has HFP connected but SCO and voice routing false,
short Bluetooth route selections and repeated unsuccessful Telecom commands.
Bluetooth off/on never fixed it; a full phone restart is the only demonstrated
owner workaround. This distinguishes discovery/radio connectivity from the
speech transport. It does not identify which Samsung native component is stuck.

An off-call session can request speech transport without competing with Telecom's
MODE_IN_CALL owner. Opening and closing it exercises AudioPolicy, the audio HAL
and HFP state transitions. This **could** release transient state; it is not a
known Samsung firmware repair or an equivalent of restarting those components.
A successful SCO exercise does not establish audible cellular speech or repair
of a later call. User confirmation on the affected hardware remains necessary.

## Candidate matrix

| Idea | Treatment | Rationale/limit |
| --- | --- | --- |
| Telecom endpoint / legacy / explicit-device request, phone hop | Retained bounded in-call recovery in 0.21 | Already reaches system HFP connection machinery; no independent reset |
| Off-call exact-target communication-device selection | Implemented first, retry once after confirmed off | Public AudioPolicy path; wait for actual per-target SCO, not return value |
| Own MODE_IN_COMMUNICATION + silent speech playback | Implemented only during idle modern/legacy attempt | Keeps a communication client active; no recorder, audio focus or user volume/mute mutation |
| Deprecated startBluetoothSco/stopBluetoothSco | Implemented bounded classic-HFP fallback | Off-call API; another compatibility entry point, often same downstream HFP path, not an independent native restart |
| BluetoothHeadset voice-recognition start/stop | Optional, default off, after other methods fail | Public supported-device path; Android 14 HFP has separate voice-recognition state. Can show car voice UI/tone and open mic transport; no audio recorded by app |
| Reacquire own HFP Binder / refresh available ports | Retained and logged | Repairs observations; cannot restart Bluetooth service |
| Profile call-audio toggle / per-device reconnect | Guided system Bluetooth settings | Ordinary apps cannot force HFP connect/disconnect or active-device selection |
| Re-pair / headset or parked car audio restart | Guided manual option | Re-pairing destroys pairing; cannot silently automate or restore a live call |
| SAP / A2DP offload setting changes | Guided experimental manual options | Conflicting A52s anecdotes, sometimes bundled with reboot; A2DP is media, not HFP speech; no causal proof |
| Phone/headset/car firmware update | Guided manual option | Most plausible lasting fix for vendor bug; app cannot install firmware |
| Phone reboot | Honest fallback | Owner-proven; normal app cannot reboot or reproduce all reset layers |
| Kill/restart audioserver, Bluetooth, vendor HAL or system apps | Excluded | Needs system/root privileges; resetting our own process is not equivalent |
| Hidden HFP APIs / reflection / native parameter injection / shell | Excluded | Restricted, unstable, unavailable privileges; no supported robust ordinary-app implementation |
| MODE_NORMAL to clear another app / seize focus during call | Excluded | Cannot remove another process's mode token, competes with real communication |
| Hold/unhold/redial, fake carrier calls | Excluded | Changes actual conversation, potentially interrupts remote person; no silent workaround |
| Repeated radio toggling / airplane mode / network reset / factory reset | Excluded | Owner's radio toggle already failed; call/network disruption, unavailable or destructive |
| Blind codec changes, A2DP media test as proof, fixed volume changes | Excluded | No evidence these restore the missing SCO transport; successful music is not speech proof |
| Perpetual background/pre-call polling | Excluded from this release | Background execution/audio restrictions and no guarantee of call-status access. Auto test explicitly requires visible resumed activity |

## Operation and ownership contract

Requires READ_PHONE_STATE (optional runtime grant) plus existing BLUETOOTH_CONNECT,
public Telecom idle status, no in-call service session, normal/unoccupied audio,
paused media, no wired/USB/hearing-aid alternative and one connected classic HFP
device. Mode-only idle detection is insufficient, especially while a call is
being established. Missing permission, unavailable APIs, unknown SCO and ambiguous
or replaced targets fail closed. There is no RECORD_AUDIO, foreground service,
accessibility service, root, self-installer or new update mechanism.

Discovery 5 s; each connection 10 s; stable SCO-on and SCO-off 750 ms each;
release/teardown 5 s; entire operation <=70 s. Modern requests require unique
address-matched SCO sink with nonzero port ID. The legacy API has no target
parameter, so it is allowed only with one connected HFP device. Pure LE Audio
without observable classic HFP is deliberately refused, not judged broken.

Stop after the first stable connection **and** teardown; otherwise modern retry,
legacy and optional supported voice recognition. Never start another method
before confirming SCO-off. No request is a success merely because accepted.
Silent 8-kHz mono AudioTrack is bounded by the operation; no microphone capture,
audio focus, stream-volume or microphone-mute changes. MODE_NORMAL cancels this
caller's mode token; it does not overwrite a saved global mode or clear another
app. Track and own Binder selections are released on rejection, timeout, call,
activity departure and completion. Binder-after-acceptance is treated as possibly
owned. Failed cleanup has <=3 extra retries and latches the controller against
new tests; it is exported rather than presented as successful cleanup.

Voice-recognition stop has native side effects: Android's HFP implementation may
fall back to its current active device. Do not issue it after target ambiguity,
replacement or phone-call handover. After a new call, the system owns the handover;
log it and do not disconnect its native voice audio. This cannot guarantee vendor
cleanup across arbitrary firmware races. Multiple clients starting VoIP with the
same public mode are not fully identifiable by ordinary apps; physical race tests
are required. Avoid concurrent communication/assistant sessions during testing.

Manual cooldown 30 s, automatic 10 min. Automatic mode is opt-in and also obeys
protection pause. Failed-call candidate is persisted for a future **visible**
activity session; no hidden background repair is promised. Activity pause/screen
lock cancels. Process death releases Binder/track ownership in Android, but the
next launch marks an interrupted test unresolved, never channel-tested.

## Diagnostic interpretation

Logs include operation ID, trigger, monotonic elapsed time, method/attempt,
acceptance versus confirmed transport, changed public audio snapshots, HFP proxy
generations/events, per-target SCO, support checks, port IDs/types/roles/address
presence/target-match flags, release results and bounded cleanup retries. In-call
callbacks identify legacy/modern source and endpoint type/token (hashed opaque
identifier), not a user's tap count. No device name/MAC, call number, audio or
private Bluetooth/system logs are collected. Existing two-file 512-KB rotation
bounds disk usage. Export before reboot, then compare next cellular call before
and after the test; include whether speech actually came from the car/headset.

## Primary sources and owner reports

Reviewed 2026-10-10; AOSP explains reference behavior, not Samsung private changes.

- [AudioManager](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/media/java/android/media/AudioManager.java): off-call SCO preconditions, phone priority, communication selection and caller-scoped cleanup.
- [AudioDeviceBroker](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/services/core/java/com/android/server/audio/AudioDeviceBroker.java): mode-owner priority and communication routing.
- [BtHelper](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/services/core/java/com/android/server/audio/BtHelper.java): virtual-call versus voice-recognition SCO modes, shared downstream mechanisms.
- [HeadsetService](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/heads/android14-release/android/app/src/com/android/bluetooth/hfp/HeadsetService.java): idle/occupied guards, voice-recognition state, target fallback and audio teardown.
- [BluetoothHeadset API](https://developer.android.com/reference/android/bluetooth/BluetoothHeadset): supported voice-recognition API, asynchronous audio states and required Bluetooth permission.
- [Self-managed audio guide](https://developer.android.com/develop/connectivity/bluetooth/ble-audio/audio-manager): device callbacks, bounded waiting and clear/retry.
- [Android 13 behavior changes](https://developer.android.com/about/versions/13/behavior-changes-13): normal apps cannot enable/disable Bluetooth programmatically.
- [Samsung A52s community thread](https://eu.community.samsung.com/t5/galaxy-a-series/galaxy-a52s-5g-bluetooth-broken-after-last-update-security-from/td-p/11872914/page/12): anecdotal and conflicting SAP/offload/reboot outcomes.
- [Samsung hands-free report](https://r1.community.samsung.com/t5/galaxy-a/handsfree-not-working-a52s/td-p/32093782): music works while hands-free fails; reboot anecdotes.

Do not attribute a reported improvement to a setting when a reboot was performed
at the same time. Tests and coverage cannot prove a vendor hardware workaround.
