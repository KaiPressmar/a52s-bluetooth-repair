# Bluetooth call recovery implementation backlog

Date: 15 October 2026. Status: ready to implement, with hardware-dependent release gates. This backlog translates the [technical roadmap](REFACTORING_PLAN.md), [research](RESEARCH_2026-10-15.md) and [community roadmap](COMMUNITY_PLAN.md) into ordered, reviewable changes. Application behavior has not been changed by creating this plan.

Current delivered behavior and remaining external gates are tracked in [implementation status](IMPLEMENTATION_STATUS.md). The original checklist below records the proposed work breakdown.

## Delivery objective

After initial access setup, a supported faulty Bluetooth call recovers automatically without opening the app, changing a route, toggling Bluetooth or restarting the phone. The implementation must preserve user choices, stop when authority or evidence is missing, and report the difference between restored routing and independently confirmed sound.

The first deliverable is an instrumented stock-A52s feasibility result. The second is a bounded recovery implementation. The third is a release backed by actual faulty-call recovery evidence. A simulated successful request is insufficient for the third deliverable.

## Implementation decisions

Retain Java, the existing `:core` and `:app` modules, existing package roots and the Telecom-bound service. Use additive interfaces and compatibility decoders to keep intermediate PRs buildable. Avoid a broad package rename, language migration, dependency injection framework, permanent foreground service or database migration unless later evidence requires one.

In the paths below, core paths are relative to `core/src/main/java/de/kaipressmar/a52srepair/core/`; Android paths are relative to `app/src/main/java/de/kaipressmar/a52srepair/`. New names describe planned classes, not files already present.

### Responsibility boundaries

| Component | Responsibility | Must not do |
| --- | --- | --- |
| `CallAudioService` | Receive platform callbacks and forward lifecycle events | Make recovery policy decisions |
| `TelecomAudioController` | Translate endpoint observations and requests | Infer audible success |
| `CallSession` | Serialize observations, reducer events, journal acknowledgments and execution | Hide failures or reset budgets on rebind |
| `CallRepairEngine` | Pure state transition and command proposal | Read clocks, Android objects or storage |
| `CommandDispatcher` | Check current eligibility, reserve budget durably and invoke one adapter | Retry on its own |
| `HeadsetMonitor` | Collect target-specific HFP evidence | Treat another accessory's audio as target health |
| `VoiceStream` | Read volume and route hints; execute explicitly authorized volume command | Treat predicted routing as actual speech delivery |
| `RecoveryJournalRepository` | Durable checkpoints and interruption recovery | Store phone numbers or call handles |
| `CallReportRepository` | Versioned history and migrations | Turn unknown outcomes into successful audio |
| `DiagnosticReport` | Export allowlisted evidence | Dump arbitrary platform objects |

### Proposed interfaces

These signatures define internal contracts. Android SDK objects remain inside the Android adapters.

```java
interface MonotonicClock {
    long nowMs();
}

interface SessionScheduler {
    void schedule(long generation, long deadlineMs, Runnable task);
    void cancel(long generation);
}

interface TelecomAudioController {
    EndpointInventory inventory();
    void request(EndpointKey endpoint, RequestToken token);
    void close();
}

interface RecoveryJournalRepository {
    void reserve(Reservation reservation, JournalCallback callback);
    void finish(SessionCheckpoint checkpoint, JournalCallback callback);
}
```

`EndpointKey`, `RequestToken`, `EndpointInventory`, `Reservation` and `SessionCheckpoint` are immutable types. `RequestToken` carries generation, transaction ID and sequence number. Adapter callbacks feed `CommandResult` and observation events into the session dispatcher; none calls the engine recursively.

Use a single serialized journal writer. After a durable reservation completes, return to the session dispatcher and recheck generation, eligibility, deadline and target availability before invoking the platform. A failed reservation sends no command. A canceled reservation may consume budget conservatively; record reserved and actually dispatched counts separately.

