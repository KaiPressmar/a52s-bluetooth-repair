# Android / Play Protect installation warnings

The owner reports that v0.20.2 was classified as potentially harmful and installation required expanding the warning and choosing to install anyway. This is a security classification, not just the ordinary permission to install from an unknown source. The exact warning text and classification category are not available. Do not claim that a checksum, our tests or a stable signing key clears a Play Protect verdict.

v0.20.3 removes `REQUEST_INSTALL_PACKAGES`, direct APK download/installation code and the update FileProvider path. The app only checks release metadata and opens the canonical official project release in the browser after user action. Android remains responsible for installation and matching the signing identity. Diagnostic sharing keeps its restricted FileProvider path. The package name and existing release signing certificate are preserved so installed versions can update normally. Old downloaded update APKs are cleaned from cache.

This reduces application privileges and executable-download behavior. It does not establish why Google flagged the previous release or guarantee that the classification disappears. Already installed older versions still use their old update behavior until replaced. The new APK itself may still receive the warning.

## Investigate an erroneous classification

Record the exact Play Protect message, phone firmware, flagged release and where its APK was downloaded. Use the official release assets and their SHA-256 sidecars; compare with the release workflow's signature/provenance verification. A new diagnostic export includes `signerSha256` and installer package name, with unavailable information explicitly marked. The stable release signing certificate SHA-256 is:

```text
fccf4a9080dd19b934b8bb157c31a0b5f123c108d9049512bd62feeb1e68c6af
```

Review Google's [developer guidance for Play Protect warnings](https://developers.google.com/android/play-protect/warning-dev-guidance). If the classification is incorrect after reviewing the relevant guidance, the owner/developer can use Google's [Play Protect appeal form](https://support.google.com/googleplay/android-developer/contact/protectappeals) with the canonical download URL, package name, certificate and warning evidence. No appeal has been submitted by this work. Google's review, not app code, determines its classification. Keep Play Protect enabled; the app offers no bypass or security-setting automation.
