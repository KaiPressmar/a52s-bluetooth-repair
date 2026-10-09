# Changelog

Notable project changes are recorded here. Published GitHub Releases also use automatically generated notes from merged pull requests.

## Unreleased

### Added

- Full English and German UI: English is the default and fallback, German is a complete translation. In-app language setting (System default / Deutsch / English) and the Android 13+ per-app language setting (`localeConfig`). Update and setup error messages are now translatable too.
- Dev container (`.devcontainer/`): JDK 21 and the Android SDK as in CI, Docker host access, GitHub CLI, persistent Gradle cache, and recommended extensions including Claude Code and Codex. The same recommendations are in `.vscode/extensions.json`.

### Project

- MIT license, issue forms (call-audio problem, field report, feature request), SUPPORT, CODE_OF_CONDUCT, CODEOWNERS and Dependabot.
- README rewritten for people searching for the A52s bug: symptom check, German summary, direct download link, FAQ and a "help improve it" section. Social-preview image.
- Releases from `main` are full releases ("Latest") and include a stable `bluetooth-repair.apk` asset for a permanent download link.

## 0.17.0 - universal APK and app icon

### Changed

- One universal APK for all devices and Android 12–16 instead of the `a52s`/`s22` builds. The code was already identical; the device model and Android version are now detected at runtime (preventive rebuild default: A52s every car call, other phones after recent problems).
- App renamed to **Bluetooth Call Repair**.
- Updater uses `bluetooth-repair-vX.Y.Z.apk`. Releases also publish identical copies under the former per-device names, so 0.16.x installs of either variant (even a "wrong" one) update to the universal build.
- CI runs one job; runtime-relevant Robolectric tests run on Android 14 and 16.

### Added

- New adaptive launcher icon (handset with sound waves plus a Bluetooth badge; flat, keyline-aligned) with a themed monochrome layer.
- README header with icon, badges and screenshots.

## 0.16.0 - invisible, Telecom-based repair

### Why

AOSP analysis showed that Android 14 ignores `AudioManager.setCommunicationDevice()` from third-party apps during cellular calls: only the audio-mode owner (Telecom) controls the route. The route repairs of v0.4–v0.15 therefore could not change real calls. See `docs/ARCHITECTURE.md`.

### Changed

- The app runs only during calls, as a non-UI `InCallService` bound by Telecom. The permanent foreground service, boot receiver, periodic checks and **all notifications** are gone; legacy channels and state are cleaned up on update.
- Repairs use `InCallService.setAudioRoute()`: move a stuck call to the car, or rebuild Bluetooth audio (car → phone → car) for SCO/downlink faults. Volume restore stays.
- New per-call repair engine in a pure Java `:core` module: confirmation windows, attempt budget, verification, user-choice respect, observe-only mode.
- Preventive rebuild at call start: A52s on every car call, S22 only after recent problems; configurable.
- One-time setup: Bluetooth access plus car link via Companion Device Manager, or a single adb command.
- New Material 3 UI (dynamic color, dark mode): status, call history with outcomes, preference-based settings. Diagnostic report shared as a file.
- Updates are checked only while the app is open; no update notifications.

### Removed

- `MonitorService`, `BootReceiver`, SCO off-call preflight, notification actions, health chart and the per-sample health history.
- Permissions: `READ_PHONE_STATE`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE*`, `RECEIVE_BOOT_COMPLETED`.

### Project

- Modules `:core` and `:app`, version catalog, Gradle wrapper, R8-minified release (≈2.4 MB), lint clean, CI runs core tests and uploads screenshots.

## 0.15.0 - one-way call audio

### Added

- Downlink signals in every in-call probe and snapshot: voice playback device (`getAudioDevicesForAttributes`), voice-call volume/mute, and telephony/audio-mode mismatch.
- `DOWNLINK_ROUTE_MISMATCH` and `DOWNLINK_SILENCED` failure signatures for "the other side hears me, I hear nothing".
- Route bounce repair (earpiece → SCO) that forces a fresh SCO link. Re-selecting the already selected device is a no-op.
- Voice-call volume restore repair.
- Hands-free preventive SCO rebuild once at the start of every Bluetooth call on the A52s (setting, on by default), because the driver can only accept calls via the car and silence inside the vendor HAL is undetectable.
- Optional "Ich höre nichts – reparieren" notification action during calls. The manual repair button now bounces the route during a call.
- Research notes (`docs/A52S_CALL_AUDIO_ISSUE.md`), field report 2026-10-07, dev-environment and device-capture scripts.

### Changed

- Repair execution is shared by the watchdog, the notification action and the UI (`RepairExecutor`).
- Repair verification now also requires a clean downlink.

## Unreleased (pre-0.4)

### Added

- Test-driven repair policy and safety regression tests.
- CI test/lint/debug-APK pipeline.
- Signed APK release automation with checksum and provenance attestation.
- Release, contribution and security documentation.

## 0.3.0 - experimental baseline

- Bluetooth/audio state snapshots and background monitoring.
- Manual audio-routing reset experiment.
- Manual SCO rebuild experiment.
- Log sharing for comparison of working and failed states.
