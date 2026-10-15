## Problem / hypothesis

Describe the observed behavior and the hypothesis this change tests.

## Change

Describe the smallest behavioral change made.

## Verification

- [ ] Added/updated regression tests first where deterministic testing is possible
- [ ] `./gradlew :core:test :app:testDebugUnitTest` passes
- [ ] `./gradlew :app:lintDebug` passes
- [ ] Debug APKs build; screenshots in `app/build/screenshots/` reviewed for UI changes
- [ ] Diagnostics remain read-only
- [ ] Route changes only through the durable session dispatcher; every request spends the shared budget
- [ ] Authority, call topology, target identity, cancellation and deadline behavior tested
- [ ] Legacy history migration and interrupted-session behavior checked when changed
- [ ] No audible-recovery claim based only on request acknowledgment or route metadata
- [ ] Nothing runs or notifies outside calls
- [ ] Real Galaxy A52s test documented when Bluetooth/HFP/SCO behavior changes
- [ ] Logs/examples are sanitized and contain no personal identifiers

## Risk / rollback

Describe possible side effects and how to revert or disable this experiment.
