# Galaxy A52s 5G – Bluetooth call-audio failure

Research notes and technical analysis that the detection and repair logic is based on. Last updated: 2026-10-09 (v0.19.0). Current resilience rules and sources: [CALL_REPAIR_RESILIENCE.md](CALL_REPAIR_RESILIENCE.md).

## Symptom variants

All variants share one property: Bluetooth stays connected and media (A2DP) usually keeps working, while **phone-call audio over HFP/SCO** fails until the phone is rebooted. A reboot fixes it temporarily, then the fault returns after hours or days.

| Variant | What the user experiences | What Android exposes | App signature |
|---|---|---|---|
| A – call stays on the phone | Car/headset is silent and the call plays on the phone's earpiece | SCO device available but not selected | `RECOVERABLE_ROUTE_DRIFT` |
| B – SCO missing | Car shows the call, but no audio in either direction | HFP connected, **no** SCO communication device | `HFP_CONNECTED_NO_SCO(_MEDIA_ALIVE)` |
| C – transport down | Route looks right, but no SCO link | SCO selected, `BluetoothHeadset.isAudioConnected()` false | HFP transport mismatch |
| **D – one-way audio** | **The other side hears us (mic over car works), but we hear nothing** | SCO selected and connected; the downlink is routed elsewhere, silenced, or dead in the vendor HAL | `DOWNLINK_ROUTE_MISMATCH`, `DOWNLINK_SILENCED`, or **not observable** |

Variant D is the one in the 2026-10-07 field report ([field-reports/2026-10-07-vw-one-way-audio.md](field-reports/2026-10-07-vw-one-way-audio.md)). Up to v0.14 the app rated it `HEALTHY_CALL`, because every route check passes: the microphone path proves that SCO is up.

## Timeline and affected firmware

