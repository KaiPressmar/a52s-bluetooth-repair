# Release process

Releases follow [Semantic Versioning](https://semver.org/) and are published as GitHub Releases from **release branches**. The model is the one used by projects such as Kubernetes (`release-X.Y`), .NET (`release/X.Y`) and Electron (`N-x-y`): `main` is the development line, every minor version gets its own release branch, fixes land on `main` first and are cherry-picked onto the release branch, and release branches are never merged back.

```text
main          ──●────●────●────●────●────●──▶   all PRs land here; CI only, never released directly
                │         fix  │    fix
                │          ╎   │     ╎ cherry-pick -x
release/0.19    ●──────────●─────────●───────   v0.19.0 → v0.19.1 → v0.19.2
                               │
release/0.20                   ●─────────────   v0.20.0 …
```

## Rules

- **Tags** are `vMAJOR.MINOR.PATCH`, created by the release workflow on the release branch commit. Never move, delete or reuse a published tag; if a release is bad, publish the next patch.
- **Release branches** are named `release/MAJOR.MINOR`, cut from `main` for every `X.Y.0`, and live as long as that line gets patches. They are never deleted, force-pushed or merged back into `main`.
- **Upstream first:** every fix is merged into `main` through a reviewed PR with a regression test, then cherry-picked (`git cherry-pick -x`) onto the release branch. Release branches receive no commits that are not on `main`, except the `Release vX.Y.Z` commit (version and changelog).
- **Single version source:** `RELEASE_VERSION` in the repository root. Gradle reads it for `versionName`; CI sets `versionCode` from the workflow run number, so it grows with every release.
- **Changelog first:** `CHANGELOG.md` on `main` is the source of truth. New changes go under `## Unreleased`; a release needs a `## X.Y.Z - title` section, which becomes the GitHub release notes.
- **Stable versions only:** the in-app updater offers every non-draft release to all users, so the workflow refuses pre-release versions such as `1.0.0-rc.1`.
- **Latest:** a release is marked *Latest* (and serves `releases/latest/download/bluetooth-repair.apk`) only if it is the highest version. A patch on an older line is published without taking over *Latest*.

| Change | Version |
| --- | --- |
| Bug fix, safety fix, diagnostics correction | PATCH, on the existing release branch |
| New repair or diagnostic behavior, UI features | MINOR, new release branch |
| Incompatible behavior, storage or workflow (after 1.0) | MAJOR, new release branch |

Until 1.0, significant experimental behavior changes may increment MINOR.

## New minor or major release (X.Y.0)

1. Merge a PR into `main` titled **Prepare vX.Y.0** that
   - renames `## Unreleased` in `CHANGELOG.md` to `## X.Y.0 - <short title>` (and adds a new empty `## Unreleased` above it), and
   - sets `RELEASE_VERSION` to `X.Y.0`.
2. Confirm CI is green on `main` and, for Bluetooth/HFP/SCO behavior changes, that a real-device test is documented (`TESTING.md`).
3. Cut the branch:

   ```bash
   scripts/release.sh cut X.Y.0          # creates release/X.Y from origin/main and pushes it
   ```

4. The **Release APK** workflow publishes `vX.Y.0` from `release/X.Y`.
5. Install the published APK on the affected phone and run the smoke checks in `TESTING.md`.

## Patch release (X.Y.Z)

1. Merge the fix into `main` (PR with regression test).
2. Merge a PR into `main` that adds a `## X.Y.Z - <short title>` section to `CHANGELOG.md`, directly below `## Unreleased`.
3. Cherry-pick and release:

   ```bash
   scripts/release.sh patch X.Y.Z <commit-on-main>...
   ```

   The script checks that the commits are on `main` and not yet on `release/X.Y`, cherry-picks them with `-x` in a temporary worktree, inserts the changelog section, sets `RELEASE_VERSION`, commits `Release vX.Y.Z` and pushes `release/X.Y`. Use `--dry-run` to see the result without pushing. If a cherry-pick conflicts, resolve it in the printed worktree, then finish the same steps by hand (changelog section, `RELEASE_VERSION`, `Release vX.Y.Z` commit, `git push origin HEAD:release/X.Y`).
4. Install and smoke-test as above.

## What the release workflow checks

`.github/workflows/release.yml` runs on every push to `release/**` and can be started manually on a release branch to retry a failed run. It

1. reads `RELEASE_VERSION` and does nothing if tag `vX.Y.Z` already exists (pushes without a version bump are no-ops);
2. fails unless the version is stable `X.Y.Z`, belongs to the branch (`release/X.Y`) and has a `## X.Y.Z` section in `CHANGELOG.md`;
3. runs the unit tests and release lint, restores the signing key from secrets, builds and verifies the signed APK, generates SHA-256 checksums and a provenance attestation;
4. publishes the GitHub Release with the changelog section as notes, a compare link to the previous version, and *Latest* only for the highest version.

CI (`.github/workflows/ci.yml`) also runs for pushes and PRs on `release/**`.

## Recommended repository settings

Under *Settings → Rules → Rulesets*:

- `main`: require a pull request and the CI check; block force pushes and deletion.
- `release/**`: block force pushes and deletion; restrict pushes to maintainers.
- Tags `v*`: block updates and deletion (immutable release tags).

Under *Settings → General*: enable *Automatically delete head branches*, so merged feature branches disappear.

## One-time signing setup

Generate and securely retain a dedicated Android release keystore. Never commit it to Git. Configure these GitHub Actions repository secrets:

- `ANDROID_SIGNING_KEYSTORE_BASE64` — base64 representation of the keystore file.
- `ANDROID_SIGNING_STORE_PASSWORD` — keystore password.
- `ANDROID_SIGNING_KEY_ALIAS` — signing key alias.
- `ANDROID_SIGNING_KEY_PASSWORD` — key password.

Back up the keystore and credentials offline. Losing the signing key prevents future APKs from updating installations signed with that key.

## Release artifacts

Each release contains:

- `bluetooth-repair.apk`: the same APK under a stable name for the permanent link `releases/latest/download/bluetooth-repair.apk`
- `bluetooth-repair-vX.Y.Z.apk`: the universal APK (Android 12–16, all devices) and its `.sha256`
- `bluetooth-repair-a52s-5g-vX.Y.Z.apk` and `bluetooth-repair-galaxy-s22-vX.Y.Z.apk`: byte-identical copies under the former per-device names (with `.sha256`), so 0.16.x installs of either variant find their update
- release notes from `CHANGELOG.md` and a provenance attestation

The in-app updater (0.17.0+) only uses the universal APK.
