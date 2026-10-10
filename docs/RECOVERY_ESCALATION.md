# Public recovery paths and their limits (v0.21.0)

Reviewed 2026-10-10 against the owner's failed v0.20.2 call, the current app and
Android 14 source. The owner's only demonstrated fix is a phone reboot. Bluetooth
off/on never helped. Nothing below is a demonstrated Samsung firmware repair.

| Observable condition | Bounded action | What it cannot establish |
| --- | --- | --- |
| Active call remains on phone while Bluetooth is advertised | Wait for confirmation, request the selected/sole advertised endpoint/device | A routing command does not prove a connected SCO link |
| Modern endpoint request times out/fails | One advertised legacy Telecom fallback in the same active repair, with exactly one safely mapped device | Both paths ultimately enter Telecom; this is not a Bluetooth service reset |
| SCO or voice routing remains broken | Phone hop, wait for SCO-off/stable phone routing, return to the selected Bluetooth device | Unknown SCO cannot prove teardown; vendor state can survive routing |
| Earlier attempts fail | Last bounded rebuild: reread Telecom, reacquire our observation proxy, wait 1.5 seconds; legacy routing with 2-second settle and up to 8 seconds to leave | Rebinding our client does not restart HFP or force physical disconnection |
| Same fault persists with exact advertised conversation-device address | One `AudioManager.setCommunicationDevice` experiment per call, only after extended return; 15-second lifetime | Phone mode owner has priority and may ignore it; acceptance is not actual call audio |
| HFP connected but Telecom omits Bluetooth routing | Refresh observations once; route only if the endpoint later becomes genuinely advertised | The app cannot manufacture an unavailable Telecom endpoint |
| Voice stream muted/zero | Bounded voice-volume restoration, microphone mute preserved | Does not repair missing SCO or a vendor downlink failure |
| Selected target disappears or several targets cannot be mapped | Stop automatic switching; do not use another peripheral as success evidence | Public endpoint UUIDs provide no general address mapping |
| Speaker/wired/streaming, hold, another call, or automatic protection paused | Cancel pending automatic changes; release our AudioManager experiment | No competing audio-mode ownership or background radio reset |

Automatic routing still has three operation attempts; manual routing has two,
with a 15-second cooldown. An alternate-API fallback is part of the same operation,
not a reset of its budget. Return requests repeat at most once after 6 seconds.
The communication experiment runs at most once per call, even across manual
attempts. It requires an output device, nonzero port ID, SCO/LE conversation type,
exact Bluetooth address and a unique match; missing addresses and A2DP are refused.
The original mode and microphone mute are never changed.

An AudioManager selection is scoped to this app's Binder token. Cleanup cancels
only our request, not the phone application's selection. The driver schedules the
15-second cleanup even when the repair engine wants a longer idle delay. System
Binder failures cannot be made impossible: cleanup remains pending and retries
with 5-second backoff while the session exists, and is attempted at shutdown.
Our own positive predicted voice route is excluded from recovery evidence while
the experiment is outstanding, so it cannot validate itself. Stable SCO evidence
still does not prove audible speech; test before and after the request is cleared.

## Other candidate operations reviewed

`startBluetoothSco`/`stopBluetoothSco` are deprecated predecessors of communication
device selection, intended for use when the phone is not in a call. Telephony has
priority; adding them over the experiment provides no independent documented
reset. `BluetoothHeadset.startVoiceRecognition` establishes a voice-recognition
session, not the current cellular call, and is not used to fabricate SCO success.
Forcing MODE_NORMAL/MODE_IN_COMMUNICATION or seizing audio focus would compete
with the real phone application; MODE_IN_CALL needs privileged phone-state access.

Direct HFP connect/disconnect, active-device changes in the Bluetooth service,
process restarts, reboot, vendor/offload/SAP settings, system-app data clearing and
profile configuration require unavailable privileges or user/system intervention.
Bluetooth toggling already failed for this owner and is restricted on current
Android. Pairing removal is destructive and cannot restore the ongoing call.
Holding/unholding or ending/redialling changes the carrier call and can leave the
other person waiting or disconnected; it is not an automatic audio repair here.
None of these operations is silently replaced with reflection, hidden APIs,
root/Shizuku, an accessibility service, shell execution or new permissions.

## Primary sources

- [InCallAdapter](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/telecomm/java/android/telecom/InCallAdapter.java): generic and explicit-device routing both invoke `setAudioRoute`, with an optional address.
- [CallEndpointController](https://android.googlesource.com/platform/packages/services/Telecomm/+/refs/heads/android14-release/src/com/android/server/telecom/CallEndpointController.java): an already-current endpoint may return success without another connection request.
- [BluetoothRouteManager](https://android.googlesource.com/platform/packages/services/Telecomm/+/refs/heads/android14-release/src/com/android/server/telecom/bluetooth/BluetoothRouteManager.java) and [BluetoothDeviceManager](https://android.googlesource.com/platform/packages/services/Telecomm/+/refs/heads/android14-release/src/com/android/server/telecom/bluetooth/BluetoothDeviceManager.java): target selection and native HFP connection, including already-connected short circuits.
- [AudioManager](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/media/java/android/media/AudioManager.java): requesting-process lifetime, required cleanup and priority of the current mode owner. The experiment exercises a public AudioPolicy entry point; it cannot bypass that priority.
- [Self-managed audio guide](https://developer.android.com/develop/connectivity/bluetooth/ble-audio/audio-manager): the documented communication-device workflow primarily targets self-managed communication apps, not repair of another app's cellular call.
- [Samsung A52s reports](https://eu.community.samsung.com/t5/galaxy-a-series/galaxy-a52s-5g-bluetooth-broken-after-last-update-security-from/td-p/11872914/page/12): varied, temporary and conflicting SAP/offload/reboot outcomes, not proof of a single cause. Earlier owner evidence and source research remain in [REBOOT_RECOVERY.md](REBOOT_RECOVERY.md).

Android's open source implementation explains the paths, not Samsung's private
changes. Hardware validation in [TESTING.md](../TESTING.md) is still required.