## Ordered pull requests

Every checkbox below begins uncompleted. Existing tests remain useful, but tests that equate a fake route transition with verified sound must be corrected.

### PR 01 Establish baseline and prove call access

Dependencies: none. Estimate: 2–4 engineering days plus device availability.

- [ ] Add `docs/validation/ACCESS_PROTOCOL.md` with separate consumer-association and ADB enrollment trials.
- [ ] Extend diagnostic setup evidence with app version, firmware, enrollment method, current app-op, last bind time and whether a real request was observed to change a route.
- [ ] Add an instrumentation test file `CallAccessInstrumentedTest.java` for read-only checks and an explicit laboratory invocation protocol; never place call mutations in the ordinary smoke test.
- [ ] Test stock SM-A528B Android 14 with Samsung Phone retained as the calling UI. Record whether the car/watch-profile association is accepted and whether Telecom binds the service.
- [ ] Repeat after process recreation, normal reboot and app update; test grant revocation separately.
- [ ] Create an initial compatibility record with unverified fields explicitly unknown.

Completion: a sanitized evidence record establishes access and routing authority for each enrollment method tested, or records a concrete failure. Historical binding does not prove current authority. No production status is promoted solely because an app-op is allowed.

Gate: if consumer enrollment fails, continue pure-core, reporting and community work, but do not claim unattended consumer recovery. Do not silently substitute a default dialer or privileged bridge.

### PR 02 Correct capability and outcome vocabulary

Dependencies: PR 01 diagnostic field definitions; hardware evidence may still be pending. Estimate: 2–3 days.

- [ ] Add core `model/Evidence.java` with value, status, source and observation time; add stale/unknown handling tests.
- [ ] Add `model/RecoveryCapabilities.java` and Android `setup/CapabilityEvaluator.java`.
- [ ] Refactor `ProtectionStatus` to separate access missing, configured but not observed, currently bound, paused, Bluetooth off and unsupported configuration. Keep current capability and historical validation separate.
- [ ] Rename the meaning of `voiceOnBluetooth` to `predictedVoiceRoute` through a temporary compatibility accessor; update `VoiceStream` documentation and diagnostic labels.
- [ ] Introduce distinct report fields for intervention, command result, route/transport observation and audible confirmation.
- [ ] Update EN/DE status/history strings and README claims affected by these distinctions.

Completion: tests demonstrate that unknown probes and metadata-only success never produce an audio-confirmed label. A healthy-looking snapshot says no observable fault, not guaranteed sound.

### PR 03 Extract time and scheduling seams

Dependencies: PR 02. Estimate: 1–2 days.

- [ ] Inject `MonotonicClock`, `SessionScheduler`, controller and report store into `CallSession`; retain a production constructor that wires the Android implementations.
- [ ] Route all events through one dispatcher and replace loosely scheduled callbacks with generation-tagged work.
- [ ] Add deterministic fakes for delayed, missing, rejected, duplicated and reordered callbacks.
- [ ] Preserve existing behavior at this extraction step except corrected result vocabulary.

Completion: session tests advance a virtual clock without wall-clock sleeps; finish is idempotent; a callback from an old generation cannot dispatch a command.

### PR 04 Introduce endpoint controllers

Dependencies: PR 03. Estimate: 3–5 days.

- [ ] Add Android `telecom/TelecomAudioController.java`, `EndpointTelecomController.java` and `LegacyTelecomController.java`.
- [ ] Add core `model/EndpointKey.java`, `EndpointInventory.java`, `EndpointKind.java` and `repair/CommandResult.java`.
- [ ] Have `CallAudioService` forward endpoint changes, inventory changes, request results and legacy route callbacks to the selected adapter.
- [ ] Use the current platform-provided endpoint inventory on the modern path; keep platform endpoint objects inside the adapter.
- [ ] Preserve the older route path in one adapter with explicit limitations and observed-target verification.
- [ ] Invalidate removed endpoint keys. Do not persist ephemeral IDs as accessory identities or automatically cascade from a rejected modern request into legacy mutations.

