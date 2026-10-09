# Testing

The goal is a repair that works hands-free in the car without ever turning a hypothesis into a broad or destructive reset.

## Test layers

1. **`:core` JVM tests** (`./gradlew :core:test`): fault classification, the per-call repair state machine with a simulated clock and a simulated Telecom, call-report encoding and statistics, version and device helpers. Every safety rule lives here.
2. **Robolectric tests** (`:app:testDebugUnitTest`, runtime-relevant tests run on Android 14 and 16):
   - Telecom constant mapping;
   - an end-to-end `CallSession` with a fake Telecom host (preventive rebuild, stuck-on-phone repair, persistence);
   - Android 14+ endpoint observations, Bluetooth receiver lifetime, denied permissions, route-command failures and callbacks after hang-up;
   - repository, log rotation, update parsing and verification;
   - activity and navigation smoke tests;
   - runtime device detection and defaults (A52s, S22, other phones);
   - the launcher icon rendered as squircle, circle and themed icon;
   - localization: every `values-xx/` translation covers all keys with identical placeholders; English is the fallback;
   - rendered screenshots of every screen in light and dark, in English and German (`app/build/screenshots/`, uploaded by CI).
3. **Instrumentation** (`androidTest`): read-only smoke checks on a real device.
4. **Real A52s calls:** the only layer that can prove the HFP/SCO fault is repaired.

## Safety invariants (enforced by `CallRepairEngineTest`)

- No action without a confirmed fault: 3 s for "call not on Bluetooth", 2 s for SCO/downlink faults, 1.5 s for muted volume. Transient faults are ignored.
- At most 3 route operations and 2 volume restores per call; every operation is verified before the next one.
- Speaker and wired headsets are never overridden, not even in the middle of a rebuild.
- Leaving established Bluetooth audio stops repairs until Bluetooth is selected again. Establishing that link requires 2.5 s of continuous positive evidence during a single audio-carrying call; ringing time, failed selections and unknown audio evidence do not count. Exception: the first drop to the phone within 8 s of one of our own route changes is repaired once; a further drop from working audio is respected.
- Ignored direct route requests can escalate to a teardown/return sequence within the same attempt budget. Multiple calls defer repairs without spending another attempt.
- A confirmed fault clears only after 2.5 s of positive SCO or voice-route evidence without a conflicting fault. Missing evidence or a brief healthy pulse is not verified recovery.
- The preventive rebuild runs at most once, only within the first 30 s, only on a single call, and only after 1 s of steady Bluetooth.
- "HFP connected but no Bluetooth route" is reported, never "repaired".
- With automatic repair switched off, the engine observes and reports only.
- Diagnostics never change the audio mode (instrumentation smoke test).

## Real-device protocol (v0.19.1)

Setup:

1. Install the release APK. Allow Bluetooth access and link the car (or run the adb command from the setup screen).
2. Confirm that the status screen shows **Schutz aktiv**.

Healthy path:

3. Make a call through the car. With preventive rebuilding enabled, expect a short switch to the phone and back after the call connects. Afterwards **Verlauf** should show the preventive rebuild. With prevention off, healthy audio should receive no route command.
4. During a call, switch to the phone speaker yourself. The app must not switch back.

When the fault occurs ("they hear me, I hear nothing"):

5. Do nothing on the phone. Note whether audio came back within a few seconds.
6. When parked, open **Verlauf**. Expected outcomes are "Automatisch repariert", "Problem blieb bestehen" or "Bluetooth-Telefonie blockiert". Then share the diagnostic report (Einstellungen → Diagnosebericht teilen) before rebooting.
7. If possible, also run `scripts/capture-call-audio-state.sh` before rebooting.

The `CALL …` lines in the report show the Telecom route, the SCO link, the voice route and the volume for every state change, and the repair command the engine chose.

For v0.19 also exercise the [additional device cases](docs/CALL_REPAIR_RESILIENCE.md#real-device-validation-still-required): incoming/outgoing calls, repeated failed selections, Bluetooth off/on, hold/call waiting, two peripherals, permission revocation and hang-up during a pending profile bind. Export once during the call and again afterwards. A live report is provisional.

For v0.19.1 also run the [hands-free answer scenarios](docs/CALL_REPAIR_RESILIENCE.md#hands-free-answer-without-phone-input) without touching the phone. A long ringing interval and a brief positive audio pulse must not suppress repair of an immediate answer fallback.

Status: the supplied reports predate v0.19. No physical A52s call has been performed in this workspace; these checks remain pending on the affected phone.

A passing CI build proves the code-level invariants only. Whether the Telecom-level rebuild clears the vendor fault must be confirmed with real calls on the affected phone.
