# A52s Bluetooth Repair

[![CI](https://github.com/KaiPressmar/a52s-bluetooth-repair/actions/workflows/ci.yml/badge.svg)](https://github.com/KaiPressmar/a52s-bluetooth-repair/actions/workflows/ci.yml)

Experimental Android diagnostic and repair app for a Bluetooth phone-call audio failure observed on the **Samsung Galaxy A52s 5G (SM-A528 family)**. The characteristic symptom is that Bluetooth remains connected and media audio may continue to work while phone-call audio over the car/headset stops working until the phone is restarted.

> **Status:** experimental research software. A successful APK build or passing unit tests do not prove that the device-specific Samsung/Qualcomm HFP/SCO fault is fixed.

## Current capabilities

- Periodic Bluetooth/audio diagnostic snapshots while monitoring is enabled.
- Active Android communication-device and output-device inspection.
- **Test 1 — routing reset:** clears the Android communication route, disables speaker routing and returns `AudioManager` to normal mode.
- **Test 2 — SCO rebuild:** stops legacy SCO, clears the communication device and attempts to select/restart the Bluetooth SCO communication path.
- Shareable diagnostic log for before/after comparison.
- No root, Shizuku or system modification in the current implementation.

## Safe test procedure

When the fault is present, do **not** reboot first. Park the vehicle before interacting with the app. Capture a snapshot, enable monitoring, then try exactly one repair method and make a test call. Record whether audio recovered before trying another method. Finally share a sanitized log. This preserves evidence about which intervention, if any, changed the state.

See `TESTING.md` for the TDD strategy, safety invariants and real-device acceptance protocol.

## Builds

### Development builds

Every pull request and push to `main` runs unit tests and Android lint before building an installable debug APK. Debug artifacts are intended for development/testing and expire from GitHub Actions.

### Releases

User-facing APKs are published through GitHub Releases and use semantic tags such as `v0.4.0` or `v0.4.0-beta.1`. The release workflow runs tests/lint, builds a **signed release APK**, verifies the signature, publishes a SHA-256 checksum and creates a build-provenance attestation.

Do not treat CI debug artifacts as official releases. See `RELEASING.md` for the release and signing process.

## Development

Requirements: JDK 17 and Android SDK 35. CI uses Gradle 8.9. Core repair decisions should remain separated from Android side effects so safety behavior can be tested with fast JVM tests.

Before a pull request, run the equivalent of:

```text
gradle :app:testDebugUnitTest
gradle :app:lintDebug
gradle :app:assembleDebug
```

Contributions should follow `CONTRIBUTING.md`. Security/privacy guidance is in `SECURITY.md`.

## Safety and privacy

Repair experiments temporarily change Android audio routing and can interrupt audio. Do not operate the app while driving. Diagnostic logs and Android bugreports may contain identifiers or personal information; sanitize them before sharing publicly. Never commit signing keys, passwords, raw private bugreports or other credentials.

## Project scope

The immediate goal is to identify the smallest reliable workaround for the A52s Bluetooth call-audio failure while preserving enough diagnostics to determine whether the failure lies in Android HFP/SCO state, audio routing, or a Samsung/Qualcomm vendor layer. Automatic repair should only be introduced after the detection logic and safety conditions are demonstrated on the affected hardware.
