# Bluetooth call recovery compatibility

Version 0.24.0 is an experimental prerelease. No configuration has yet been physically validated for this version. Android API compatibility and successful unit tests do not establish repaired call audio.

| Configuration | Evidence status | Result |
| --- | --- | --- |
| SM-A528B Android 14 A528BXXSBGYI3 and VW head unit, older application | Known limitation | User reported silent downlink; in-call evidence is truncated |
| SM-A528B Android 14 A528BXXSBGYI3, version 0.24.0 | Untested | Run the stock-phone access and recovery protocol |
| Other A52s firmware, SM-A528N and other phones | Untested | No automatic-recovery efficacy claim |

Machine-readable records live in [results.json](compatibility/results.json). Run `python3 scripts/validate-repository.py` before adding results. Statuses distinguish untested, API compatible, reported working, fault recovery observed, repeatedly validated and known limitation. Each positive result needs total calls, reproduced faults, automatic recoveries, manual corrections and a linked evidence record.

Use the [validation protocol](validation/ACCESS_PROTOCOL.md) and the existing field-report form. Both successful and unsuccessful results are useful. Publish only sanitized summaries.
