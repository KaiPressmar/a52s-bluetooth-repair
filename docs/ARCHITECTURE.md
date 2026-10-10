# Architecture

Since v0.16.0 the app is event-driven: **Android starts it only during calls**. Between calls nothing runs. There is no background service, no polling, no wake-up and no notification.

## Why the earlier design could not repair real calls

Up to v0.15 a foreground service called `AudioManager.setCommunicationDevice()` to "reselect" Bluetooth SCO during a call. In Android 14, AudioService only honors the route request of the process that **owns the audio mode** (`AudioDeviceBroker.topCommunicationRouteClient()`):

```java
for (CommunicationRouteClient crc : mCommunicationRouteClients) {
    if (crc.getPid() == mAudioModeOwner.mPid) return crc;   // Telecom / phone process
}
if (!mCommunicationRouteClients.isEmpty() && mAudioModeOwner.mPid == 0) {
    return mCommunicationRouteClients.get(0);               // only when nobody owns the mode
}
return null;
```

During a cellular call the mode owner is the telephony stack, so a third-party app's request is accepted (`setCommunicationDevice()` returns `true`) but **never applied**. The route repairs of v0.4–v0.15 were therefore no-ops in real calls.

## The Telecom path

The component that actually owns call audio routing is Telecom (`CallAudioRouteStateMachine`). The app uses API 34+ `requestCallEndpointChange()` with actual available endpoints and result callbacks; it retains legacy `setAudioRoute()` on older devices or when no modern availability was supplied. Switching car → phone → car makes Telecom disconnect and reconnect the HFP SCO link (`BluetoothHeadset.disconnectAudio()/connectAudio()`). The app must observe departure and return; a command alone does not prove the link was rebuilt.

Telecom binds a **non-UI InCallService** from any package when the app holds the `MANAGE_ONGOING_CALLS` app-op (`InCallController.getInCallServiceType()`, android14-release). That permission is `signature|appop`, and the app-op can be granted in two ways:

1. **Companion device link (on the phone, no computer):** the `COMPANION_DEVICE_WATCH` role grants `MANAGE_ONGOING_CALLS` as an app-op. A normal app obtains the role by associating a device through `CompanionDeviceManager` with `DEVICE_PROFILE_WATCH`, using the normal permission `REQUEST_COMPANION_PROFILE_WATCH`. The discovery dialog lists already bonded Bluetooth Classic devices, so the car can be selected directly.
2. **One adb command:** `adb shell appops set de.kaipressmar.a52srepair MANAGE_ONGOING_CALLS allow`. It persists across reboots.

Dead ends checked against AOSP 14: the `CALL_COMPANION_APP` permission is "normal", but Telecom's `getCallCompanionApps()` only returns test overrides. The car-mode InCallService requires the system-only `CONTROL_INCALL_EXPERIENCE`.

## Modules

```
core/   Pure Java 17 library, no Android dependency. All decisions live here.
  model/      CallAudioSnapshot, AudioRoute, CallPhase
  diagnosis/  Fault, FaultClassifier          – snapshot → most fundamental visible fault
  repair/     CallRepairEngine, RepairCommand, PreventiveRebuildMode
  report/     CallReport, CallOutcome, CallHistory
  device/     DeviceFamily
  version/    SemanticVersion

app/    Android application: one universal APK (minSdk 31, targetSdk 36), device detected at runtime
  telecom/      CallAudioService (InCallService), CallSession, TelecomMapping
  audio/        HeadsetMonitor (HFP/SCO state), VoiceStream (downlink route + volume)
  setup/        ProtectionStatus, CarLinkManager (Companion Device Manager)
  data/         AppSettings, CallReportRepository
  diagnostics/  DiagnosticLog (rotating), DiagnosticReport (share)
  update/       UpdateRepository, UpdateInstaller, UpdateRelease, HttpClient
  ui/           MainActivity + status / history / settings screens (Material 3, ViewBinding)
```

## Call flow