- **One UI 6.1 / Android 14 + security patch 2025-01-01** (e.g. `A528BXXSAGYA2`): many A52s 5G owners report that Bluetooth call audio stops working with every car and headset, while music keeps working. Reboot helps for a while.
- **2025 patches**: some users report that the April 2025 patch together with a cache-partition wipe and re-pairing helped. Others continue to see the fault on later builds.
- **`A528BXXSBGYI3`** (the build in this repo's field reports, 2025-09 patch level) still shows the fault in 2026.
- User reports do not establish Samsung's future firmware plans or guarantee a vendor fix. The app must operate within the public APIs available on the affected installed firmware.
- The custom ROM UN1CA had a matching "calls go to the speaker" bug on the A52s that was traced to a missing build-system patch, not to hardware. That supports a **software (vendor audio/BT integration) cause**, not a radio defect.

## Community workarounds (all temporary)

| Workaround | Notes |
|---|---|
| Reboot | Reliable for hours or days |
| Bluetooth device settings → ensure **Calls** toggle is on | Rules out a disabled HFP profile |
| Developer options → **Bluetooth SAP activation = Force** | Reported to help on the A52s; effect fades after some days |
| Developer options → **Disable A2DP hardware offload** | Often combined with the SAP toggle |
| Battery → set **Bluetooth** and system services to *Unrestricted* | Reported to delay recurrence. Supports the "stack degrades over time" hypothesis |
| Clear Bluetooth app cache, re-pair, wipe cache partition | Mixed results |

The pattern behind all of these: the fault **builds up over runtime** and is cleared by restarting the Bluetooth/audio stack. A normal app cannot restart Samsung's Bluetooth process or the Qualcomm audio HAL. It can only use the public communication-route, volume and SCO APIs.

## What an app can observe during a call

| Signal | API | Useful for |
|---|---|---|
| Call active | Telecom `Call` state callbacks | Gating repairs to call audio; no background telephony listener |
| Selected call route | `CallAudioState` and API 34+ `CallEndpoint` | Variant A |
| Available call outputs | Telecom route mask and API 34+ endpoint availability | Variants A/B |
| HFP SCO link up | `BluetoothHeadset.isAudioConnected()` | Variant C |
| **Predicted device for voice playback** | `getAudioDevicesForAttributes(USAGE_VOICE_COMMUNICATION)` (API 33+) | **Variant D: routing evidence, not actual speech content** |
| **Voice-call volume / mute** | `getStreamVolume/isStreamMute(STREAM_VOICE_CALL)` | **Variant D: silenced downlink** |
| Downlink audio content | *not accessible to apps* | – |

The last row is the hard limit. If SCO is up, routing is correct and volume is audible, but the HAL still plays silence, **no public API can tell**.

**Design constraint: everything must be hands-free.** The fault usually happens in the car, where the driver can only accept the call through the head unit and must not operate the phone. The app therefore must not depend on user input to repair. For the invisible case it rebuilds SCO **preventively once at the start of every Bluetooth call** (step 4 below).

## Repair ladder (v0.16)

AOSP analysis showed that up to v0.15 the app's `setCommunicationDevice()` requests had **no effect during cellular calls**: on Android 14 only the audio-mode owner (Telecom) controls the communication route. Since v0.16 every route step goes through Telecom (`InCallService.setAudioRoute()`), which genuinely tears down and rebuilds the HFP SCO link, exactly like the audio button in the phone app. Details and sources: [ARCHITECTURE.md](ARCHITECTURE.md).

From least to most invasive. Each step is verified before the next one runs:

1. **Restore call volume** (`VOICE_SILENCED`): unmute and set `STREAM_VOICE_CALL` to about 60 %.
2. **Route the call to the car** (`CALL_NOT_ON_BLUETOOTH`, variant A): `setAudioRoute(ROUTE_BLUETOOTH)`.
3. **Rebuild Bluetooth audio** (`SCO_DISCONNECTED`, `DOWNLINK_NOT_ON_BLUETOOTH`, variants C/D): `setAudioRoute(ROUTE_WIRED_OR_EARPIECE)`, wait until Telecom has left Bluetooth, then `setAudioRoute(ROUTE_BLUETOOTH)`. This forces a new SCO link and codec negotiation with the car.
4. **Preventive rebuild at call start** (A52s: every car call; configurable): step 3 once, after 1 s of steady Bluetooth within the first 30 s. It covers the silent downlink that no API can observe. Cost: about 1 s of audio on the phone at call start.
5. **Report** (`BLUETOOTH_ROUTE_MISSING`, variant B): no app can repair it, so the status screen recommends a reboot.

Safety rules: confirmation windows, at most 3 route operations per call, speaker and wired routes untouched, and no fighting a user's or car's switch to the phone. See [TESTING.md](../TESTING.md).

## Debugging on a real device

- `scripts/setup-dev-env.sh` installs the toolchain without root.
- `scripts/capture-call-audio-state.sh` pulls `dumpsys audio`, Bluetooth, Telecom and filtered logcat **during** a faulty call. Run it before rebooting. The interesting fields are the communication route clients, the SCO state and the `STREAM_VOICE_CALL`/`STREAM_BLUETOOTH_SCO` volume indexes.

## Sources

- [Samsung Community: Galaxy A52s 5G bluetooth broken after last update (security from 1st Jan 2025)](https://eu.community.samsung.com/t5/galaxy-a-series/galaxy-a52s-5g-bluetooth-broken-after-last-update-security-from/td-p/11872914/page/2)
- [Samsung Community: Bluetooth issues with phone calls](https://eu.community.samsung.com/t5/galaxy-a-series/bluetooth-issues-with-phone-calls/td-p/13867504)
- [Samsung Community: Bluetooth headset won't answer calls since recent software updates](https://eu.community.samsung.com/t5/galaxy-a-series/bluetooth-headset-won-t-answer-calls-since-recent-software/td-p/12184150/page/2)
- [Samsung Community (IT): A52s 5G problema bluetooth dopo aggiornamento](https://eu.community.samsung.com/t5/galaxy-a-series/a52s-5g-problema-bluetooth-dopo-aggiornamento/td-p/11913440/page/3)
- [UN1CA discussion #661: [A52s] Bluetooth headset does not work with phone calls](https://github.com/salvogiangri/UN1CA/discussions/661)
- [XDA: bluetooth calls not working with stock rom](https://xdaforums.com/t/bluetooth-calls-not-working-with-stock-rom-can-be-a-solution-pass-to-a-custom-one.4766761/)
- [Android: Audio Manager self-managed call guide](https://developer.android.com/guide/topics/connectivity/ble-audio/audio-manager)
- [Android API diff 33: AudioManager (`getAudioDevicesForAttributes`)](https://developer.android.com/sdk/api_diff/33/changes/android.media.AudioManager)
