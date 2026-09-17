# Contributing

## Engineering principles

This project investigates a device-specific Bluetooth call-audio failure. Treat every proposed repair as a hypothesis until it is supported by logs and real-device reproduction.

- Prefer the smallest reversible intervention.
- Keep diagnosis read-only.
- Never silently broaden permissions or add root/Shizuku requirements.
- Do not perform automatic routing changes during an active call.
- Preserve evidence: log state before and after each repair attempt.
- Keep Android/vendor-specific side effects behind testable decision logic.

## Development flow

Create a focused branch and pull request. Add or update a failing test first for deterministic behavior. Make the smallest implementation change that passes it. Run unit tests, lint and a debug build. For Bluetooth/HFP/SCO behavior, document the real-device scenario separately because emulator/JVM success cannot prove the Samsung/Qualcomm path is fixed.

## Pull requests

A PR should explain the observed problem, hypothesis, behavioral change, safety implications, tests added, and manual A52s validation if applicable. Avoid combining unrelated repair experiments in one PR.

## Commit and release discipline

Use descriptive commits. Do not commit generated APKs, keystores, credentials, personal bugreports or logs containing identifiers. Releases are produced only from `main` through the release workflow described in `RELEASING.md`.