Completion: adapter tests select the correct implementation and simulate disappearing endpoints and request errors. A hardware trace shows a directed request reaching the selected endpoint. No audible-success assertion is inferred from that trace.

### PR 05 Scope observations to the intended accessory

Dependencies: PR 04. Estimate: 2–4 days.

- [ ] Add Android `setup/ProtectedAccessoryRepository.java` and `telecom/EndpointResolver.java`.
- [ ] Keep the minimum platform identifier needed for local association privately; exports use report-local aliases. Do not expose that identifier through domain `toString()` methods.
- [ ] Change `HeadsetMonitor` queries from any connected device to the resolved target; return unknown when resolution is ambiguous.
- [ ] Guard proxy callbacks arriving after close and close a late proxy exactly once.
- [ ] Add evidence freshness and explicit transport kind; unsupported BLE/hearing-aid evidence cannot become Classic SCO failure.
- [ ] Separate initial-routing consent from recovery of a previously selected Bluetooth call.

Completion: two connected accessories, duplicate names, a disappearing target and an unrelated active SCO link never cause repair of the wrong device. If identity cannot be resolved, intervention is suppressed.

### PR 06 Put eligibility and user choice ahead of every transition

Dependencies: PRs 02–05. Estimate: 2–4 days.

- [ ] Add core `repair/EligibilityEvaluator.java`, `EligibilityDecision.java` and `UserChoiceTracker.java`.
- [ ] Extend call context with active call identity, topology, emergency knowledge, supported call type and external/streaming state.
- [ ] Refactor `CallRepairEngine.onSnapshot()` so eligibility precedes advancing an in-flight operation.
- [ ] Cancel pending mutations on hold, extra call, conference, emergency or unknown emergency status, disabled protection, permission loss or target ambiguity.
- [ ] Replace broad command-echo/grace forgiveness with transaction-specific expected transitions and an explicit yielded state.
- [ ] Leave ordinary handset calls unchanged when an unused Bluetooth accessory is connected.

Completion: table-driven tests introduce every disqualifier at every transaction phase and observe no subsequent mutation. Document the remaining older-platform ambiguity when a user selects the same endpoint as an in-flight app request.

### PR 07 Replace repair attempts with bounded transactions

Dependencies: PR 06. Estimate: 4–6 days.

- [ ] Replace enum-only `RepairCommand` with a typed immutable request carrying target, reason and token; migrate all call sites in this PR.
- [ ] Introduce `RecoveryTransaction`, `RecoveryBudget` and explicit engine events for observations, command results and deadlines.
- [ ] Count all requested endpoint mutations, including preventive actions and failed calls to the platform. Keep actual dispatch and conservative reservations distinguishable.
- [ ] Enforce four endpoint requests maximum, one rebuild maximum and one explicitly scheduled return retry maximum per session.
- [ ] Enforce one absolute 15-second recovery deadline from first mutation; callbacks and event storms cannot extend it.
- [ ] Require observed leave before labeling a rebuild complete. Use a real earpiece, reserve return budget before leaving, and yield to unexpected route choices.
- [ ] Stop automatic escalation for absent endpoints, denied authority and exhausted budgets.

Completion: deterministic event sequences prove exact maximum request counts, no repeated per-tick retries and no mutation after terminal state. Tests cover leave timeout, target loss, late success, return rejection and deadline expiry.

### PR 08 Add durable reservations and rebind recovery

Dependencies: PR 07. Estimate: 3–4 days.

