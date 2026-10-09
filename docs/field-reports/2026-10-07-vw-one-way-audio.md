# Field report 2026-10-07 – VW head unit, one-way call audio

| | |
|---|---|
| Phone | Samsung SM-A528B (`a52sxq`), Android 14 (SDK 34), build `A528BXXSBGYI3` |
| App build | A52s profile, earlier release (watchdog `triggerMode=bluetooth+hfp+audio`, i.e. before v0.13 telephony/HFP-transport signals) |
| Car | VW head unit, Bluetooth name `VW_BT_1159` (HFP + A2DP) |
| Symptom | During a phone call **the other side could hear us, but we could not hear them** over the car |
| Fixed by | Reboot (as in all previous incidents) |

## Timeline from the log

| Time | Event | State |
|---|---|---|
| 21:12:40 | Manual check, car not connected | `IDLE`, only built-in devices (`type=1` earpiece, `type=2` speaker, `type=18` telephony) |
| 21:12:48 / 21:13:27 | `WATCHDOG START` twice within 40 s | The foreground service was restarted (process killed/recreated) |
| 21:15:54 | Car connects: `type=7 id=862 name=VW_BT_1159` (Bluetooth SCO) appears as communication device and output | `hfpProfile=true scoAvailable=true scoSelected=false` → `IDLE`, correct: no call yet |
| 21:16:01 | A2DP connects (`media-profile-2`) | `mediaBt=true` |
| *(call)* | *The submitted report text was truncated at this point; the in-call snapshots were not included* | – |

## Analysis

1. **The idle state is clean.** The SCO endpoint for the car is offered by Android before the call, so the stack was not in the "SCO missing" state (variant B). The preflight / ready-state logic would treat this connection as ready.
2. **The fault is one-way audio (variant D).** The uplink works, which proves that an SCO link existed and the car's microphone path was routed. In this state the older app logic sees `scoAvailable=true scoSelected=true` → `HEALTHY_CALL` and does **nothing**. The same blind spot existed in v0.14, even with the HFP transport check, because `isAudioConnected()` is true when the microphone works.
3. The early `audioMode=0` while the car is connected is normal. The earlier field log already showed that the mode may lag during a call, which is why call detection uses the telephony OFFHOOK state.
4. The two `WATCHDOG START` lines 40 s apart show that the service can be restarted by the system. State that must survive a restart is kept in `RepairStateStore`.

## Consequences for v0.15.0

- New downlink signals in every in-call snapshot: `voiceRouteBt`, `voiceVolume`, `voiceMuted`, `callModeMismatch`.
- New signatures `DOWNLINK_ROUTE_MISMATCH` (voice playback not on Bluetooth while the SCO mic is) and `DOWNLINK_SILENCED` (voice stream muted/zero), each with an automatic repair.
- A **route bounce** (earpiece → SCO) repair, because re-selecting the already selected SCO device is a no-op.
- **Hands-free preventive SCO rebuild at the start of every Bluetooth call** (A52s). While driving, the call is only accepted via the car, so no repair may depend on tapping the phone. If the downlink is dead inside the vendor HAL, no signal is visible to apps, so the rebuild runs preventively.
- Optional **"Ich höre nichts – reparieren"** notification action (for a passenger) and a relabelled manual button.
- Regression test: `OneWayAudioDetectionTest`.

## What to capture next time

During the faulty call, before rebooting:

1. Note the time and whether the audio came back after the short gap at call start (preventive rebuild).
2. When parked, share the app log (the snapshot now includes the downlink fields).
3. If a PC is available, run `scripts/capture-call-audio-state.sh` over wireless debugging.

Telling `voiceRouteBt=false` apart from `voiceRouteBt=true` with silence decides whether the public-API repair can work or only a reboot helps.

## Raw report (as submitted, truncated)

