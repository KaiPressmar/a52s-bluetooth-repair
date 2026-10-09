# Contributing

## Engineering principles

This project investigates a device-specific Bluetooth call-audio failure. Treat every proposed repair as a hypothesis until it is supported by logs and real-device reproduction.

- The app must stay invisible: no background service, no notifications, no polling. Work happens only while Telecom has the app bound during a call.
- Repairs must be hands-free: the user is driving and can only accept calls through the car.
- Prefer the smallest reversible intervention, and route changes only through Telecom (`InCallService.setAudioRoute`).
- Decisions belong in `:core` (pure Java, unit-tested); `:app` only collects state and executes commands.
- Keep diagnosis read-only and log state before and after each repair step.
- Never silently broaden permissions or add root/Shizuku requirements.

## Ways to contribute

You don't need to write code to help:

- **Field reports** are the most valuable contribution: which phone, firmware, car or headset, and whether the repair worked. Use the [field report form](https://github.com/KaiPressmar/a52s-bluetooth-repair/issues/new?template=2-field-report.yml).
- **Failed calls** with a diagnostic report: use the [call-audio problem form](https://github.com/KaiPressmar/a52s-bluetooth-repair/issues/new?template=1-call-audio-problem.yml).
- **Translations**: the UI is English (`values/`) and German (`values-de/`). A new language is a single `values-xx/strings.xml`; `LocalizationTest` checks that it covers every key with the same placeholders.
- **Documentation**: corrections to the research notes, workarounds you verified, other affected models.

Issues labeled [`good first issue`](https://github.com/KaiPressmar/a52s-bluetooth-repair/labels/good%20first%20issue) are a good starting point for code.

## Development flow

The easiest environment is the dev container in `.devcontainer/` (GitHub Codespaces or VS Code *Reopen in Container*). It matches CI exactly.

Create a focused branch and pull request. Add or update a failing test first for deterministic behavior. Make the smallest implementation change that passes it. Run `./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`. Check the rendered screenshots in `app/build/screenshots/` for UI changes. For Bluetooth/HFP/SCO behavior, document the real-device scenario separately because emulator/JVM success cannot prove the Samsung/Qualcomm path is fixed.

## Pull requests

A PR should explain the observed problem, hypothesis, behavioral change, safety implications, tests added, and manual A52s validation if applicable. Avoid combining unrelated repair experiments in one PR.

## Commit and release discipline

Use descriptive commits. Do not commit generated APKs, keystores, credentials, personal bugreports or logs containing identifiers. Open pull requests against `main`, even for fixes to a released version: maintainers cherry-pick them onto the `release/X.Y` branch, from which releases are published (`RELEASING.md`).
