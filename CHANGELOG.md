# Changelog

Notable project changes are recorded here. Published GitHub Releases also use automatically generated notes from merged pull requests.

## Unreleased

## 0.20.2 - app hardening and measured regression coverage

- Protection and prevention settings now take effect during an active call. Pausing protection stops pending automatic routing without resetting retry limits; explicit manual repairs remain available. Automatic rebuilds refuse ambiguous Bluetooth targets before changing the phone route.
- Stable update selection rejects previews, malformed entries and untrusted assets. Cache expiry handles clock rollback; failed checks retain valid cached updates. Version comparison follows SemVer, including large components and numeric prerelease ordering.
- Update downloads enforce size limits, close connections and remove partial or unverified files. Installation callbacks and update dialogs respect activity lifecycle; rejected car associations produce feedback instead of crashing.
- Recent-call risk assessment handles nonchronological timestamps and excludes future reports.
- Added regression tests for Android 12, 14 and 16 Telecom integration, settings changes, lifecycle, setup, update checks, downloads and installation. Test fixtures prevent unintended network requests.
- Introduced full-production JaCoCo HTML/XML/CSV reports and enforced line/branch minimums: core 95%/85%, Android app 75%/60%. CI and release candidates run coverage checks, script tests and Android lint. See [coverage guide](docs/COVERAGE.md).
- JVM coverage verifies behavior and integration, not audible recovery on an A52s. Bluetooth toggling is not treated as a proven repair; only a phone reboot has been demonstrated by the owner.

## 0.20.1 - wait for SCO teardown before reconnecting call audio

- A Bluetooth rebuild now waits for stable phone routing and 500 ms of observed SCO disconnect before requesting the same endpoint again. Unknown SCO/LE Audio uses a 1.5-second stable phone dwell, explicitly logged as unverified teardown.
- A route or SCO rebound resets the dwell. The leave phase waits at most 4 seconds; a still-connected SCO link aborts and backs off. Later automatic attempts cannot bypass failed teardown with a direct request.
- Diagnostic exports and call starts include time since boot and audio mode; mode changes are logged read-only for comparison with a post-reboot call.
- Manual help and fallback guidance distinguish call-route rebuilding from adapter toggling and phone restart. UI text describes repair attempts without promising recovery.
- [Reboot-only recovery research](docs/REBOOT_RECOVERY.md) records the owner's finding that Bluetooth off/on never helps and only restarting the phone has been demonstrated. Comparable A52s reports suggest temporary/contradictory SAP and offload effects; these are not automated or treated as established causes.
- Regression tests cover delayed and persistent SCO teardown, rebounds, unknown evidence, retry limits and report fields. Physical A52s/car validation remains pending; this patch is a better routing experiment, not a vendor-service restart.

## 0.20.0 - manual call-audio checks and verified endpoint repairs

### Manual help

- The status screen offers a read-only **Check now** action and an explicit **Reconnect Bluetooth audio** action during calls, including when automatic protection is paused or API signals look healthy.
- Manual reconnection has two attempts per call with a 15-second cooldown, without resetting automatic limits. It can revoke the phone-route hands-off gate; speaker, wired and streaming routes, multiple calls and unavailable endpoints are still protected.
- Reports persist manual attempts and distinguish a user-reported problem from a detected fault. Old reports remain readable. A failed/unverified manual rebuild is not reported as repaired.
- Missing Bluetooth/call access, no Telecom-bound call, unavailable or ambiguous devices, ongoing repairs and exhausted attempts have explicit feedback. Microphone mute is observed and preserved.

### More reliable repairs

- Android 14+ uses actual advertised call endpoints with completion/error callbacks when modern availability has been received; older devices and legacy-only observations retain Telecom routing. Rebuilds prefer the previously selected Bluetooth endpoint and do not guess between multiple devices.
- Native request failures stop the operation and back off; superseded/duplicate callbacks are ignored. A timed-out teardown no longer sends an unverified return request.
- Short healthy/unknown pulses do not indefinitely postpone connection-fault confirmation; a stable healthy window resets it.
- LE Audio voice evidence no longer requires a classic SCO link. Bluetooth observation can start after permission is granted during a call.

### Validation and limits

- Regression coverage includes manual checks/reconnections, paused protection, hidden user-reported faults, budgets, persistence, teardown failures, endpoint selection/outcomes, LE Audio and the status-screen controls on Android 14 and 16.
- [Recovery options](docs/RECOVERY_OPTIONS.md) documents primary Android/Samsung sources, practical device checks and actions unavailable to a normal app. Physical A52s/car validation remains pending; audible speech and a vendor HAL reset cannot be guaranteed by these APIs.

## 0.19.1 - recognize hands-free answer fallbacks

### Fixed

- Ringing time no longer counts as stable answered Bluetooth audio. A car-answered call that briefly reports Bluetooth and falls back to the phone remains eligible for automatic repair without another manual selection.
- Establishing a working Bluetooth link for the hands-off decision requires 2.5 seconds of continuous positive SCO or voice-route evidence during a single audio-carrying call. Brief positive pulses, missing evidence and held/multiple-call states reset that evidence window.
- Added regressions for answering with the phone route already selected, long ringing followed by a failed SCO attempt and a brief healthy answered link. No screen taps are needed in any of these scenarios.

