## Problem / hypothesis

Describe the observed behavior and the hypothesis this change tests.

## Change

Describe the smallest behavioral change made.

## Verification

- [ ] Added/updated regression tests first where deterministic testing is possible
- [ ] `./gradlew :core:test :app:testA52sDebugUnitTest :app:testS22DebugUnitTest` passes
- [ ] `./gradlew :app:lintA52sDebug :app:lintS22Debug` passes
- [ ] Debug APKs build; screenshots in `app/build/screenshots/` reviewed for UI changes
- [ ] Diagnostics remain read-only
- [ ] Route changes only through Telecom and covered by `CallRepairEngineTest` safety rules
- [ ] Nothing runs or notifies outside calls
- [ ] Real Galaxy A52s test documented when Bluetooth/HFP/SCO behavior changes
- [ ] Logs/examples are sanitized and contain no personal identifiers

## Risk / rollback

Describe possible side effects and how to revert or disable this experiment.
