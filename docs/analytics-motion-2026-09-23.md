# Month navigation and dock polish — 0.3.69 (81)

The dock no longer paints a pressed tint/ripple on destinations or the create action. Selected
icons, labels, touch targets, semantics and haptic callbacks remain intact. Connection shortcuts
formerly labelled “Cards and accounts” now say “Bank SMS / SMS банка”, matching the actual
message-journal destination, with an SMS icon on connection pages.

Analytics had two separate sources of instability:

- Optional pace/merchant sections changed the list geometry above the month graph. A chart tap
  now preserves the chart offset during the upcoming measurement. Intermediate calculations from
  fast taps retain the latest requested anchor. Top arrows keep visible period controls in place.
- The model combined a new month label with the previous calculation while Room answered. Query
  and calculation now own their period and publish it together. The last correctly labelled
  snapshot remains visible until the next one is ready.

A short empty month can still reach the physical start/end of the scroll range. Normal clamping
is intentional; no artificial spacer is added. Empty spending sections distinguish no expenses
from native-currency expenses awaiting conversion. Financial formulas and Room v8 are unchanged.

## Evidence

The original host regression moved the graph from 490 to 453 px while its height stayed 346 px.
The snapshot regression observed Aug/Sep, Jul/Aug and Jun/Jul header/content mismatches before
correction. Both now pass, including an intermediate-result/rapid-tap case and top-arrow behavior.
The native checks read actual LazyList layout offsets and permit movement only toward the relevant
physical scroll boundary. Cached accessibility bounds were unsuitable for this assertion.

Commands:

```sh
./gradlew :app:testDebugUnitTest :core-ui:validateDebugScreenshotTest \
  :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease
adb -s emulator-5554 shell am instrument -w -e class \
  dev.whekin.whfin.ui.analytics.AnalyticsMonthMotionTest,dev.whekin.whfin.ui.DockPressVisualTest,dev.whekin.whfin.ui.settings.SettingsHierarchyVisualTest \
  dev.whekin.whfin.debug.test/androidx.test.runner.AndroidJUnitRunner
bash scripts/check-public-tree.sh
git diff --check
```

Host: 1,202 cases, 0 failures/errors, 5 skipped private fixtures. Core UI: 43 screenshot comparisons
passed. Release R8/lintVital passed; the existing Compose stack-trace tokenizer warning for the
Glance widget remains non-fatal. Device checks use only the disposable Pixel 9 Pro API 37 emulator.
Native dock tests compare idle/held pixels in light/dark and verify both navigation and create
callbacks. Analytics covers EN light/dark, RU dark/font 1.5 and Spending; settings covers EN light
and RU dark/font 1.5. Screenshots and hierarchies are captured under the debug app's external files
in `analytics-motion`, `dock-press`, and `settings-hierarchy` and inspected locally.

Build log: `/tmp/whfin-0369-verified.log`. Native log: `/tmp/whfin-0369-device-verified.log`.
Signed APK SHA-256: `29e62c7e3d2abc7d79c10c7575eefa55049aac57e49f2e61bf95e442c931edb0`.
Signing certificate matches the existing personal release identity (`af6009…fae92`).

All eight final instrumented scenarios passed. The signed release cold-started on the emulator
without AndroidRuntime/WHFIN errors. Samsung was updated in place from 0.3.68 to 0.3.69. The
installed base.apk matches the build hash and certificate; firstInstallTime (2026-08-16) and
READ_SMS, RECEIVE_SMS, POST_NOTIFICATIONS grants are unchanged. No phone database was read or
cleared by this workflow.

Post-update phone log has no AndroidRuntime crash, but the app's existing integrity checker
reported `duplicate_statement_row`: an SMS and statement row share account, amount and day.
This is a possible duplicate requiring investigation, not proof of corruption caused by the
update. No matching records were inspected or changed. The issue remains unresolved.
