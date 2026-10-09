<p align="center">
  <img src="docs/assets/app-icon.png" width="112" alt="Bluetooth Call Repair app icon">
</p>

<h1 align="center">Bluetooth Call Repair</h1>

<p align="center">
  <a href="https://github.com/KaiPressmar/a52s-bluetooth-repair/actions/workflows/ci.yml"><img src="https://github.com/KaiPressmar/a52s-bluetooth-repair/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="https://github.com/KaiPressmar/a52s-bluetooth-repair/releases"><img src="https://img.shields.io/github/v/release/KaiPressmar/a52s-bluetooth-repair?include_prereleases&label=release" alt="Latest release"></a>
  <img src="https://img.shields.io/badge/Android-12%E2%80%9316-3DDC84" alt="Android 12–16">
</p>

<p align="center">
  <img src="docs/assets/screenshot-status.png" width="230" alt="Status screen">
  &nbsp;
  <img src="docs/assets/screenshot-history.png" width="230" alt="Call history">
  &nbsp;
  <img src="docs/assets/screenshot-settings.png" width="230" alt="Settings">
</p>

Hands-free repair for the Bluetooth call-audio failure on the **Samsung Galaxy A52s 5G (SM-A528, Android 14 / One UI 6.1)**. Bluetooth stays connected and music plays, but call audio in the car or headset fails until the phone is rebooted. In the most common variant, the other side hears you and you hear nothing.

> **Status:** experimental. Passing tests do not prove that every Samsung/Qualcomm HFP failure can be repaired without a reboot. See [TESTING.md](TESTING.md) for the real-device protocol.

## How it works

- **Invisible.** Android starts the app only while a call is in progress (as a non-UI `InCallService`). Between calls nothing runs: no background service, no notifications, no polling, no battery use.
- **Fully automatic.** While driving you only accept the call through the car. The app checks the call audio and repairs it on its own:
  - call stuck on the phone → moved to the car,
  - Bluetooth audio link down or voice played elsewhere → Bluetooth audio rebuilt (car → phone → car, about 1 s),
  - call volume muted → restored.
- **Preventive rebuild on the A52s.** A silent downlink inside the vendor audio stack is invisible to every app, so the app rebuilds the Bluetooth audio once at the start of each car call (can be switched off).
- **Honest about limits.** If Android no longer offers a Bluetooth call route at all, no app can fix it. The app records it and recommends a reboot.

Repairs go through Telecom, the system component that owns call routing, exactly like the audio button in the phone app. Earlier versions used `AudioManager.setCommunicationDevice()`, which Android 14 silently ignores during cellular calls. [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) has the AOSP evidence.

## One app for every device

There is a single APK. The device model and Android version are detected at runtime, so a wrong build can't be installed. On the Galaxy A52s 5G the preventive rebuild is on for every car call. On other phones (e.g. Galaxy S22) it runs only after a problem was seen in the last 7 days. Releases also carry the APK under the former per-device names, so installs of 0.16.x of either variant update to the universal build.

## Setup (once)

1. **Allow Bluetooth access.**
2. **Link the car:** choose your car in the app and confirm the Android dialog. The dialog calls the car a "watch"; that profile is the only one that allows apps to control call audio. After linking, Android starts the app during calls.
   *Alternative with a computer:* `adb shell appops set de.kaipressmar.a52srepair MANAGE_ONGOING_CALLS allow`

Afterwards the status screen shows **Schutz aktiv**, and you don't need to open the app again.

## Screens

- **Übersicht:** protection status, one-time setup, 30-day summary, last car call.
- **Verlauf:** every accompanied call with its result (OK, repaired, problem, switched to phone).
- **Einstellungen:** automatic repair on/off, preventive rebuild mode, linked devices, diagnostic report, updates.

## Project structure

```
core/      Pure Java domain logic: fault classification, per-call repair state machine, reports
app/       Android app (one universal APK): Telecom service, audio probes, setup, UI, updates
docs/      Issue research, architecture, field reports
scripts/   Toolchain setup and on-device state capture
```

## Development

Requirements: JDK 17+ (CI uses 21) and Android SDK platforms 34–36. `scripts/setup-dev-env.sh` installs everything into `~/dev-tools` without root; then run `source ~/dev-tools/env.sh`. The Gradle wrapper pins Gradle 8.13.

    ./gradlew :core:test :app:testDebugUnitTest
    ./gradlew :app:lintDebug
    ./gradlew :app:assembleDebug

The unit tests also render every screen (light and dark) to `app/build/screenshots/`. For on-device debugging use adb wireless debugging. `scripts/capture-call-audio-state.sh` captures audio, Bluetooth and Telecom state during a faulty call.

## Releases

One signed universal APK (`bluetooth-repair-vX.Y.Z.apk`, Android 12–16) is published as [GitHub Releases](https://github.com/KaiPressmar/a52s-bluetooth-repair/releases) with SHA-256 checksums and build-provenance attestation. Changing `RELEASE_VERSION` on `main` triggers a release. The app can update itself from these releases (checked only while the app is open). See [RELEASING.md](RELEASING.md).

## Privacy and safety

No data leaves the phone except the optional update check against GitHub. Call history stores times, durations and results only, never phone numbers, and it is excluded from backups. Review a diagnostic report before sharing it publicly. Do not operate the app while driving; it is designed so that you never need to.

Further reading: [issue research](docs/A52S_CALL_AUDIO_ISSUE.md) · [architecture](docs/ARCHITECTURE.md) · [testing](TESTING.md) · [contributing](CONTRIBUTING.md) · [security](SECURITY.md)
