# Testing

The goal is a repair that works hands-free in the car without ever turning a hypothesis into a broad or destructive reset.

## Test layers

Coverage reports and enforced module thresholds: [docs/COVERAGE.md](docs/COVERAGE.md).

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
- With automatic repair switched off, automatic observation remains read-only; an explicit manual reconnect is separately authorized and bounded.
- Diagnostics never change the audio mode (instrumentation smoke test).

## Real-device protocol (v0.20.0)

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

For v0.20 also test [manual and endpoint scenarios](docs/RECOVERY_OPTIONS.md#device-validation), including paused protection, native failure, unknown evidence, multiple endpoints, LE Audio, permission recovery and preserving microphone mute. The status-screen integration test exercises both buttons against a bound fake call. CI screenshots cover English/German and light/dark UI.

Status: the supplied reports predate v0.20. No physical A52s call has been performed in this workspace; these checks remain pending on the affected phone.

For v0.20.1 exercise delayed SCO disconnect, persistent SCO during the phone hop,
route/SCO rebounds and unknown SCO/LE Audio. No Bluetooth return should happen over
an observed connected old SCO link. Check the 500 ms/1.5 s stable dwell and 4 s
abort/backoff. Compare failure and post-reboot reports as described in
[reboot-only recovery](docs/REBOOT_RECOVERY.md). Bluetooth off/on is explicitly not
the owner's proven fix. These timing thresholds and audible recovery require
physical A52s/car validation; only the phone restart has been confirmed by the owner.

A passing CI build proves the code-level invariants only. Whether the Telecom-level rebuild clears the vendor fault must be confirmed with real calls on the affected phone.

For v0.20.3, run the [report-derived device scenarios](docs/REPORT_0202_ANALYSIS.md#physical-validation-still-required). Verify explicit legacy targets when modern endpoints are absent, retained car selection with a second peripheral, no routing after target disappearance and no premature repeated request. Updates open only the official release in the browser; verify that package-installation permission is absent and diagnostic sharing still works. A Play Protect classification requires separate investigation as described in [installation warnings](docs/INSTALLATION_WARNINGS.md).

## Real-device protocol for v0.21.0

Only physical speech establishes success. While parked, answer through the same car
without touching the phone. If routing fails, export during the call and after it.
Confirm that the last bounded attempt logs observation refresh, an alternate
Telecom request, a 2-second settled phone dwell and, only if an exact AudioManager
device mapping exists, the communication-device request. False/rejected requests
must remain unresolved. Check that `communicationRequest` becomes false no later
than the scheduled 15-second cleanup (Binder failures are retried with backoff).
A temporary predicted Bluetooth route created by our own request is excluded
from positive recovery evidence. Observe the call after cleanup too.

Repeat with speaker selection, hold/resume, a second call, disabled protection and
car disappearance while another headset stays connected. No return may override
these guards. With two devices and no safe UUID/address mapping, alternate API
routing is refused rather than guessed. Check the second manual attempt and its
separate limits; closing the call/service must cancel this app's AudioManager
request. Compare a failing call against one after the owner's proven phone reboot.
These scenarios remain physically unverified until exercised on the A52s.

## 0.22.0 off-call channel recovery — physical validation pending

Automated tests cover state timing, short SCO pulses, per-target matching, permission
and call guards, finite fallback order, incoming-call preemption, activity pause,
mode/mute/volume preservation and Binder-after-acceptance/cleanup failures. CI runs
Robolectric on SDK 31/34/36 and core JUnit with combined coverage gates. The new
public APIs cannot be validated against Samsung's vendor implementation in CI.

On the affected A52s and the parked car/headset:

1. Export a baseline while the fault exists, before reboot. End all calls and pause
   music. Connect exactly one classic HFP device. Start the new off-call test,
   read the phone-status explanation, grant the optional permission and keep the
   app visible. Export the report afterwards, regardless of outcome.
2. Verify the UI distinguishes CHANNEL_TESTED (stable SCO on AND off) from
   UNRESOLVED, BLOCKED or CLEANUP_FAILED. Accepted requests must not report a
   repaired cellular call. Verify mic mute and call volume did not change.
3. Make a cellular call, answer on the car without touching the phone, verify
   actual incoming speech and microphone operation. Export during and after the
   call. Repeat without an idle test for comparison. A successful idle test can
   still be followed by a failed carrier call.
4. If ordinary off-call tests fail, enable the optional voice-session fallback and
   repeat; note car voice UI/tones. Do not run a separate assistant simultaneously.
   Export native-state/support/timeout/release observations and next-call result.
5. Interrupt each stage by an incoming/outgoing call, Home, screen lock, permission
   revocation, headset removal/replacement and connecting a second headset.
   Check no further attempt starts, no alternate device is selected, call audio
   is not torn down by voice-session cleanup and no mic/mode request remains.
6. Check opted-in auto tests only while the app is visible, no repeats within ten
   minutes, protection pause disables auto, manual attempts have 30-second
   cooldown and background failed calls are deferred to the next visible session.
7. Kill the app process mid-test, reopen, verify interrupted result is unresolved
   and controls are usable. Inspect audio state independently; system Binder
   cleanup is not proof the firmware released a stuck transport.
8. Compare new and previous APKs with the same signing key and increasing version
   code. READ_PHONE_STATE is new and optional; original in-call protection must
   work without it. Google Play Protect classification remains unverified.

Do not count an idle test as a repaired call in call-history statistics. Reboot
remains the only proven owner workaround until the above confirms otherwise.

## v0.23 manual duplex validation

Run the extended core/Robolectric tests and combined coverage gates. Verify the
capture source is standard VOICE_COMMUNICATION, reads are nonblocking, unknown or
silenced capture and mismatched real routes cannot pass, samples are erased and
partial native failures retain cleanup ownership. With both options enabled,
automatic idle tests must still never create capture. The normal permission dialog
must appear only for an explicitly enabled manual test, and cancelling must not
start capture. No root/Shizuku/shell/hidden API dependency is installed.

On the actual A52s, export before/after the optional manual attempt and during the
next phone call. Compare audible speech in both directions with the same device.
Test incoming call, mic permission revocation/privacy mute, headset disappearance,
app pause and screen lock during capture. Android should show the mic indicator
only during the bounded attempt and it must disappear after cleanup. Native init
inventory can be unavailable; it must never imply a daemon actually restarted.
A successful off-call duplex check does not establish repaired cellular speech.