```
Telecom binds CallAudioService ──► CallSession.start()
        │  onCallAdded / onCallAudioStateChanged / call state changes
        ▼
CallSession.tick():  snapshot (Telecom route + HFP/SCO + voice route/volume)
        │
        ▼
CallRepairEngine.onSnapshot(snapshot, elapsed) ──► Step{commands, nextCheckInMs}
        │
        ├─ ROUTE_TO_EARPIECE   → available earpiece endpoint / legacy route
        ├─ ROUTE_TO_BLUETOOTH  → available Bluetooth endpoint / legacy route
        └─ RESTORE_VOICE_VOLUME→ unmute + 60 % STREAM_VOICE_CALL
        ▼
last call removed ──► CallReport persisted, Telecom unbinds, process idle
```

Ticks are 1 s during the first 30 s of a call and 5–15 s afterwards. Every Telecom event re-evaluates after 150 ms.

## Engine rules

| Situation | Action |
|---|---|
| Healthy Bluetooth call, Galaxy A52s 5G | One **preventive rebuild** (earpiece → Bluetooth) after 1 s of steady Bluetooth, within the first 30 s. This covers the silent-downlink fault that no API can see. |
| Call on the phone although the car is a usable route (≥ 3 s) | `ROUTE_TO_BLUETOOTH` |
| SCO link down, or voice played off Bluetooth (≥ 2 s) | Rebuild |
| Voice stream muted / 0 (≥ 1.5 s) | Restore volume |
| HFP connected, but Telecom offers no Bluetooth route | Report unavailable. Check/reconnect the device; a restart may help a wedged stack. |
| Speaker or wired headset | Never touched, also when chosen in the middle of a rebuild |
| Call left Bluetooth without our command (user or car chose the phone) | Hands off after established audio until a new Bluetooth selection or an accepted explicit manual request |
| Call drops to the phone within 8 s after one of our route changes (the rebuilt SCO link collapsed; Telecom falls back to the earpiece) | Treated as a failed repair and routed back to the car once; a second drop is respected as a user choice |
| Conference / waiting call | Defer route repair, including explicit manual requests |

Budget: three automatic route attempts, at most one preventive experiment and two volume restores per call. Explicit manual requests have a separate limit of two with a 15 s cooldown and retain the automatic budgets. Every operation is verified. "Automatic repair" off means observe-only unless the user explicitly requests a manual reconnect. The read-only manual check never executes an engine command.

On API 34+, routing selects the last known Bluetooth endpoint if still available, otherwise a single unambiguous candidate. Native rejection ends the current operation and backs off. Superseded outcomes and outcomes from an ended session are ignored. Successful acknowledgements trigger observation, not an assumed repair.

Manual controls and source-backed alternatives: [RECOVERY_OPTIONS.md](RECOVERY_OPTIONS.md).

## Settings

- **Automatische Reparatur** (on): switches the engine between repair and observe-only.
- **Vorsorglicher Neuaufbau**: the default comes from the detected device (A52s: every call; other phones: only after a problem in the last 7 days); can also be switched off.

## Sources

- [AudioDeviceBroker.java (android14-release)](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/services/core/java/com/android/server/audio/AudioDeviceBroker.java)
- [InCallController.java (android14-release)](https://android.googlesource.com/platform/packages/services/Telecomm/+/refs/heads/android14-release/src/com/android/server/telecom/InCallController.java)
- [InCallAdapter.java (android14-release)](https://android.googlesource.com/platform/packages/services/Telecomm/+/refs/heads/android14-release/src/com/android/server/telecom/InCallAdapter.java)
- [RoleManagerAdapterImpl.java (android14-release)](https://android.googlesource.com/platform/packages/services/Telecomm/+/refs/heads/android14-release/src/com/android/server/telecom/RoleManagerAdapterImpl.java)
- [roles.xml – COMPANION_DEVICE_WATCH (android14-release)](https://android.googlesource.com/platform/packages/modules/Permission/+/refs/heads/android14-release/PermissionController/res/xml/roles.xml)
- [core/res/AndroidManifest.xml – MANAGE_ONGOING_CALLS, REQUEST_COMPANION_PROFILE_WATCH](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/core/res/AndroidManifest.xml)
- [CompanionDeviceDiscoveryService.java – bonded devices](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/packages/CompanionDeviceManager/src/com/android/companiondevicemanager/CompanionDeviceDiscoveryService.java)
