# Failed v0.20.2 call: findings and v0.20.3 hypothesis

The owner supplied exports during and after the same A52s / Android 14 call. Raw captures stay outside the repository. The newer report shows an unresolved 189-second session, three automatic route attempts and two accepted manual repairs. The live export is provisional and contains only the first accepted manual attempt. An earlier manual button press returned BUSY.

Detection worked in this session: active call state, Bluetooth availability, HFP and microphone/voice volume were observed. Across the newer capture there are nine application Bluetooth requests, four phone-route requests and fifteen active Bluetooth observations, with **no SCO-positive observation**. Volume was 6/8, microphone unmuted and audio mode IN_CALL. A route label briefly becomes Bluetooth then returns to earpiece; several application-triggered pulses last about 60–313 ms. This is not verified recovery.

Later Bluetooth pulses without nearby application route commands are consistent with further user attempts. Route callbacks cannot establish who pressed which button; they must not be counted as exact tap events. Old October 9 records come from an earlier engine and its former hands-off behavior; they do not establish a current regression.

The report does not include modern endpoint availability, legacy device lists, target identity or Telecom/native error codes. `linked=1` counts companion associations, not all connected call-capable devices. It does not prove a single available audio device or identify the faulty vendor component.

## Concrete changes

The old fallback calls `setAudioRoute(BLUETOOTH)` with no device. When modern availability is absent, v0.20.3 uses the selected or sole device from `CallAudioState.getSupportedBluetoothDevices()` through [InCallService.requestBluetoothAudio](https://developer.android.com/reference/android/telecom/InCallService#requestBluetoothAudio(android.bluetooth.BluetoothDevice)). The selected target is retained during a phone hop. Multiple unselected devices, or disappearance of the selected device while another remains, block automatic routing rather than choosing another peripheral. Modern advertised endpoints retain precedence.

[Android 14 BluetoothRouteManager](https://android.googlesource.com/platform/packages/services/Telecomm/+/refs/heads/android14-release/src/com/android/server/telecom/bluetooth/BluetoothRouteManager.java) chooses an active or arbitrary connected device when no matching address is provided. Passing an actual advertised device removes that uncertainty. It still goes through Telecom and cannot bypass a blocked stack. SCO verification now observes that target rather than any connected headset.

[Android 14 Timeouts](https://android.googlesource.com/platform/packages/services/Telecomm/+/refs/heads/android14-release/src/com/android/server/telecom/Timeouts.java) defines a 5-second Bluetooth pending window and 500-ms internal retry backoff. The old 4-second direct timeout and 3-second repeated return could overlap this work. New windows are 6 seconds direct, 12 seconds returning, one repeat no earlier than 6 seconds, and a 5-second pause after unsuccessful return. These are bounded hypotheses, not measured Samsung-specific timing requirements.

Diagnostics expose endpoint-vs-legacy path, advertised Bluetooth-device count and target availability. No device name/address is exported. Existing teardown confirmation, manual limits, mute preservation and speaker/wired/multiple-call protections remain in place.

## Physical validation still required

Answer via the car without touching the phone, with one call-capable Bluetooth device connected. Export during failure and after completion. Look for `routing path=explicit-device` or `routing path=endpoint`, then sustained SCO/voice evidence and actual audible speech. Check two-device ambiguity, disappearance of the chosen car and user-selected speaker routing separately while parked.

If explicit requests still immediately fall back with SCO disconnected, public routing did not clear the vendor fault. The app cannot restart audioserver, Telecom, the modem or proprietary HAL components with its normal permissions. No forced audio mode, global communication-device ownership, root, hidden API or profile/radio reset is added. Bluetooth toggling has never helped this owner; a phone reboot remains the only demonstrated recovery.
