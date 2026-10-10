# Recovery options and manual controls (v0.20.1)

Research checked on 2026-10-10. The supplied reports support failed SCO/routing
and one-way audio symptoms. They do not establish which microphone carried the
uplink, the vendor root cause, or that any phone-side workaround fixes every case.
The new controls and state-machine regressions have automated coverage; actual
A52s/car audio recovery still needs physical validation.

The owner subsequently clarified that Bluetooth off/on never restores this fault;
only a phone restart has been demonstrated. See [reboot-only recovery](REBOOT_RECOVERY.md)
for the layer analysis, comparable reports and the improved teardown hypothesis.

## What the implementation can do

| Case | Detection and treatment |
| --- | --- |
| Answer in the car, call remains on phone | Confirm available Bluetooth plus wrong call route after 3 s; request Bluetooth without screen taps. Failed direct requests can escalate within the automatic budget. |
| Bluetooth route but SCO down or predicted voice route elsewhere | Confirm after 2 s; leave Bluetooth, wait for stable phone routing and SCO teardown, then request the previously selected available Bluetooth endpoint. |
| Brief positive/unknown pulses between connection failures | Preserve the connection confirmation window across short gaps; reset after stable health or a sufficiently long gap. Never repair based only on unknown evidence. |
| Teardown ignored or SCO still connected | Wait at most 4 s. Require 500 ms of phone routing with SCO off, or 1.5 s of phone routing when SCO is unknown. Abort rather than reconnect over an old link; back off without bypassing the failed teardown. |
| Native endpoint request rejected or timed out | Log the error code, end the current operation and back off. Cancellation by another request prompts a fresh observation. Old/duplicate outcomes cannot fail a newer request or another call. |
| Two Bluetooth call endpoints | Prefer the last selected endpoint if still advertised. With no unambiguous target, ask the user to choose in the call app; manual preflight does not first move a working call to the phone. |
| Voice uses BLE/LE Audio | Positive LE Audio evidence does not require classic HFP SCO. Media-only A2DP is not accepted as voice-call transport. |
| Call playback muted or volume zero | Restore voice-call volume within the existing limit; manual reconnection also restores it when this indication exists. The car/headset can have its own volume/mute, which the app cannot measure reliably. |
| Microphone muted | Show/log the microphone flag separately; preserve its state. An intentional input mute is not a downlink fault and is not automatically undone. |
| Bluetooth access granted during a call | A manual check/reconnect can start the previously unavailable profile observation. No new permission is requested. |
| Initial profile-proxy request fails | In-call snapshots retry profile acquisition at most once per 5 s; observation also recovers after a permission grant without needing phone input. |
| Speaker/wired/streaming, hold, multiple calls, hang-up | Refuse or pause disruptive operations. Finished sessions cannot issue manual requests. |
| HFP connected but no Bluetooth endpoint | Report unavailable; no route command can create an endpoint missing from Telecom. Reconnect/check the device outside the app. |
| No call delivered to the app | Explain missing call access or Telecom binding. A manual button cannot grant the app-op, detect unsupported calls or force Telecom to bind the service. |
| APIs look healthy but remote speech is silent | Public signals cannot prove speech content. Use the optional preventive rebuild, or an explicit manual reconnect while stationary. Both are hypotheses, not a vendor-stack reset. |

## Manual actions

The status screen offers **Check now** / **Jetzt prüfen** and **Reconnect Bluetooth
audio** / **Bluetooth-Audio neu verbinden**. Checking reads current call observations
and setup access without executing an engine command. Existing automatic protection
continues independently.

Manual reconnection is an explicit report of an audio problem. It works with
automatic protection paused and can lift the hands-off gate after a phone switch.
It shares the real routing/verification implementation rather than creating a
second repair backend. It has two attempts per call and a 15 s cooldown; automatic
route and volume budgets are retained. A manual attempt also suppresses a pending
preventive experiment, without being labelled a preventive rebuild.