- [ ] Add Android `data/RecoveryJournalRepository.java`, `RecoveryJournalCodec.java` and `telecom/CommandDispatcher.java`.
- [ ] Use an atomic versioned checkpoint file, a serialized writer and acknowledged durable reservations before platform dispatch.
- [ ] Recheck all gates after storage acknowledgment; capture reservation failure and timeout as blocked outcomes.
- [ ] Store generation, transaction, policy version, private target key, spent budget, preventive marker and last state. Exclude telephone handles and remote-party identity.
- [ ] On rebind, reconcile current platform state; never replay a command blindly. When matching is uncertain, suppress another preventive transaction and mark interrupted observation.
- [ ] Define boot/session invalidation for monotonic timestamps; do not compare elapsed times across boots.

Completion: crash fixtures at before-reservation, after-reservation, before-dispatch, after-dispatch and before-result preserve conservative budgets. No process recreation duplicates preventive recovery.

This PR completes production integration of the new transaction engine. Before it lands, keep the new engine disabled for production mutations so partial integration cannot reset budgets.

### PR 09 Migrate reports and add structured diagnostics

Dependencies: PR 08. Estimate: 3–4 days.

- [ ] Add a core `report/CallReportCodec.java` supporting existing semicolon format version 1 and an explicit version 2 format.
- [ ] Keep a separate version 2 history file so an older application cannot rewrite and discard new records. Import version 1 idempotently and retain its source file until migration is verified.
- [ ] Preserve old outcomes as legacy evidence with audible confirmation unknown. Unknown future fields/statuses must not become success.
- [ ] Check atomic replacement results in `CallReportRepository` and expose storage failures without blocking call handling.
- [ ] Add `DiagnosticEvent` and an allowlisted export serializer; remove raw platform objects and arbitrary exception messages from normal reports.
- [ ] Bound history and event retention; update clear-data controls and backup exclusions for new files.

Completion: fixtures cover mixed versions, malformed entries, interrupted migration, repeated import, storage failure and downgrade/re-upgrade. Synthetic personal values never appear in standard export. Exported request counts reconcile with the journal.

### PR 10 Introduce explicit recovery policies

Dependencies: PRs 08–09 and usable PR 01 results. Estimate: 2–4 days.

- [ ] Add core `repair/RecoveryPolicy.java` and Android `data/RecoveryPolicyRepository.java` with bundled, versioned local profiles.
- [ ] Separate reactive routing, experimental prevention and optional volume restoration settings.
- [ ] Scope AFTER_PROBLEMS history by firmware and protected accessory; invalidate confidence after incompatible policy/firmware changes.
- [ ] Run prevention at most once for one active, stable, eligible Bluetooth call. It spends the common budget and respects all cancellation gates.
- [ ] Replace the 60-percent volume target with an explicitly approved prior level; initially ship volume mutation disabled for unverified configurations.
- [ ] Migrate current settings transparently. Preserve the user's protection switch and explicit choices; explain any safety-driven disabling of a formerly implicit default in a release note and settings status.

Completion: no device-family match alone enables a validated claim. OFF stays off, prevention cannot repeat after rebind, and unknown silence remains unknown after a route bounce.

### PR 11 Finish the support and contribution loop

Dependencies: PR 09 export schema; can develop alongside PR 10. Estimate: 2–3 days.

- [ ] Extend existing issue forms with exact app version, enrollment method, call type, firmware/accessory context, actual fault count and manual-intervention count.
- [ ] Extend existing `.github/pull_request_template.md` and `.github/CODEOWNERS` rather than duplicating them. Preserve the existing maintainer assignment; add owners only when agreed.
- [ ] Add `docs/COMPATIBILITY.md`, a small versioned evidence schema and a validation script. Seed the VW report as a reported failure with truncated evidence, not a repaired case.
- [ ] Add privacy documentation and update SUPPORT with safe, optional diagnostic collection.
- [ ] Add CI checks for local links, compatibility records and issue-form structure while retaining existing Gradle checks.
- [ ] Prepare a repository-settings checklist for labels, Discussions and branch rules; record settings as configured only after owner-side verification.

