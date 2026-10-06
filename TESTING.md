# Testing strategy

The goal is to develop the A52s Bluetooth repair experimentally without turning an unverified hypothesis into a broad or destructive Bluetooth reset.

## TDD rule

Every bug fix starts with a failing test or a captured reproducible device scenario. Pure decision logic belongs outside Android framework classes so it can be exercised by fast JVM tests. Android API integration gets Robolectric tests where practical and on-device instrumentation tests where framework behavior matters.

## Test pyramid

1. **Pure JVM tests**: health classification, repair eligibility, state transitions, cooldowns and safety invariants.
2. **Robolectric tests**: persistence and Android-facing UI behavior that can be simulated reliably.
3. **Instrumentation tests**: read-only diagnostics and carefully scoped integration checks on a real Android device.
4. **Manual A52s fault reproduction**: the only layer that can prove the real HFP/SCO failure has been fixed.

## Non-negotiable safety invariants

Automatic repair is opt-in through Auto-Schutz. It may only act when all of the following are true: Bluetooth permission is present, Bluetooth is enabled, an active call/communication mode is detected, a Bluetooth SCO/HFP communication device is available, that Bluetooth device is not currently selected, the same suspect condition has been observed twice consecutively, speakerphone is not explicitly active, and the repair cooldown has elapsed.

The automatic path may only clear/reselect the public Android communication route. It must not toggle Bluetooth, clear Bluetooth app data, restart vendor/system services, change `AudioManager` mode, or attempt hidden/private API resets. Every repair attempt and verification snapshot must be logged.

## Required regression scenarios

- Bluetooth permission missing.
- Bluetooth disabled.
- Bluetooth enabled but no active call.
- Active call with no SCO/HFP endpoint.
- Healthy active Bluetooth SCO/HFP route.
- Suspect active route for one sample: no automatic repair yet.
- Suspect active route for repeated samples: eligible for automatic repair.
- Active speakerphone route: automatic repair blocked.
- Cooldown prevents repair thrashing.
- Diagnostic logging appends rather than destroys earlier evidence.
- Snapshot contains communication device, available communication devices and output devices.
- Diagnostic snapshot does not alter `AudioManager` mode.
- Auto-Schutz preference and last state survive Activity recreation/app restart.

## Real A52s protocol

Capture a known-good snapshot and test call after reboot. Enable Auto-Schutz and leave it active until the failure occurs. When possible, record the exact time of the failed call. Before rebooting, open the app and export the log.

For the first real-device validation of v0.4.0, verify separately:

1. A healthy call through the car remains untouched.
2. Selecting phone speaker intentionally is not immediately overridden by the watchdog.
3. When the failure occurs and Android still exposes a SCO/HFP endpoint, the log shows `SUSPECT_ROUTING`, two consecutive observations, a route-repair attempt and a verification result.
4. If Android exposes no SCO/HFP endpoint, the app reports `CALL_WITHOUT_SCO` and does not claim success.
5. Stopping Auto-Schutz removes the persistent service behavior and clears any route requested by the app.

A passing CI build proves only that code-level invariants hold. It does **not** prove the Samsung/Qualcomm HFP/SCO defect is fixed. That requires reproduction on the affected Galaxy A52s 5G.
