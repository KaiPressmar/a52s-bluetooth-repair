# A52s Bluetooth Repair

[![CI](https://github.com/KaiPressmar/a52s-bluetooth-repair/actions/workflows/ci.yml/badge.svg)](https://github.com/KaiPressmar/a52s-bluetooth-repair/actions/workflows/ci.yml)

Experimental Android diagnostic and repair app for a Bluetooth phone-call audio failure observed on the **Samsung Galaxy A52s 5G (SM-A528 family)**. The characteristic symptom is that Bluetooth remains connected and media audio may continue to work while phone-call audio over the car/headset stops working until the phone is restarted.

> **Status:** experimental research software. A successful APK build or passing unit tests do not prove that every Samsung/Qualcomm HFP/SCO failure can be repaired without a reboot.

## Current capabilities

- **Auto-Schutz foreground service:** persists in the background with a visible notification and checks the Bluetooth communication route every 30 seconds, increasing to every 5 seconds during an active communication session.
- **Event-driven checks:** audio-device changes trigger an additional near-immediate probe.
- **Guarded fault detection:** an automatic repair candidate is only raised when a call/communication session is active, Bluetooth SCO/HFP is available, but Bluetooth is not the selected communication device.
- **Debounced automatic repair:** the same suspect state must be observed twice before the app reselects the Bluetooth communication device. Automatic repair has a cooldown to avoid route thrashing.
- **Speakerphone guard:** the watchdog does not automatically override an explicitly active speakerphone route.
- **Manual diagnosis and repair:** run a non-invasive diagnosis, diagnose-and-repair flow, or force a SCO/HFP route reselection.
- **Persistent state:** last check, last result and repair count survive app restarts. If Auto-Schutz was enabled, the app attempts to restore it after a reboot or app update.
- **One-way audio detection (v0.15.0):** during a call the app also checks where Android plays the voice downlink and whether the voice-call volume is muted. "They hear me, I hear nothing" is detected as `DOWNLINK_ROUTE_MISMATCH` or `DOWNLINK_SILENCED` and repaired with a route bounce (earpiece → SCO) or a volume restore.
- **Hands-free call-start protection (A52s):** while driving, a call can only be accepted via the car, so no repair depends on touching the phone. Silence inside the vendor audio HAL is invisible to apps, so Auto-Schutz rebuilds the SCO channel once, shortly after every Bluetooth call is accepted (≈1 s gap; can be switched off in the settings).
- **Optional "Ich höre nichts – reparieren"** notification action during calls, for a passenger or when parked.
- **Detailed diagnostics:** communication device, available communication devices, output devices, audio mode and health assessment are logged and can be shared.

## What the app can and cannot reset

On Android 12+ the app uses the public AudioManager.setCommunicationDevice() / clearCommunicationDevice() APIs. It can therefore release and reselect the Bluetooth communication route exposed by Android.

A normal third-party app **cannot restart Samsung's Bluetooth system service, toggle protected vendor services, or reset the full Qualcomm/Samsung Bluetooth stack**. If Android no longer exposes a Bluetooth SCO/HFP communication device at all, the app records that state and does not pretend that a public-API repair succeeded. A privileged Shizuku/root fallback is intentionally not part of v0.4.0.

## Safe test procedure

When the fault is present, do **not** reboot first. Park the vehicle before interacting with the app. Start with **Nur Diagnose ausführen** and capture the state. Then use **Jetzt prüfen & bei Bedarf reparieren**. If the app reports that no Bluetooth-SCO/HFP device is available, save/share the diagnostic log before rebooting.

For background protection, enable **Automatische Überwachung starten** while the app is in the foreground. Android keeps the watchdog visible as a foreground-service notification.

Background research on the failure, its variants and the community workarounds is in [docs/A52S_CALL_AUDIO_ISSUE.md](docs/A52S_CALL_AUDIO_ISSUE.md). Field reports are in [docs/field-reports/](docs/field-reports/).

See TESTING.md for the TDD strategy, safety invariants and real-device acceptance protocol.

## Builds

### Development builds

Every pull request and push to main runs unit tests and Android lint before building an installable debug APK. Debug artifacts are intended for development/testing and expire from GitHub Actions.

### Releases

User-facing APKs are published through GitHub Releases and use semantic tags such as v0.4.0 or v0.4.0-beta.1. The release workflow runs tests/lint, builds a **signed release APK**, verifies the signature, publishes a SHA-256 checksum and creates a build-provenance attestation.

Updating the repository-level RELEASE_VERSION file on main triggers a signed release for that version. The workflow can still be started manually when needed.

Do not treat CI debug artifacts as official releases. See RELEASING.md for the release and signing process.

## Development

Requirements: JDK 21, Gradle 8.13 and Android SDK platforms 34–36 (same as CI). `scripts/setup-dev-env.sh` installs all of them into `~/dev-tools` without root and writes `local.properties`. Then run `source ~/dev-tools/env.sh`.

Device debugging from WSL works best over adb wireless debugging (`adb pair`, `adb connect`). `scripts/capture-call-audio-state.sh` captures audio, Bluetooth and Telecom state during a faulty call. Core repair decisions remain separated from Android side effects so safety behavior can be tested with fast JVM tests.

Before a pull request, run the equivalent of:

    gradle :app:testA52sDebugUnitTest :app:testS22DebugUnitTest
    gradle :app:lintA52sDebug :app:lintS22Debug
    gradle :app:assembleA52sDebug

Contributions should follow CONTRIBUTING.md. Security/privacy guidance is in SECURITY.md.

## Safety and privacy

Repair attempts temporarily change Android communication-audio routing and can interrupt audio. Do not operate the app while driving. Diagnostic logs and Android bugreports may contain identifiers or personal information; sanitize them before sharing publicly. Never commit signing keys, passwords, raw private bugreports or other credentials.

## Project scope

The goal is to provide the smallest reliable workaround for the A52s Bluetooth call-audio failure while preserving diagnostics that distinguish a recoverable Android routing failure from a deeper Samsung/Qualcomm vendor-stack failure. Automatic repair is deliberately limited to a narrow, observable HFP/SCO mismatch and avoids destructive Bluetooth resets.
