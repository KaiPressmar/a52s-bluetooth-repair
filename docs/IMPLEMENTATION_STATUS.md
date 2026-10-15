# Version 0.24.0 implementation status

The implementation is based on upstream `b416d67` (v0.23.0), which already supplied modern endpoint routing, target preservation, callback generations, HFP proxy lifecycle handling and extensive tests. The dated research documents retain their earlier `5f3a77d` baseline; their code audit is historical.

## Delivered code

| Backlog area | Implementation |
| --- | --- |
| Access evidence | Typed known/unknown/unavailable app-op observation; configured state distinguished from an active bound session; stock-phone protocol provided |
| Domain and time | Pure `RecoveryBudget`, existing pure repair engine, immutable safety context and virtual-time session tests; existing Handler/elapsed clock retained |
| Endpoint adapters | Existing modern and legacy implementations retained; session owns all route dispatch; automatic uncounted cross-API fallback removed |
| Target observations | Existing endpoint UUID targeting and device-specific HFP checks retained; ambiguous multi-device evidence cannot establish recovery |
| Eligibility | Active single ordinary cellular call, emergency classification, access and topology checked before dispatch and again after durable storage acknowledgment |
| Transactions | Shared four-request limit, one rebuild, 15-second dispatch deadline, one existing scheduled return retry, real earpiece requests and user-choice precedence |
| Durable recovery | Atomic write-ahead lease and serialized storage writer; dirty process interruption suppresses another mutation until actual call completion |
| Reports | Version 2 history in a separate file, non-destructive legacy import, separate reserved/dispatched counts, control/stop results and audible outcome always unknown |
| Policies | Firmware/accessory/policy-scoped risk; no implicit in-call volume or AudioPolicy mutation; protection switch stops all call mutations |
| Diagnostics | Allowlisted enum/numeric route and transport events; arbitrary private log text excluded from standard export; bounded retention |
| Community | Improved existing forms and PR template, compatibility records, validation script and privacy documentation |
| Delivery | Experimental GitHub release channel, signing/checksum/provenance workflow retained; prerelease excluded from stable updater |

The existing `CallSession`, `EndpointAudioState` and `LegacyBluetoothTarget` are the integration boundaries. They were extended rather than replaced with parallel controller classes. Typed endpoint callbacks retain their generation guards. Storage acknowledgment and the session lifetime serve as the dispatch token; the pure engine's existing command enum is retained. The production path has one dispatcher and cannot execute both an old and a new engine.

## Remaining external evidence gates

No Android device is attached to the implementation environment. Actual companion enrollment, stock-Samsung service binding, physical bidirectional call recovery, seven-day observation and comparison of prevention settings remain pending. The compatibility table records the new version as untested. No successful hardware result has been invented.

Version 0.24.0 must remain an experimental prerelease. A stable follow-up requires the [access and recovery protocol](validation/ACCESS_PROTOCOL.md) and the sample/reporting gates in the [technical plan](REFACTORING_PLAN.md). Universal recovery of a missing endpoint or a vendor HAL failure is not established.

A further refinement can add a user-approved accessory-specific volume policy; this version deliberately disables implicit in-call volume changes. Later Android request-intent callbacks are not required by the Android 14 path. The existing visible, optional off-call experiments are preserved separately and do not become an unattended background requirement.
