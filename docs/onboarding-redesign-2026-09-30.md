# Personal setup: guided actions and recognizable accounts

## Why the old flow was confusing

The setup stage mostly listed links to full settings pages. Its primary `Continue` advanced the
stage without applying any configuration, so doing the work and postponing it looked the same.
The first bank sign-in also moved to Bank SMS, making the other bank less discoverable. TBC left
its sign-in page as soon as history could run in the background; its initial-balance question could
therefore first surface as a follow-up elsewhere. When opened, that question rendered every currency
field together with alternative imports, sync reports and connection management.

The accounts stage grouped contracts correctly but showed masked account numbers, product choices
and card details without balances. Those identifiers were much harder to recognize than the money.
The original accounts and TBC-with-IME renders were inspected before editing.

## Interaction contract

The saved eight stage names remain compatible: Banks → Bank SMS → Accounts → Categories → Income →
Savings and debts → Preferences → Balance review. A step map is available throughout setup. A stage
change starts at its heading; configuration forms return to their stage. Only the last workspace
entry action completes Personal setup.

- A configuration action opens the corresponding form or applies its explicitly named operation.
  Income, savings, category suggestions and SMS enabling have their own prominent actions.
- A navigation action names the next destination. A postponement says what is left for later.
  Neither records a configuration simply because the owner went forward.
- Banks remain a two-bank choice after either connection. Credo may return there while its explicit
  history load continues. TBC stays in its bank flow until the owner returns explicitly; during a
  verified history read it offers a named return without cancelling the running sync.
- File import, restore, individual SMS switches, push, historical categorization, all-account tools,
  lock and backups remain available through disclosures or the Preferences stage. They no longer
  compete with the main task on every first screenful.
- Finishing with incomplete history, unknown balances or unresolved routing keeps the existing
  explicit Ready wording. Optional card/product details do not become a financial blocking gate.

## Recognizing accounts

`SetupBankContainer` carries its currency reviews from the same Room transaction that creates the
setup overview. It does not start an unrelated balance flow with a zero placeholder. The contract
name leads, with the bank and masked suffix as secondary identity. Each currency and current WHFIN
balance is displayed separately immediately below it; product, fund role and cards follow the money.
The entire account block opens its mapping editor. Unknown review data says unknown; zero and negative
balances are real values. A running bank load explains why current amounts may still change.

No balance is summed across currencies or substituted with a TBC available amount. No schema or
money-import rule changes are part of this redesign.

## TBC initial balances

`TbcBalanceWizard` reviews one already-read ledger/currency at a time. The bank's offered number is
prefilled only when present and is still an owner check. A short instruction distinguishes booked
money from the available figure after pending purchases. Detailed help and alternate imports stay
reachable on demand. Failures for other accounts have a visible actionable notice.

The next action validates the current amount and advances only the local review. Previous preserves
the drafts; opening sync results preserves both drafts and the currency index. The final action
requires valid exact minor-unit amounts and initial history for every waiting ledger, and invokes
the existing atomic batch confirmation once. No intermediate action writes a starting balance.
The primary action remains pinned above the navigation bar and real IME. Explicit postponement
retains the existing confirmation that names the unloaded accounts. Finished onboarding results put
Done before another sync; forgetting the connection lives under connection management.

Remembered sign-in remains opt-in encrypted credentials under WHFIN lock. TBC now offers an actual
lock setup entry when it explains that requirement. OTP delivery and submission behavior are unchanged.

## Verification

Screenshots and layout XML are
synthetic, ignored artifacts under `artifacts/onboarding-redesign/`. The physical S25 is excluded from
all test deployment and instrumentation. No live bank requests, personal SMS reads or phone upgrade
are part of this iteration.

- Full `:app:testDebugUnitTest`: 1298 tests, 0 failures/errors, 5 skipped. The targeted setup/TBC
  suites passed again after the final layout edits; step-map navigation is covered separately.
- `:core-ui:validateDebugScreenshotTest`: all 43 existing references passed without replacement.
- `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:assembleRelease`: successful;
  release R8 and lintVital passed. Gradle still reports its existing Glance Compose mapping warning.
- Explicit `adb -s emulator-5554` deployment and instrumentation only. Pixel_9_Pro AVD, API 36.1:
  complete EN/light and RU/dark/font 1.5 journeys, stage restoration, cash/income editors and real IME;
  account inventory EN/light and RU/dark/font 1.5 at 1200×1920; unclassified-card editor RU/large.
- TBC synthetic sign-in → built-in OTP → result with real IME; five currency balances including zero,
  Previous and final batch confirmation in EN; compact RU/dark/font 1.5 guide and explicit skip
  confirmation; mixed-result/error/deposit report RU/dark/font 1.5. Final frames were visually inspected.
- The emulator initially refused APK replacement for insufficient storage. Trimming only its caches
  allowed installation. An earlier combined run was interrupted when another chat redeployed its APK
  to the same emulator; the later complete EN/RU journeys passed. Old QA assumptions about Done and
  asynchronous step changes were corrected; all affected scenarios passed on repeat.

Representative final artifacts (ignored; local only):

- `artifacts/onboarding-redesign/accounts-final/en-accountSetup.png`
- `artifacts/onboarding-redesign/accounts-final/ru-accountSetup.png`
- `artifacts/onboarding-redesign/journey-final-pass/en-false-1.0-overview.png`
- `artifacts/onboarding-redesign/journey-final-pass/ru-true-1.5-overview.png`
- `artifacts/onboarding-redesign/tbc-verified/manual-balance-en-keyboard.png`
- `artifacts/onboarding-redesign/tbc-verified/initial-ru-dark-compact.png`
- Final-step TBC frames are under the same `tbc-verified/` directory.

Actual TBC transport, bank credentials and owner balances were not exercised. Dark system bars were
inspected on these final QA hosts, which now use explicit dark bar styles; this does not claim an
additional physical-device run. Production insets and OTP policy were not changed. The release was
built but not installed on S25.
