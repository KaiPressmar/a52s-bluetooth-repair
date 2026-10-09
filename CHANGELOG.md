# Changelog

Notable project changes are recorded here. Published GitHub Releases also use automatically generated notes from merged pull requests.

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
