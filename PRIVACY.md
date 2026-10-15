# Privacy and local diagnostic data

Recovery runs locally and does not require an online service. Update checks contact the project's GitHub releases when enabled. No automatic diagnostic upload or call recording is part of call recovery.

The app stores bounded call summaries, timestamps, durations, observed faults, request counts and results. Audio is not verified by call-route metadata. A small private recovery journal preserves a dirty-operation marker across process death. A local policy store uses a hash of accessory identity, firmware and policy version to scope risk history. Raw accessory identities used for targeting remain in memory; companion associations are managed by Android.

The standard diagnostic share contains phone model/firmware, app and permission state, call summary times and typed recovery events. It excludes the free-form internal log, contact names, call numbers, Bluetooth names/addresses and audio. Recovery event retention is at most 200 entries; call history is at most 200 entries and exports contain at most 50 completed calls. Review a report before sharing because firmware and call times may still identify a situation.

Internal troubleshooting logs rotate across two files of roughly 256 KB each. Clear history in settings to remove call history and diagnostic events. It also clears scoped risk history. The active recovery journal is retained until call completion so deleting history cannot reset a running repair budget. Android app data removal clears remaining private data and may revoke setup grants. Local history and diagnostics are excluded from backup.

Advanced ADB captures are separate from standard reports and may contain personal information. They require review before publication. Public GitHub reports are public; shared copies cannot be recalled by clearing local app data.
