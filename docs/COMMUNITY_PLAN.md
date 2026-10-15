# Repository quality and community collaboration plan

Plan date: 15 October 2026. This roadmap complements the [research](RESEARCH_2026-10-15.md) and [technical refactoring plan](REFACTORING_PLAN.md). It proposes repository changes and operating practices; it does not enable GitHub settings, publish releases, contact users, or create remote issues.

## Repository purpose and public promise

Position the project as an independent, evidence-driven Bluetooth call recovery tool for affected Galaxy A52s configurations. Explain the symptom in everyday language, show initial setup clearly, and distinguish API compatibility from verified recovery. Keep English and German support.

The README should lead with the observed failure, current experimental status, supported automatic actions, one-time access requirements, and known unrecoverable cases. Replace absolute claims such as “no battery use,” proven prevention, permanent protection or unattended self-updates. The current updater starts Android's installer; explain that confirmation separately from hands-free call recovery.

Keep the existing MIT license, Code of Conduct, SUPPORT, SECURITY, CONTRIBUTING, TESTING and release documentation. The repository already contains these foundations. Improve their consistency rather than adding duplicate policy documents for appearances.

## Documentation structure

| Destination | Purpose and owner |
| --- | --- |
| `README.md` | Maintainer owns installation, product status and links to deeper evidence |
| `docs/RESEARCH_2026-10-15.md` | Dated source analysis and baseline code audit; preserve as a historical review |
| `docs/REFACTORING_PLAN.md` | Implementation roadmap; update proposal status as work lands |
| `docs/COMMUNITY_PLAN.md` | Community and repository operating roadmap |
| Proposed `docs/COMPATIBILITY.md` | Evidence-backed firmware/accessory results, failures and observation periods |
| Proposed `docs/adr/` | Architectural decisions on access, endpoint APIs, budgets, privacy and distribution |
| Existing `docs/field-reports/` | Sanitized case summaries with explicit observation versus inference |
| Existing `TESTING.md` | Repeatable engineering and physical-call validation protocol |
| Existing `SUPPORT.md` | Short user support path; no driver must operate a phone to file a report |
| Proposed `PRIVACY.md` | Local records, export contents, retention, GitHub update connections and deletion controls |

Historical field reports should retain original observations. Add corrections as dated notes instead of rewriting them into evidence for a new theory. Research sources need access dates, relevant build/context and confidence. A maintainer should be able to explain every claimed compatibility result by following its evidence link.

## Compatibility records

Use a small reviewed data format such as YAML or JSON with a generated readable table. Do not expose persistent personal or device identifiers.

Required record fields: model, firmware, Android/One UI, stock/custom ROM, accessory model and firmware if known, app version, enrollment method, policy settings, call type, observation duration, call count, reproduced-fault count, automatic-recovery count, unsuccessful count, manual-intervention count and evidence issue.

Status values: UNTESTED, API_COMPATIBLE, REPORTED_WORKING, FAULT_RECOVERY_OBSERVED, REPEATEDLY_VALIDATED and KNOWN_LIMITATION. “No failures in 30 calls” belongs under reported working, not proven repair. Show last verification date and retain negative results. Avoid a single green supported badge spanning all Android releases.

## Issue intake

Extend the existing call-audio and field-report forms. Replace hard-coded version dropdowns with an exact version field. Provide “unknown” choices so nontechnical users can participate.

For a failed call, collect symptom direction, when it occurred, model/build, accessory, call type, app version, access method, selected policy, whether a route gap occurred, whether a manual action was needed, and a sanitized report if available. Distinguish “not tried” from “reboot required”; never instruct users to reboot just to answer the form.

For field results, ask for observation days, total eligible calls, actual fault episodes, automatically restored episodes, failed episodes, interruptions and manual corrections. Ask whether other settings changed. A stable period and a recovered reproduced fault are different evidence.

For an unintended intervention, add a dedicated regression option: chosen route, app action, call topology, approximate relative timing and whether the call was interrupted. Treat repeated unwanted route changes as higher priority than a missing cosmetic feature.

Keep diagnostic attachments optional. Explain that public GitHub issues are public, and full Android bugreports can contain personal information. Standard app exports should already be redacted. Provide a documented private security path for vulnerabilities; do not repurpose it as a general personal-log upload service.

## Triage and interaction

Proposed labels should describe independent dimensions:

| Dimension | Examples |
| --- | --- |
| Work type | `bug`, `research`, `field-report`, `documentation`, `enhancement` |
| Symptom | `audio:downlink`, `audio:uplink`, `audio:both`, `route:missing`, `route:fallback` |
| Workflow | `needs-triage`, `needs-information`, `reproduced`, `ready`, `blocked-platform`, `needs-device-validation` |
| Risk | `regression`, `user-choice`, `privacy`, `call-interruption` |
| Contribution | `good first issue`, `help wanted`, `translation` |

Do not encode every firmware or accessory as a label; keep those in report fields. Configure labels once with maintainer review and avoid automation that grants broad repository write access merely to label issues.

Triage flow: acknowledge the symptom, check privacy, classify setup versus route versus transport versus unknown silence, link similar cases, request one missing discriminating detail, then propose a reproducible next experiment. Accept English and German. Provide a short explanation when marking a platform limitation and keep an evidence issue open for genuinely unresolved research.

Proposed response target: initial triage within seven days when volunteer capacity permits, with a visible maintainer availability note. This is a target, not a service guarantee. Do not auto-close intermittent reports simply because the reporter cannot reproduce the fault promptly. Link duplicates without losing firmware/accessory differences.

