# Call-audio resilience, v0.19.1

Historical v0.19 behavior. For current endpoint routing and manual actions, see [RECOVERY_OPTIONS.md](RECOVERY_OPTIONS.md).

## Evidence and sources

The October 9 A52s report proves failed Bluetooth selections and persistent
disconnected SCO, not which microphone carried the uplink. The October 7 report
describes one-way audio despite apparently correct transport/routing. Related
[Samsung user reports](https://eu.community.samsung.com/t5/galaxy-a-series/bluetooth-headset-won-t-answer-calls-since-recent-software/td-p/12184150/page/2)
describe connected peripherals with unavailable call audio. These are symptom
reports, not proof of a particular firmware cause or a universal workaround.

[BluetoothHeadset](https://developer.android.com/reference/android/bluetooth/BluetoothHeadset)
exposes distinct HFP profile and audio-state notifications; profile connection
alone does not establish SCO audio. The implementation uses broadcasts as wake-up
hints and queries current APIs rather than trusting broadcast extras.

[InCallService](https://developer.android.com/reference/android/telecom/InCallService)
provides endpoint and availability callbacks from API 34, replacing the deprecated
legacy audio callback. The app observes both; modern endpoint observations take
precedence once received. Routing still uses the existing Telecom command path;
this release does not invent endpoints or migrate commands to a new backend.

[AOSP Telecom](https://android.googlesource.com/platform/packages/services/Telecomm/+/android16-release/src/com/android/server/telecom/CallAudioRouteStateMachine.java)
waits for connection events before completing Bluetooth transitions and can fall
back when Bluetooth disconnects. This supports treating failed connection attempts
separately from leaving working Bluetooth audio; Samsung implementation details
may differ.

[AudioManager](https://developer.android.com/reference/android/media/AudioManager#getAudioDevicesForAttributes(android.media.AudioAttributes))
describes predicted audio routing, not access to remote speech samples. Positive
route/SCO evidence is useful for verification but cannot establish audible downlink.

[Android broadcast guidance](https://developer.android.com/develop/background-work/background-tasks/broadcasts)
requires exported receivers to receive events from the Bluetooth privileged UID.
The receiver listens to protected Bluetooth actions only during a call and ignores
extras. No background service or new permission is introduced.

## Derived behavior and regression coverage

| Additional case | Behavior |
| --- | --- |
| Bluetooth requests ignored while HFP stays connected | Escalate once the direct request times out, using the existing teardown/return sequence and shared per-call budget. |
| Brief healthy pulse after a fault | Keep the fault unresolved until healthy routing/evidence stays present for 2.5 seconds. |
| Both SCO and voice-route evidence unknown | Do not count a confirmed fault as repaired; keep observing. |
| Unknown audio before a fallback to phone | Do not infer a manual departure from working Bluetooth solely from its route label. |
| Call waiting or multiple calls | Defer disruptive repair without spending another attempt. Resume diagnosis for a single call. |
| No current Telecom route yet | Wait for a route observation. |
| Endpoint-only updates or stale legacy Bluetooth | Use the selected modern endpoint and available endpoint list, including an empty list. |
| HFP/SCO or adapter state changes without a Telecom callback | Trigger an in-call check from the protected Bluetooth event. |
| Late profile callback after hang-up | Close its proxy; do not reopen monitoring. |
| Permission/Binder failure during a route command | Log the exception class, keep observing and retain the attempt limit. |

Speaker, wired and streaming choices remain explicit alternatives. A normal app
cannot reliably distinguish every earpiece fallback from a manual phone choice;
the engine uses the preceding audio evidence, its own commands and the existing
one-time repair grace. It does not restart Bluetooth, change SAP/AVRCP settings,
record calls or restart the vendor HAL.

## Hands-free answer without phone input

Repeated Bluetooth selections are diagnostic observations, not a requirement for
repair. With call access granted and protection enabled, the bound InCallService
tracks ACTIVE call state, Telecom Bluetooth availability, HFP/SCO, predicted voice
routing and voice volume. A call remaining on the earpiece while Bluetooth is
available confirms a route fault after 3 seconds; a persistent disconnected SCO
or non-Bluetooth voice route confirms after 2 seconds. In-call checks continue
even when no further callback or screen tap arrives.

Version 0.19.1 fixes an additional answer transition: time spent ringing cannot
establish working call audio. Positive link evidence must persist for 2.5 seconds
during an audio-carrying single call before a subsequent phone switch is treated
as a departure from working Bluetooth. This also rejects a short healthy pulse.
The shared attempt budget and speaker/wired/streaming choices are unchanged.

Public observations do not reliably reveal whether the answer button was pressed
on the car or phone. A later automatic fallback from established working audio can
still look like an intentional phone choice. Healthy routing with silent remote
speech is also unobservable through these APIs; the existing optional preventive
rebuild is a mitigation, not a successful fault diagnosis. Without call access or
a Telecom binding the app cannot observe the call at all.

Validate three cases without touching the phone: answer while the earpiece is
already selected, answer after a long ring then lose SCO immediately, and receive
brief positive audio signals before fallback. Expect bounded automatic commands;
also verify that a deliberate phone switch after established audio stays respected.

## Real-device validation still required

Test incoming and outgoing calls; repeated failed selections; Bluetooth off/on;
call waiting/hold/resume; two paired peripherals; and a deliberate switch to phone,
speaker or wired audio. Export during and after each call. Check both directions
by listening, compare SCO/voice-route evidence and inspect the bounded command log.
Also test permissions being revoked and hanging up while a profile bind is pending.

The available field reports are from before this release. Automated tests establish
state-machine behavior and Android wiring, not recovery of the Samsung/Qualcomm HAL.
