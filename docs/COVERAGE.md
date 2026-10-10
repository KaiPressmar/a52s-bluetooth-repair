# Test coverage and regression development

Run with Java 21 and the Android SDK specified by the build:

```sh
./gradlew coverageReport
python3 -m unittest discover -s scripts -p 'test_coverage_*.py'
python3 scripts/coverage-summary.py build/reports/jacoco/coverageReport/coverageReport.xml --verify
./gradlew :app:lintDebug :app:assembleDebug
```

Open `build/reports/jacoco/coverageReport/html/index.html`. XML, CSV and the CI Markdown summary live alongside it. GitHub Actions uploads these reports and raw JUnit results in `ci-reports`; release builds upload `<tag>-test-reports`. Download the artifact and open its HTML index locally. CI also publishes the coverage table in the run summary.

Both production modules are measured, including UI, update installation and Android integration. Only generated resource classes, BuildConfig and view bindings are excluded. Tests and Robolectric shadows are not production inputs. Missing execution data or a missing module fails verification.

| Module | Minimum lines | Minimum branches |
| --- | --- | --- |
| Core | 95% | 85% |
| Android app | 75% | 60% |

CI and release candidates enforce both thresholds independently. Raising core coverage cannot hide an Android regression. The summary parser has tests for incomplete reports, each threshold and empty counters.

During this review, measured coverage improved from 75.3% to 83.3% of all production lines and from 68.2% to 75.6% of branches. Core lines improved from 97.4% to 97.8%, Android lines from 65.3% to 76.9%. These are measurements from the review runs, not permanent targets; consult the latest artifact for current numbers.

Use red/yellow HTML lines to identify a realistic failure scenario first. Add regressions around externally observable outcomes: commands suppressed after protection is paused, no route hop with an ambiguous return device, cleanup after interrupted downloads, ignored stale activity callbacks, and invalid update entries skipped without hiding a valid release. Avoid asserting private implementation details merely to increase coverage.

Remaining gaps include UI interaction branches, diagnostic formatting and OS installer integration. The test application disables automatic startup update checks; update tests inject deterministic fake fetchers and connections. Production automatic update behavior is unchanged. Telecom/setup tests cover Android 12, 14 and 16 where relevant; existing UI screenshots remain checked.

From v0.20.3, OS installation runs through the browser: direct APK-download/install code and its tests were removed with the package-installation permission. Tests cover canonical browser URLs, invalid metadata, missing browsers, absent permission and old-cache cleanup instead. Coverage measures the remaining production code, so changes in percentages also reflect this reduced scope.

Coverage does not establish that the car receives audible speech. Validate car-answered calls without phone input, SCO teardown/rebounds, persistent downlink silence and manual repair on the physical A52s. Preserve permissions, selected devices and retry budgets. The owner's only demonstrated recovery is a phone restart; public Android routing APIs cannot guarantee a vendor audio/HAL reset.
