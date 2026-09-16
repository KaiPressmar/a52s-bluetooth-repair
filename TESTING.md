# Testing strategy

The goal is to develop the A52s Bluetooth repair experimentally without turning an unverified hypothesis into an automatic, disruptive fix.

## TDD rule

Every bug fix starts with a failing test or a captured reproducible device scenario. Pure decision logic belongs outside Android framework classes so it can be exercised by fast JVM tests. Android API integration gets Robolectric tests where practical and on-device instrumentation tests where framework behavior matters.

## Test pyramid

1. **Pure JVM tests**: repair policy, state transitions, escalation thresholds, cooldowns and safety invariants.
2. **Robolectric tests**: diagnostic persistence and Android-facing behavior that can be simulated reliably.
3. **Instrumentation tests**: read-only diagnostics and carefully scoped integration checks on a real Android device.
4. **Manual A52s fault reproduction**: the only layer that can prove the real HFP/SCO failure has been fixed.

## Non-negotiable safety invariants

Automated repair logic must not modify routing while a call/communication session is active. It must not act when Bluetooth is disabled or no Bluetooth SCO endpoint is present. Repeated suspicious observations are required before escalation. Diagnostics must remain read-only. A repair attempt must be logged before and after execution. New repair strategies remain manual/opt-in until real-device evidence demonstrates they are safe and useful.

## Required regression scenarios

- Bluetooth disabled.
- Bluetooth enabled but no car/headset SCO endpoint.
- Healthy Bluetooth SCO route.
- Suspect route for one sample (transient state).
- Suspect route for repeated samples.
- Active call/communication session always blocks automatic mutation.
- Diagnostic logging appends rather than destroys earlier evidence.
- Snapshot contains stable keys used for later comparison.
- Diagnostic snapshot does not alter AudioManager mode.

## Real A52s protocol

Capture a known-good snapshot and test call after reboot. Leave monitoring active until the failure occurs. Before applying any repair, capture another snapshot and note the exact time. Apply exactly one repair strategy, then immediately repeat the same call test. Record success/failure and capture the post-action snapshot. Do not combine repair strategies in one experiment; otherwise the effective action cannot be identified.

A passing CI build proves only that code-level invariants hold. It does **not** prove the Samsung/Qualcomm HFP/SCO defect is fixed. That requires reproduction on the affected Galaxy A52s 5G.