```
2026-10-07 21:12:40.673 | MANUAL CHECK
device=samsung SM-A528B device=a52sxq sdk=34 build=UP1A.231005.007.A528BXXSBGYI3
buildProfile=Galaxy A52s 5G targetKey=a52s actualFamily=A52S profileMatch=true
bluetoothEnabled=true
audioMode=0 speaker=false musicActive=false
communicationDevice=type=1 id=2 name=SM-A528B
availableCommunicationDevices:
 - type=1 id=2 name=SM-A528B
 - type=2 id=3 name=SM-A528B
outputs:
 - type=1 id=2 name=SM-A528B
 - type=2 id=3 name=SM-A528B
 - type=18 id=12 name=SM-A528B
health=IDLE hfpProfile=false scoAvailable=false scoSelected=false btMediaOutput=false signature=IDLE confidence=NONE routeOwned=false summary=Bereit – kein aktiver Anruf

2026-10-07 21:12:48.210 | WATCHDOG START triggerMode=bluetooth+hfp+audio periodicOnlyWhenBtOn
2026-10-07 21:12:49.427 | WATCHDOG CHECK reason=audio-device-added state=IDLE hfpProfile=false mediaBt=false scoAvailable=false scoSelected=false attempts=0
[… identical idle snapshot …]
2026-10-07 21:12:49.429 | WATCHDOG DECISION signature=IDLE confidence=NONE action=NONE reason=Kein aktiver Telefoniefehler
2026-10-07 21:12:49.442 | WATCHDOG NEXT inMs=1800000 state=IDLE hfp=false attempts=0
2026-10-07 21:13:27.456 | WATCHDOG START triggerMode=bluetooth+hfp+audio periodicOnlyWhenBtOn
2026-10-07 21:13:28.701 | WATCHDOG CHECK reason=audio-device-added state=IDLE hfpProfile=false mediaBt=false scoAvailable=false scoSelected=false attempts=0
[… identical idle snapshot …]
2026-10-07 21:13:28.702 | WATCHDOG DECISION signature=IDLE confidence=NONE action=NONE reason=Kein aktiver Telefoniefehler
2026-10-07 21:13:28.709 | WATCHDOG NEXT inMs=1800000 state=IDLE hfp=false attempts=0
2026-10-07 21:15:54.363 | WATCHDOG CHECK reason=audio-device-added state=IDLE hfpProfile=true mediaBt=false scoAvailable=true scoSelected=false attempts=0
device=samsung SM-A528B device=a52sxq sdk=34 build=UP1A.231005.007.A528BXXSBGYI3
buildProfile=Galaxy A52s 5G targetKey=a52s actualFamily=A52S profileMatch=true
bluetoothEnabled=true
audioMode=0 speaker=false musicActive=false
communicationDevice=type=1 id=2 name=SM-A528B
availableCommunicationDevices:
 - type=1 id=2 name=SM-A528B
 - type=2 id=3 name=SM-A528B
 - type=7 id=862 name=VW_BT_1159
outputs:
 - type=1 id=2 name=SM-A528B
 - type=2 id=3 name=SM-A528B
 - type=7 id=862 name=VW_BT_1159
 - type=18 id=12 name=SM-A528B
health=IDLE hfpProfile=true scoAvailable=true scoSelected=false btMediaOutput=false signature=IDLE confidence=NONE routeOwned=false summary=Bereit – kein aktiver Anruf

2026-10-07 21:15:54.364 | WATCHDOG DECISION signature=IDLE confidence=NONE action=NONE reason=Kein aktiver Telefoniefehler
2026-10-07 21:15:54.378 | WATCHDOG NEXT inMs=900000 state=IDLE hfp=true attempts=0
2026-10-07 21:16:01.201 | WATCHDOG CHECK reason=media-profile-2 state=IDLE hfpProfile=true mediaBt=true scoAvailable=true scoSelected=false attempts=0
device=samsung SM-A528B device=a52s
[report truncated here]
```
