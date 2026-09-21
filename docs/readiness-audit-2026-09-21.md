# Readiness audit — 0.3.66 (78), 2026-09-21

The exercised personal-use scenarios are ready for a new setup. The defects below are fixed and
verified; live bank authentication and cloud recovery still need owner-side verification. The
personal phone was not updated or cleared. No Room schema change was needed (v8).

## Defects fixed

| Finding | Result and evidence |
| --- | --- |
| Editing an income declaration used SQLite REPLACE and cascaded away confirmed payments | True Upsert preserves confirmations when editing and when changing the receiving-account era. Two Room tests failed before the fix and pass after it. Commit `e1f95fb`. |
| Amount entry truncated fractional minor units and could wrap Long values | Exact parsing rejects loss of precision, overflow and an un-negatable minimum value. Account opening distinguishes blank, zero, negative and invalid input; operation type determines the composer's sign. |
| Negative debt repayments could reverse the cash movement and close a debt | Repository and forms require positive movement amounts, matching account currency and valid partial credits. Invalid requests leave people, transactions and debt events unchanged. Four regression tests reproduced the old errors. Commit `c2e824b`. |
| Restore was not coordinated with network work, overlapping restores or the pre-restore snapshot | Bank work reserves access before dispatch; wallet refresh/discovery and file imports reserve through completion. Restore rejects overlapping work. Safety export and replacement share one SQLite transaction; failure rolls back. |
| Restored account IDs could leave old screens and retained bank results alive | Successful restore discards the old Activity/ViewModels and retained bank challenges/results. The real Activity journey verifies destruction of the previous screen. Encrypted sign-in is preserved. |
| An external restart boolean bypassed App Lock | Internal restarts require a one-use process-local permit with a 30-second lifetime, issued only while foreground and unlocked. A forged external Intent was refused by the real Activity. Restore/restart fixes: `af01ff6`. |
| A source label removed characters inside chosen account names | `Cashmere` and `MyTBC` remain intact; generated bank names still collapse to their surrounding context. A regression test reproduced the display error. |
| Runtime debt/account/import messages exposed English or internal exception text | RU/EN messages now explain the failure. Import cancellation propagates instead of becoming a file error. |
| Bank holds were described/counted as drafts needing the owner's answer | Analytics calls them provisional operations; the data-health action queue excludes BANK_HOLD, matching the feed. A Room test reproduced an incorrect count of two instead of one. |

The restore contract is documented in [backup-restore-safety.md](backup-restore-safety.md).
These changes prevent future failures; they do not invent confirmations already lost by older builds.

## Verification

| Area | Evidence |
| --- | --- |
| Host tests | **1178 tests, 0 failures/errors, 5 skipped**. The skips require optional private Credo/TBC statement or captured crypto fixtures not configured for this run. |
| Native SQLite and Android data APIs | **143 scenarios covered**, including migrations, every-table backup round-trip, old/malformed backups, rollback, restore safety copies, transaction/debt/allocation invariants, repeat imports, SMS reconciliation, rates and Keystore tampering/isolation. |
| UI journeys | **95 scenarios covered** across the package run and targeted rechecks: navigation, first run, accounts, transaction forms/details, income, analytics, settings, bank results, background sync and widget entry. RU/EN, light/dark and large fonts; dock labels also checked at 2× in Russian. |
| Real Android delivery | The dedicated synthetic push sender and emulator SMS injection both passed. No live bank credentials or OTP were used. The temporary push sender was removed by its test script. |
| Shared visual components | `:core-ui:validateDebugScreenshotTest` passed. Representative screenshots were inspected, including IME, long text, amounts and system bars. |
| Signed release | `:app:assembleRelease` passed, including R8 and lintVital. The actual signed **0.3.66 / 78** APK was installed on emulator-5554 and cold-started successfully. |
| Filled application | Release Demo was opened through Welcome. Home, history, accounts, savings, analytics, settings and data health were inspected; data health reported no contradictions. |
| Text and public tree | RU/EN resource keys and format placeholders match; no duplicate names. Public-tree privacy and diff checks passed. |
| Startup log | No AndroidRuntime or WHFIN errors after the final cold start. Emulator language, font scale and night mode were restored to their original settings. |

Counts describe unique scenarios covered across the initial runs and corrected targeted reruns, not a
claim that every original suite passed on its first attempt. The original failures were investigated:

- the bundled demo is a **v6 backup** restored into the current schema; its metadata does not become v8;
- an unlabelled transfer SMS balance without prior evidence belongs to neither leg;
- forecast fixtures must declare the previous salary received before expecting the next payday, and
  must use the actual weekend rule instead of the removed latest-date fallback;
- the income start date is now a calendar, so the keyboard test uses a text field;
- Compose pulled Espresso 3.5.0, which called a removed hidden InputManager API. Instrumentation now
  uses 3.7.0, whose [official release notes](https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0)
  document the fix;
- the dock test compared the full cached paragraph width with a text-sized box. It now verifies no
  ellipsis, every visible character and actual drawn-line bounds.

Android backup rules were reviewed: only the user database and non-secret preferences are allowlisted.
The [AOSP FullBackup parser](https://android.googlesource.com/platform/frameworks/base/+/HEAD/core/java/android/app/backup/FullBackup.java)
automatically includes database journal/WAL companions. Secrets, Demo and runtime flags remain excluded.

## Commands and local evidence

- `./gradlew :app:testDebugUnitTest :core-ui:validateDebugScreenshotTest :app:assembleDebugAndroidTest :app:assembleRelease`
- Explicit `adb -s emulator-5554 ... am instrument` package runs for `dev.whekin.whfin.data` and
  `dev.whekin.whfin.ui`, followed by the documented targeted rechecks.
- `python3 scripts/test-tbc-push-emulator.py --serial emulator-5554`
- `python3 scripts/test-tbc-otp-emulator.py --serial emulator-5554`
- `bash scripts/check-public-tree.sh`; `git diff --check`.

Logs: `/tmp/whfin-readiness-final-build.log`, `/tmp/whfin-ui-audit.log`,
`/tmp/whfin-ui-audit-recheck.log`, `/tmp/whfin-dock-final.log`, `/tmp/whfin-restore-lock-audit.log`,
`/tmp/whfin-push-delivery-audit.log`, `/tmp/whfin-otp-delivery-audit.log`, `/tmp/whfin-release-package.log`, `/tmp/whfin-release-logcat.txt`.
Audit screenshots: `/tmp/whfin-readiness-screens`; release captures: `/tmp/whfin-release-*.png`.
Final cold-start capture: `/tmp/whfin-release-final-home.png`.

APK: `app/build/outputs/apk/release/app-release.apk`.
SHA-256: `4fd6f1dfc85fc0f06e73ca31b9ab4bc497f50420220b060c0e94ef5df26d8277`.

## Not exercised against personal services

- Fresh Credo/TBC login and actual bank OTP/delivery on the owner's Samsung.
- Google Drive authorization/recovery and an actual Android cloud/device-to-device restore.
- The five optional private-data tests listed above.

The first live sync should still be compared with the bank's own balances. This audit does not certify
future private-bank protocol changes or every possible input/device combination. Personal data has not
been erased; initial import still loads all available history, not a new current-balances-only mode.
