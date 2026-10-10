# Reboot-only recovery: evidence and repair hypothesis (v0.20.1)

Checked on 2026-10-10. The affected owner explicitly confirms that switching
Bluetooth off/on has **never** fixed this fault; a **phone restart** is the only
manually demonstrated recovery. Do not present reconnecting or our Telecom
rebuild as a demonstrated fix for that phone. Automated tests establish state
machine behavior, not restored speech on Samsung hardware.

## What a phone restart changes

| Layer | Restart versus the app's route rebuild |
| --- | --- |
| Telecom/Bluetooth call session | A restart ends existing calls and reconstructs service state. The app can request a different call endpoint and a return, preserving the call. |
| SCO and Bluetooth profiles | Restart initializes the stack again. The app can ask Telecom to release/reacquire call audio; it cannot restart profile services or reproduce every stack reset. |
| AudioService, audioserver, AudioFlinger/AudioPolicy and vendor HAL | A restart reinitializes processes and their connections. A route switch only exercises routing through these services. A wedged service or cached vendor state can survive it. |
| Telephony/vendor dependencies | Restart also reconstructs other service state. SAP, Samsung multi-connectivity, optimization or modem interaction are possible hypotheses; the owner's report does not identify a faulty component. |

The distinction is supported by the [Android audio architecture](https://source.android.com/docs/core/audio/implement)
and [Android 14 AudioService server-recovery implementation](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/services/core/java/com/android/server/audio/AudioService.java).
Neither describes Samsung's proprietary bug. Reboot-only recovery suggests a
state beyond a simple Bluetooth enable flag; it does not prove which layer fails.

## The useful change within ordinary app permissions

[Android 14 BluetoothRouteManager](https://android.googlesource.com/platform/packages/services/Telecomm/+/refs/heads/android14-release/src/com/android/server/telecom/bluetooth/BluetoothRouteManager.java)
disconnects and connects asynchronously. Its connection path skips connecting
the same device if it still appears audio-connected. Therefore returning as soon
as the phone-route callback arrives can fail to establish a fresh SCO session.
That is a concrete repair hypothesis, not proof of this firmware's root cause.

v0.20.1 keeps the phone route while waiting for teardown. It requires 500 ms of
observed phone routing with SCO disconnected; if SCO is unknown (including LE
Audio), it instead uses 1.5 s of stable phone routing and explicitly logs the
uncertainty. A route/SCO rebound resets that dwell. No return is issued while
SCO is still reported connected. The entire leave phase times out after 4 s,
backs off and keeps the existing retry limits. A subsequent automatic attempt
cannot bypass the failed teardown with a direct Bluetooth request.

The app observes audio mode but never forces MODE_NORMAL/MODE_IN_CALL, takes
communication-device ownership or changes microphone mute. Those actions could
fight the real call owner rather than repair it. An unknown SCO dwell is still
only a routing experiment, not confirmed teardown or a vendor-stack reset.

## Comparable first-person reports, with conflicting outcomes

| Evidence | What can be concluded |
| --- | --- |
| [A52s callers describe immediate fallbacks and temporary reboot recovery](https://eu.community.samsung.com/t5/galaxy-a-series/bluetooth-headset-won-t-answer-calls-since-recent-software/m-p/12644251/highlight/true) | Similar symptoms across cars, headsets and watches; some users report only one working call after restart. This is stronger context than generic pairing advice, but not a controlled diagnosis. |
| [A52s thread, page 12](https://eu.community.samsung.com/t5/galaxy-a-series/galaxy-a52s-5g-bluetooth-broken-after-last-update-security-from/td-p/11872914/page/12) | SAP/offload changes and unrestricted dependency apps are reported to help some devices temporarily. Others report no improvement or recurrence. Optimization/SAP causation is speculation. |
| [Earlier car-connection report](https://eu.community.samsung.com/t5/galaxy-a-serie/bluetooth-verbindungsproblem-a52s-5g-mit-auto/m-p/6717721/highlight/true) | Bluetooth Agent data clearing is proposed together with reboot/re-pairing. The combined steps do not establish an effective restart-free fix and involve a different update period. |

SAP is a SIM-access profile; AVRCP/A2DP concern media/control. Changing these can
indirectly reconfigure Samsung services, but is not a documented general HFP
repair. No global developer settings, battery restrictions or system-app data are
changed automatically. A user may compare one reversible setting at a time while
stationary and outside a call, documenting the original value and recurrence.
Do not disable SIM services or assume every car supports SAP.

## Why we cannot implement a hidden equivalent of reboot

[PowerManager.reboot](https://developer.android.com/reference/android/os/PowerManager#reboot(java.lang.String))
requires the privileged REBOOT permission, unavailable to this ordinary app.
[Android 14 process restrictions](https://developer.android.com/about/versions/14/behavior-changes-all#kill-background-processes)
allow third-party apps to kill only their own background processes. Restarting
our app or rebinding our profile proxy does not restart audioserver, Telecom or
Samsung dependencies. Adapter toggling is restricted too and already failed for
this owner. No hidden APIs, root/Shizuku, process-killing or radio/SIM changes are
introduced as an unsupported reboot substitute.

## Compare before and after the proven recovery

While stationary, export during a failing call, including any bounded manual
attempt, and after the call before restarting. Restart the phone yourself only
after ending the call. Reconnect the same peripheral, make a comparable call and
export again. The report now records bootElapsedMs and audioMode; call logs also
show audio-mode changes and the teardown/settle decisions. Numeric audio modes
are observations, not fault classifiers, and do not expose the HAL's internals.

Compare endpoint availability, HFP/SCO, predicted voice routing, microphone mute,
volume and route-request errors. Note whether speech was actually audible in both
directions. If stable API evidence returns without audible speech, classify the
experiment as unsuccessful regardless of a route-verification result. The phone
restart remains the owner's confirmed fallback; repeated Bluetooth toggling is
not a recommended recovery loop for this case.