Completion: a nondeveloper can report a result without private logs, and a reviewer can distinguish a stable observation from a recovered faulty call.

### PR 12 Validate hardware and prepare the release

Dependencies: PRs 01–11. Estimate: 1–3 calendar weeks minimum, dependent on fault recurrence.

- [ ] Run the hardware matrix and prevention comparison in the technical roadmap.
- [ ] Observe both speech directions physically, including excluding accidental handset microphone use.
- [ ] Collect at least 50 eligible calls over seven days per claimed configuration and seek at least 10 reproduced fault episodes; retain experimental status when evidence is insufficient.
- [ ] Publish successes, failures, request counts, interruptions, time to restoration and manual corrective actions. Keep zero-fault periods distinct from recovery efficacy.
- [ ] Require no unexplained route override or prohibited-call mutation and evaluate the proposed 95-percent recovery target against actual counts.
- [ ] Update compatibility status, changelog, known limitations and rollback instructions; preserve current release-branch and signing conventions.

Completion: the release claims only what its evidence supports. Failed consumer access or unrecoverable vendor silence remains a documented limitation rather than a hidden manual step.

## Dependency and milestone schedule

| Milestone | Required PRs | Usable result |
| --- | --- | --- |
| M0 Access understood | 01 | Stock-phone authority evidence and go/no-go decision |
| M1 Honest observation | 02–05 | Testable lifecycle, target resolution and accurate status |
| M2 Bounded recovery | 06–08 | Eligibility-first, durable, cancellable transactions |
| M3 Interpretable results | 09–11 | Policy controls, migrated reports and community intake |
| M4 Validated workaround | 12 | Scoped release with actual faulty-call evidence |

Engineering estimate: approximately 5–9 person-weeks for this expanded backlog, plus fault-observation time. This refines the earlier 4–7-week estimate by separating adapter extraction, durable dispatch and migration work. It is a planning range, not a delivery commitment. Early PRs may overlap in preparation, but integration follows dependencies; hardware validation cannot be replaced by more unit tests.

## Test execution and review rules

For implementation PRs, run the existing checks:

```sh
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Run focused core tests while developing a reducer change, then the full checks before review. Expand instrumentation only for behaviors that require the platform. Add coverage for API 31/33/34/36 when supported by the configured test runtime; a deliberate API 37 extension is a later compatibility task.

Each recovery PR must contain a concrete failing trace, expected command sequence and cancellation behavior. Keep synthetic traces free of identifiers. Freeze evidence fixtures for regressions so future policy changes cannot quietly weaken the invariant. Tests must verify effects and constraints rather than just copying implementation branches.

An implementation PR is done when its public behavior, tests, migration effects, diagnostics and documentation agree. A device-sensitive policy is ready for release only when its required hardware record also exists. Passing CI and merging a PR are separate from proving recovery efficacy.

## Rollback and interruption behavior

The local protection switch remains the immediate stop mechanism. Check it before every dispatch, including after storage acknowledgment. Never install an update during a call. On unexpected controller failure, invalidate current work, preserve the journal and observe; do not switch to a second mutation path automatically.

A rollback must preserve newer history files and policy data even when the older application cannot display them. Do not promise a simple APK downgrade: package installation restrictions and version codes must be handled through the existing distribution process. Prefer a forward release restoring conservative behavior while retaining compatible readers.

Remove temporary legacy accessors only after all consumers migrate. Retire the old engine dispatch path in the same PR that enables the new durable dispatcher. One session must never run both repair implementations.

## Immediate starting tasks

1. Implement PR 01 diagnostic evidence and the stock-phone access protocol.
2. Begin PR 02 capability/outcome models with fixtures for unknown and legacy reports.
3. Add PR 03 fake time and callback controls before changing transaction behavior.
4. Record hardware access results before enabling the new routing path for users.

The remaining checklist is the execution backlog. It does not require creating remote GitHub issues or assigning maintainers to start local implementation.
