# Release process

This project uses Semantic Versioning and GitHub Releases. Stable release tags are immutable identifiers in the form `vMAJOR.MINOR.PATCH`, for example `v0.4.0`. Pre-release versions may use suffixes such as `v0.4.0-beta.1`.

## Version policy

- **PATCH**: backwards-compatible bug fixes, diagnostics corrections and safety fixes.
- **MINOR**: new backwards-compatible repair/diagnostic functionality.
- **MAJOR**: incompatible behavior, storage or workflow changes once the project reaches 1.0.
- Until 1.0, significant experimental behavior changes may increment MINOR.

Do not move or reuse a published full version tag. If a release is bad, publish a new patch version.

## One-time signing setup

Generate and securely retain a dedicated Android release keystore. Never commit it to Git. Configure these GitHub Actions repository secrets:

- `ANDROID_SIGNING_KEYSTORE_BASE64` — base64 representation of the keystore file.
- `ANDROID_SIGNING_STORE_PASSWORD` — keystore password.
- `ANDROID_SIGNING_KEY_ALIAS` — signing key alias.
- `ANDROID_SIGNING_KEY_PASSWORD` — key password.

Back up the keystore and credentials offline. Losing the signing key prevents future APKs from updating installations signed with that key.

## Release checklist

1. Work through a pull request into `main`; CI must pass.
2. Confirm experimental repair changes have tests and a documented real-device test result where applicable.
3. Decide the next semantic version.
4. Preferred automated path: update the repository-level `RELEASE_VERSION` file in the release PR. When that change lands on `main`, the signed release workflow runs automatically and publishes the version as the latest release.
5. Manual path: open **Actions → Release APK → Run workflow** on `main`, enter the version without the `v` prefix, and select whether it should be a prerelease.
6. The workflow validates the version, runs unit tests and release lint, restores the signing key from secrets, builds the release APK, verifies its signature, generates SHA-256, creates a provenance attestation, and publishes a GitHub Release with generated notes.
7. Install the published APK on the affected phone and perform the smoke/real-device checks in `TESTING.md`.

## Release artifacts

Each release contains:

- `bluetooth-repair.apk`: the same APK under a stable name for the permanent link `releases/latest/download/bluetooth-repair.apk`
- `bluetooth-repair-vX.Y.Z.apk`: the universal APK (Android 12–16, all devices) and its `.sha256`
- `bluetooth-repair-a52s-5g-vX.Y.Z.apk` and `bluetooth-repair-galaxy-s22-vX.Y.Z.apk`: byte-identical copies under the former per-device names (with `.sha256`), so 0.16.x installs of either variant find their update
- GitHub-generated release notes and a provenance attestation

The in-app updater (0.17.0+) only uses the universal APK.

## Hotfixes

Fix from `main` through a focused PR, preserve a regression test for the failure, then issue the next PATCH version. Never replace an existing APK under an old version tag.
