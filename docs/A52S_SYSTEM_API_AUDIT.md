# A52s system layers and ordinary-app recovery (0.23.0)

Investigated 2026-10-10. Owner requirement: install/update by APK, no computer,
Shizuku or root dependency. The Shizuku prototype was removed before publication.
The owner's only demonstrated fix remains a phone restart; adapter off/on failed.

## What is known about this phone

The report identifies Samsung SM-A528B, a52sxq, Android 14 / SDK 34,
UP1A.231005.007.A528BXXSBGYI3. The A52s uses Qualcomm SM7325 (Snapdragon 778G).
HFP remains connected, but SCO never becomes connected and Telecom's Bluetooth
selection falls back immediately. Neither functioning music nor a caller hearing
the owner proves that headset speech transport works. The phone mic may be used.

Android's reference path is Telecom -> Bluetooth HFP -> AudioService/
AudioDeviceBroker and AudioPolicy/AudioFlinger -> vendor audio/Bluetooth HAL ->
controller/DSP/driver. AudioService lives in system_server, not audioserver.
AudioFlinger/AudioPolicy are native audioserver services. Qualcomm audio and HCI
components are separate again. Restarting one is not a full reboot.

The [A52s device tree](https://github.com/LineageOS/android_device_samsung_a52sxq)
inherits sm7325-common. Its [extracted vendor list](https://github.com/LineageOS/android_device_samsung_sm7325-common/blob/lineage-21/proprietary-files.txt)
contains libhfp.so, Samsung audio libraries, sound_trigger.primary.lahaina.so and
Qualcomm Bluetooth components. The [extracted Bluetooth init file](https://github.com/TheMuppets/proprietary_vendor_samsung_sm7325-common/blob/lineage-21/proprietary/vendor/etc/init/android.hardware.bluetooth%401.0-service-qti.rc)
declares vendor.bluetooth-1-0-qti. These are device-specific reference artifacts,
**not an extraction of the owner's exact One UI build**. LineageOS builds some
components itself; its audio service list cannot be assumed to match stock.

Reports now include public Build.SOC_* values and a bounded read-only inventory
of relevant init declarations on the actual phone, when SELinux allows reading.
Missing/unreadable files are explicit. A declaration does not prove a process is
running or faulty. No global getprop, /proc traversal, logcat, shell or private API
is used. We still cannot identify the failing proprietary component from these
public observations alone.

## API and restart audit

| Candidate | Ordinary app access | Actual scope / decision |
| --- | --- | --- |
| InCallService endpoint/route requests | Granted call access | Retain live-call ladder. Requests reach Telecom, not a service-reset API. |
| AudioManager.setCommunicationDevice / clearCommunicationDevice | MODIFY_AUDIO_SETTINGS | Rebuild caller-owned communication selections; retain exact-target check. |
| AudioManager.setMode | NORMAL/IN_COMMUNICATION available | Own PID/token only. MODE_IN_CALL requires privileged access. Cannot clear another app's stuck mode. |
| AudioTrack open/play/release, setPreferredDevice, getRoutedDevice | Public | Creates/releases an output stream; preference is advisory, actual routing must be checked. |
| AudioRecord open/start/nonblocking read/stop/release, preferred/actual input | Public, runtime RECORD_AUDIO | New opt-in manual duplex fallback. Exercises the previously unused capture/HAL path and rebuilds own input/output streams. Not a native daemon restart. |
| AudioRecord active recording configuration | Public, own recording | Require known unsilenced capture before accepting frames. Never infer audible speech from silent/zero data. |
| startBluetoothSco / stopBluetoothSco | Public deprecated off-call API | Retain bounded fallback. Phone calls have priority; not independent native reset. |
| BluetoothHeadset start/stopVoiceRecognition | Public, BLUETOOTH_CONNECT | Retain opt-in headset-native alternate state path. Can activate assistant UI; no stop after phone handover. |
| AudioManager.setBluetoothScoOn(true) | Public deprecated | AOSP 14 ordinary-app branch only changes mBtScoOnByApp and returns. **Not a physical SCO reset**; do not use as proof or add a useless retry. |
| setSpeakerphoneOn | Public deprecated | Another route request, not teardown of another owner's native state. Modern exact-device selection is preferable. |
| Bluetooth profile proxy close/rebind | Public | Own client only. Retain observation recovery; do not call it a stack restart. |
| HFP connect/disconnect/connectAudio/active-device or connection-policy APIs | Hidden/system/privileged | Cannot robustly call with ordinary installed APK permissions. No reflection or hard-coded Binder transactions. |
| BluetoothAdapter enable/disable | Restricted for target 33+ | Already failed for owner; not equivalent to rebuilding all native processes. |
| AudioManager strategy/capture-preset preferences, AudioPolicy, server-state control | System APIs / MODIFY_AUDIO_ROUTING or comparable privileges | Not grantable by a normal runtime dialog. No hidden parameter injection or fabricated HAL-reset command. |
| audioserver or vendor audio HAL restart | init/SELinux privileges | No ordinary app access. AOSP audioserver init has dependent HAL restart handlers, but those do not grant app access and stock handlers may differ. |
| Bluetooth process restart | System force-stop/crash permission | Not a normal app permission. Optional ADB/Shizuku could request a scheduled crash, but was excluded by owner's requirement. Protected packages can refuse; PID replacement alone would not prove speech recovery. |
| Qualcomm Bluetooth HCI HAL / DSP / adsprpc / modem | Native privilege / SELinux | Normal app cannot restart these. Blindly stopping DSP/modem/SAP is not a validated HFP repair and can interrupt telephony. |
| PowerManager.reboot | Privileged REBOOT | Unavailable. Owner may reboot manually after ending the call. |
| Kill other apps/processes | Restricted on Android 14 | Cannot use killBackgroundProcesses to reset these services. Killing this app only loses its clients. |
| Force-stop/clear Samsung Bluetooth Agent / SAP / multi-connectivity | System-app administration | User reports are hypotheses with conflicting outcomes, not identified culprit. No data deletion, package disabling or network reset. |
| BluetoothSocket/GATT or direct AT commands to headset | Public protocols, not system HFP ownership | Do not compete with Android's existing HFP session. No evidence this repairs its HAL/Telecom state. |

## New repair experiment

With the option off, the existing modern attempts still run first. With the
option explicitly enabled, a **manual** attempt first creates fresh silent speech output
and an AudioRecord VOICE_COMMUNICATION input (8-kHz mono PCM16). Both require unique
address-matched, nonzero-ID SCO ports with the correct input/output roles. Output
and input preferences are separate from AudioManager communication selection.
Fresh native stream creation and closing may clear an input/output-session fault
that routing alone leaves untouched. **This is a hypothesis, not a proven Samsung
fix or a way to restart audioserver/HAL/DSP.**

SCO alone cannot pass duplex: require actual exact-target routes for both streams,
known unsilenced capture, and delivered frames. Samples are discarded and cleared,
never saved, transmitted, analysed or included in a log. Reads require exact Bluetooth route evidence before and after each nonblocking
read. The OS can change routing between these observations; no atomic routing
lock is exposed, and samples are always discarded even in that race. Opening capture can briefly
activate a different mic before Android applies the requested route; the runtime
explanation discloses this and Android displays its microphone indicator.

Microphone permission and the preference default are independent: RECORD_AUDIO
alone never enables the method. Automatic idle tests never use this capture path.
Existing or newly appearing foreign recording sessions block/cancel capture;
only publicly exposed session IDs are compared, never another app name or audio.
Missing ports/permission, mic mute, unknown capture state or incorrect route cannot
be treated as success. Native errors (including ERROR_DEAD_OBJECT) terminate the
attempt, release own resources, and require confirmed SCO-off before a different
method. No automatic infinite recorder recreation or server-death reset claim.

The optional method adds one attempt and raises the state-machine budget from 70
to 90 seconds. Discovery/attempt/teardown dwell and original cooldowns remain.
Native Binder calls are not forcibly interruptible; budgets do not guarantee a
wedged system call returns on time. Activity pause, new call, revoked mic grant or
option disabled cancels and closes the owned streams. Never restore mic mute,
volume or another process's mode. Partial acceptance retains cleanup ownership;
failed release prevents further tests in this controller session.

## Primary references

- [Android audio architecture / HAL](https://source.android.com/docs/core/audio/implement)
- [Android 14 AudioService](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/services/core/java/com/android/server/audio/AudioService.java): setBluetoothScoOn ordinary-app short circuit, mode permissions, server recovery.
- [Android 14 audioserver init](https://android.googlesource.com/platform/frameworks/av/+/refs/heads/android14-release/media/audioserver/audioserver.rc): native and dependent-HAL lifecycle, not an app API.
- [AudioRecord](https://developer.android.com/reference/android/media/AudioRecord), [AudioTrack](https://developer.android.com/reference/android/media/AudioTrack), [AudioRouting](https://developer.android.com/reference/android/media/AudioRouting): public stream APIs, advisory preferences and actual routing.
- [Android 14 process restriction](https://developer.android.com/about/versions/14/behavior-changes-all#kill-background-processes)
- [Shizuku privilege distinctions](https://github.com/RikkaApps/Shizuku-API): ADB is not root; omitted from shipped code.
- [Samsung owner reports](https://eu.community.samsung.com/t5/galaxy-a-series/galaxy-a52s-5g-bluetooth-broken-after-last-update-security-from/td-p/11872914/page/12): SAP/offload/dependency ideas remain conflicting anecdotes. No verified service name or root cause for this owner's build.

## Required device validation

Compare the same car/headset, without and with the optional duplex attempt. Export
before reboot, during the failed next call and after recovery. Record actual
speech audibility in both directions. Test call arrival, screen lock, permission
revocation and headset disconnection during capture. If streams/SCO pass but the
next call fails, report **no confirmed repair**; a firmware/vendor-level fault can
remain unreachable without extra privileges. The phone reboot remains the proven
fallback. Test coverage verifies our control flow, not proprietary hardware.