### Validation

- Automated tests exercise route decisions without user input. Physical A52s/car validation remains pending; a vendor downlink fault with healthy public API signals cannot be directly detected.

## 0.19.0 - verify Bluetooth recovery and handle asynchronous call audio

### Repair and detection

- Confirmed faults clear only after 2.5 seconds of healthy Bluetooth routing with positive SCO or voice-route evidence. Unknown audio signals and short healthy pulses do not prove recovery.
- If a direct Bluetooth route request fails, the next bounded attempt rebuilds the call route through the earpiece instead of repeating the same ineffective request.
- Route repairs pause for multiple calls, held calls and unavailable Bluetooth. Unknown Telecom routes are observed rather than treated as a phone route. Explicit non-Bluetooth choices remain respected.
- Android 14+ endpoint callbacks supplement legacy call-audio callbacks, including empty availability lists and stale legacy routes. Android 12–13 keep the legacy path.
- HFP audio, profile and adapter-state events trigger checks only during the call. Receivers are removed at session end, and late profile-proxy callbacks are closed.
- Telecom command exceptions are logged without killing observation or resetting the repair budget.

### Validation and limits

- Added regression coverage for ignored route requests, transient and unknown recovery evidence, call waiting, endpoint-only updates, Bluetooth event lifetimes, denied permissions and command failures.
- Research and manual A52s validation scenarios are in `docs/CALL_REPAIR_RESILIENCE.md`. Positive API signals verify routing, not audible remote speech; vendor audio/HAL silence may remain unobservable and unrepairable without a system restart.

### Project

- Releases are published only from `release/X.Y` branches: cut from `main` for every minor version, patches cherry-picked from `main` (`scripts/release.sh`). Release notes come from this changelog, *Latest* stays on the highest version, and pre-release versions are refused because the in-app updater would offer them to everyone. `RELEASE_VERSION` is now the only place the version name is set.

## 0.18.2 - recognize failed Bluetooth call-audio selections

### Fixed

- Ringing fallbacks and short, unsuccessful Bluetooth/SCO selections no longer disable repair as an assumed user exit. Selecting Bluetooth again resumes diagnosis without resetting repair limits.
- Repeated Bluetooth/earpiece transitions share a fault-confirmation window instead of postponing detection indefinitely. A Bluetooth route label alone does not complete SCO verification.
- Route repairs pause when Bluetooth is switched off or call audio is suspended. Reconnection is diagnosed afresh; losing Bluetooth after a confirmed fault is not reported as a successful repair.
- Each Bluetooth rebuild repeats its return request at most once. Speaker, wired and streaming choices cancel route operations, including devices without an earpiece.

### Diagnostics

- Short Telecom audio callbacks are logged directly. Diagnosis changes and periodic status remain visible during the call.
- Exports include the currently observed call with a provisional result, rather than only completed calls.

### Validation

- Regression tests cover the October 9 A52s report, repeated failed selections, Bluetooth off/on during repair, repair limits and user route choices. The observed pre-update device scenario is documented in `docs/field-reports/2026-10-09-bluetooth-selection-fallback.md`.
- Actual A52s/car audio recovery after this update still needs a real-device test. This release fixes detection and bounded Telecom repair attempts; it does not establish that the vendor SCO fault is eliminated.

## 0.18.1 - don't give up when a repair's Bluetooth link collapses

### Fixed

- A call that dropped back to the phone shortly after the app rebuilt the Bluetooth audio was treated as "the user chose the phone", so the app stopped helping for the rest of the call. In the worst case the preventive rebuild at call start left a car call on the earpiece. Android 14's Telecom moves the call to the earpiece whenever a fresh SCO link collapses, which looks exactly like a user choice. A drop within 8 s of one of the app's own route changes is now treated as a failed repair and the call is moved back to the car once; a second drop is still respected as a deliberate choice.
- If the speaker was selected while the app was rebuilding the Bluetooth audio, the rebuild could still move the call back to the car. The app now stops and leaves the speaker alone.

### Changed

- The dev container keeps Claude Code and Codex logins in volumes and points the Java extension at the image's JDK.

## 0.18.0 - English UI, dev container and modern toolchain

### Changed

- Build toolchain: Android Gradle Plugin 9.4.1, Gradle 9.8, compileSdk 37 (Android 17 SDK), build-tools 36. targetSdk stays at 36 on purpose.
- Libraries: AndroidX Core 1.19.1, AppCompat 1.8.0, RecyclerView 1.4.0, Material 1.14.0; test stack: Robolectric 4.17, AndroidX Test 1.7 / ext-junit 1.3.
- The APK ships only English and German resources (`localeFilters`), so library UI never mixes in a third language; release APK ≈1.9 MB.
- Dependabot: AGP and Gradle wrapper move together and majors are migrated deliberately; 7/30-day cooldown; minor/patch grouped, majors separate; dev container image and features tracked; labels and commit prefixes.

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
