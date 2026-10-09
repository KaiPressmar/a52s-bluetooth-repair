# Testing

The goal is a repair that works hands-free in the car without ever turning a hypothesis into a broad or destructive reset.

## Test layers

1. **`:core` JVM tests** (`./gradlew :core:test`): fault classification, the per-call repair state machine with a simulated clock and a simulated Telecom, call-report encoding and statistics, version and device helpers. Every safety rule lives here.
2. **Robolectric tests** (`:app:testDebugUnitTest`, runtime-relevant tests run on Android 14 and 16):
   - Telecom constant mapping;
   - an end-to-end `CallSession` with a fake Telecom host (preventive rebuild, stuck-on-phone repair, persistence);
   - repository, log rotation, update parsing and verification;
   - activity and navigation smoke tests;
   - runtime device detection and defaults (A52s, S22, other phones);
   - the launcher icon rendered as squircle, circle and themed icon;
   - rendered screenshots of every screen in light and dark (`app/build/screenshots/`, uploaded by CI).
3. **Instrumentation** (`androidTest`): read-only smoke checks on a real device.
4. **Real A52s calls:** the only layer that can prove the HFP/SCO fault is repaired.

## Safety invariants (enforced by `CallRepairEngineTest`)

- No action without a confirmed fault: 3 s for "call not on Bluetooth", 2 s for SCO/downlink faults, 1.5 s for muted volume. Transient faults are ignored.
- At most 3 route operations and 2 volume restores per call; every operation is verified before the next one.
- Speaker and wired headsets are never overridden.
- When the call leaves Bluetooth without our command (user or car chose the phone), the engine stops acting for that call.
- The preventive rebuild runs at most once, only within the first 30 s, only on a single call, and only after 1 s of steady Bluetooth.
- "HFP connected but no Bluetooth route" is reported, never "repaired".
- With automatic repair switched off, the engine observes and reports only.
- Diagnostics never change the audio mode (instrumentation smoke test).

## Real-device protocol (v0.16)

Setup:

1. Install the release APK. Allow Bluetooth access and link the car (or run the adb command from the setup screen).
2. Confirm that the status screen shows **Schutz aktiv**.

Healthy path:

3. Make a call through the car. Expect a short (≈1 s) switch to the phone and back after the call connects: that's the preventive rebuild. Afterwards **Verlauf** shows "Alles in Ordnung · Vorsorglich neu aufgebaut".
4. During a call, switch to the phone speaker yourself. The app must not switch back.

When the fault occurs ("they hear me, I hear nothing"):

5. Do nothing on the phone. Note whether audio came back within a few seconds.
6. When parked, open **Verlauf**. Expected outcomes are "Automatisch repariert", "Problem blieb bestehen" or "Bluetooth-Telefonie blockiert". Then share the diagnostic report (Einstellungen → Diagnosebericht teilen) before rebooting.
7. If possible, also run `scripts/capture-call-audio-state.sh` before rebooting.

The `CALL …` lines in the report show the Telecom route, the SCO link, the voice route and the volume for every state change, and the repair command the engine chose.

A passing CI build proves the code-level invariants only. Whether the Telecom-level rebuild clears the vendor fault must be confirmed with real calls on the affected phone.