GitHub Discussions categories should include Questions, Field results, Research, Ideas and Announcements. Promote a discussion into an issue when there is a concrete testable defect. Pin a getting-started thread and a current limitations thread. Publish occasional summaries of what changed, what the evidence shows and which experiment needs help. Community posting and outreach remain explicit maintainer actions; no automatic unsolicited messages.

### Example reply

“Thanks for the report. Music working while calls fail matches the symptom we are investigating. Your report shows Bluetooth selected, but it does not yet confirm whether the call transport stayed connected. When safely parked, please attach the app's sanitized report and give the exact firmware and app version. No further reset is needed for this report. We have linked the related cases and will keep your configuration separate in the results.”

## Contributor experience

Update CONTRIBUTING to point to current state-machine invariants, endpoint adapter boundaries, the development command and hardware evidence expectations. Add a short architecture walkthrough with one event-to-command example. Preserve the rule that domain decisions live in `:core` and repair experiments remain focused.

Create small starter tasks: correct predicted-route wording, add unknown probe fixtures, implement report-version migration, improve German wording, validate Markdown links, or add a redacted synthetic report. Do not label emergency gating or transaction cancellation as easy starter tasks.

A PR template should request the concrete failure, resulting behavior, validation, user-choice impact, schema/permission changes and hardware evidence for routing changes. A documentation-only PR need not pretend it tested Bluetooth. Assign code owners for core recovery, Android integration and releases when actual maintainers agree to those responsibilities; do not invent owners or contacts.

## Engineering and supply chain

The current CI already runs core/app unit tests, lint, debug assembly and screenshot artifacts. Keep that baseline. Add checks for local documentation links, issue-form syntax, compatibility schema, secret leaks and dependency verification as appropriate. Pin third-party actions to reviewed immutable commits and automate proposed dependency updates through review.

Use least-privilege workflows. PR validation must not receive signing secrets; release signing runs only on authorized release refs through the existing release process. Keep checksum and provenance artifacts, verify signer continuity and document key backup/recovery ownership without exposing keys.

Require implementation PR checks before merging and require review for protected recovery/release files when enough maintainers exist. Emergency self-merge procedures, if needed for a solo maintainer, should be explicit and auditable. Configure branch protection/rulesets through the repository owner rather than assuming documentation enables them.

Define severity by user impact: unintended route changes or calls disrupted by the app warrant prompt containment; a repeatable recovery failure warrants targeted investigation; cosmetics follow normal prioritization. A local protection switch and conservative defaults provide immediate containment without depending on a network call.

## Privacy and diagnostics

Collect typed allowlisted fields rather than arbitrary platform object strings. Use report-local aliases for accessories and relative event times by default. Keep firmware/model and app version because they make evidence useful; omit serials, accounts, phone numbers, contact names, Bluetooth addresses, call audio and location.

Version the export schema. Include policy version, access capability, session generation, target alias, event age, request/result IDs, budget, cancellation reason and separately recorded audible confirmation. Test redaction with synthetic sensitive values, nested errors and malformed imported records. A regex alone is insufficient when objects can stringify identifiers unpredictably.

Bound local history and logs with documented size/age limits; provide clear/delete controls and disclose exports already shared elsewhere cannot be recalled. Keep backup exclusions. Separate ordinary sanitized exports from opt-in ADB captures, which may contain personal information and need review before sharing. No automatic telemetry or uploads are required for the community process.

## Releases and feedback loop

Use the existing `release/X.Y` publishing model. Release notes should state behavior changes, tested firmware/accessories, sample sizes, failed cases, known limits, permission changes, migration impact and rollback instructions. Label experimental recovery policies explicitly.

Alpha participants opt into a specific test policy. Beta promotion follows the hardware gates in the technical plan; a passing CI badge alone does not promote recovery claims. Keep a known-limitations list and publish unsuccessful experiments so others do not repeat them blindly.

Each reported regression should map to a reproducer or synthetic trace, a focused test, a fix and a documented device retest when practical. Link release notes back to the issue and compatibility record. Thank contributors only using their preferred public attribution; do not copy private reports into the repository without consent.

## First month backlog

| Priority | Deliverable | Completion criterion |
| --- | --- | --- |
| P0 | Correct product claims and initial access status | README/setup/history distinguish experimental access, route status and audible results |
| P0 | Publish limitations and the hardware test protocol | A new tester can report an actual recovery without driving interaction |
| P1 | Update both existing issue forms | Exact version and evidence denominators captured; no compulsory private logs |
| P1 | Add compatibility schema and first baseline records | Local VW case remains reported failure with truncated evidence, not verified fix |
| P1 | Add PR template and architecture decisions | Reviewer can assess budgets, cancellation, access and privacy |
| P1 | Redacted report format and privacy statement | Synthetic sensitive data never reaches standard export |
| P2 | Maintainer configures Discussions, labels and branch rules | Settings inspected and recorded; no claim that docs enabled them |
| P2 | CI documentation/schema checks and dependency review | Documentation breaks and invalid compatibility entries fail clearly |
| P2 | First public research update | Includes confirmed findings, unresolved questions and one useful next experiment |

Track effectiveness through interpretable reports, time to useful triage, reproducible regressions, contributions merged and call-recovery evidence. Stars and downloads can show reach but cannot establish that Bluetooth calls were repaired.
