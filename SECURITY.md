# Security and privacy

A52s Bluetooth Repair interacts with Android audio routing and records diagnostic state. It is experimental software, not a safety-critical service.

## Sensitive diagnostic data

Before publishing or attaching logs, review them for device identifiers, Bluetooth device names/addresses, phone/account information and other personal data. Do not commit raw Android bugreports to this public repository.

## Signing material

Android release keystores, passwords and signing keys must never be committed. The release workflow expects signing material only through GitHub Actions secrets. The release keystore should be backed up securely offline.

## Reporting a vulnerability

Do not publish secrets or personally identifying diagnostic data in a public issue. For ordinary non-sensitive bugs, open a GitHub issue with a minimal reproduction and sanitized logs. For a security issue, use [private vulnerability reporting](https://github.com/KaiPressmar/a52s-bluetooth-repair/security/advisories/new) instead of a public issue.

## Supported versions

Only the latest release receives fixes. The app updates itself from GitHub Releases and verifies the source, SHA-256 checksum, package name and version of every update before handing it to Android.