The UI reports a started attempt, not successful audible audio. Reports record
manual counts separately and keep the user-reported fault unresolved until a
completed return and stable positive audio evidence. Rejected commands, no return
or unknown audio cannot establish success. Physical listening is still required.

## Primary API evidence and rejected alternatives

- [InCallService](https://developer.android.com/reference/android/telecom/InCallService)
  supports endpoint requests on API 34 with result callbacks. Targets must come
  from the available list; the app does not manufacture endpoint identifiers.
- [CallEndpointException](https://developer.android.com/reference/android/telecom/CallEndpointException)
  distinguishes missing endpoints, timeouts, cancellation and unspecified errors.
  Success of a request is a reason to recheck audio, not proof of audible speech.
- [LE Audio guidance](https://developer.android.com/develop/connectivity/bluetooth/ble-audio/overview)
  distinguishes Telecom-managed call routing from applications managing their own
  communication audio. LE Audio is not the classic HFP/SCO transport.
- [AudioManager](https://developer.android.com/reference/android/media/AudioManager#startBluetoothSco())
  states that telephony has priority and an app's SCO-start request during a phone
  call is ignored. Adding start/stop SCO or voice-recognition commands would not be
  a supported general call repair. Voice-attribute devices are routing evidence,
  not access to remote speech samples. Forcing audio mode or communication-device
  ownership can interfere with the actual call owner and is not used.
- [Android 13 behavior changes](https://developer.android.com/about/versions/13/behavior-changes-13#bluetooth-adapter)
  prevent ordinary apps targeting API 33+ from enabling/disabling the Bluetooth
  adapter. The app cannot silently toggle the adapter or restart the Samsung audio
  HAL. System/root/device-owner workflows would change the project's requirements.

## Device-side recovery beyond route repair

[Samsung troubleshooting](https://www.samsung.com/us/support/troubleshoot/TSG10005126/)
recommends checking device settings/compatibility, restarting both devices,
updating phone and peripheral software, reconnecting/re-pairing, and diagnosing
interference from downloaded apps using Safe mode. These are general connection
steps, not proof of a permanent fix for this A52s firmware fault.

Generic checks: verify call audio is enabled for the paired device and both
devices' call volume; avoid a competing connected headset; reconnect and retry;
restart phone/head unit if wedged; check available official firmware. Safe mode
can help isolate another app, but also disables this downloaded repair app.
For the supplied owner's persistent fault, adapter toggling has already failed;
the demonstrated fallback is a phone restart after the call. Device reconnection
and the app's call-route rebuild remain unverified alternatives on that phone.

[Samsung's Bluetooth reset guide](https://www.samsung.com/ca/support/mobile-devices/reset-bluetooth-connections-on-your-samsung-galaxy/)
describes resetting Wi-Fi/Bluetooth settings for persistent connection problems.
This loses saved connection settings and requires setup again; the app never
performs it automatically. Factory reset, custom firmware and privileged process
restarts are not part of this app's repair ladder and do not establish a permanent
fix for a vendor bug.

[A52s community reports](https://eu.community.samsung.com/t5/galaxy-a-series/bluetooth-headset-won-t-answer-calls-since-recent-software/td-p/12184150/page/2)
mention AVRCP/SAP/offload changes and temporary reboot recovery with mixed results.
These are uncontrolled workarounds. AVRCP/A2DP media settings are not a documented
general HFP call fix; no global developer-setting changes are automated.

## Device validation

Repeat incoming/outgoing and hands-free-answer scenarios with prevention on/off.
While stationary, test the manual read-only action and a reconnect with protection
paused, after a deliberate phone switch, after automatic budget exhaustion and
with apparently healthy API signals. Also test multiple headsets, LE Audio where
supported, denied/restored permission, held/waiting calls, command rejection and
hang-up during a pending request. Verify intentional speaker/wired/microphone
choices survive. Export during and after calls, and confirm both speech directions
by listening; the supplied pre-release reports cannot validate this new release.
