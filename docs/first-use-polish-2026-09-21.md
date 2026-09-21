# First-use polish — 0.3.67 (79), completed 2026-09-22

The six agreed improvements are implemented, with short routine text and details revealed on demand.
Room remains at schema v8. No personal phone data was changed.

## Result

- Setup reports saved facts: bank history, accounts, categories, income, plans, debts and PIN.
  The final step reviews each currency ledger and can reopen earlier stages through one collapsed row.
- Bank balances are compared at their stated date, excluding holds and later postings. User openings
  are not independent bank evidence. Crypto shows the observed chain balance and observation time.
  Explicit owner checks expire when financial evidence changes, not when a category is edited.
- Home separates account/category decisions from bank waiting. Each opens its matching history filter;
  SMS awaiting a statement remains active ledger evidence, not a mandatory confirmation task.
- Financial editors retain input on failure and close after persistence. Repeated saves are reserved
  synchronously. Form submissions and restore cannot overlap; cancellation before dispatch releases
  the reservation. Manual operation writes and counterparty learning share one transaction.
- Local files and Drive copies show creation date, counts and history range before replacement.
  Confirmation applies the exact prepared snapshot, with no second read/download. Undo keeps the
  private safety-copy path. The restarted unlocked Activity reports success once.
- Setup prose is shorter, review details and step links are collapsed, and the Demo strip shows only
  its name and exit action; accessibility retains the synthetic-data explanation.

## Verification

- App host suite: 1191 tests, 0 failures/errors, 5 skipped optional private fixtures.
- Core UI: 39 screenshot comparisons passed; the unit-test target has no test sources.
- Emulator: 27 UI scenarios passed, followed by 33 final setup, Home and restore scenarios including 19
  data restore/safety tests. These cover 46 distinct scenarios, with intentional overlap between runs.
- Rendered EN light, EN dark and RU dark at font scale 1.5. Reviewed balances, expanded evidence,
  bank/category queues, backup confirmation, compact Demo strip and existing account/income/composer
  forms. The setup journey uses the real keyboard and recreates the Activity at the final step.
- Error retention and retry are checked through the real income form in Compose tests. Restore races,
  double submissions and pre-dispatch cancellation have focused coroutine tests.
- Drive is tested against a local HTTP server: preview does not mutate the ledger and confirmation
  performs no second download. Live Google authentication/cloud recovery was not exercised.
- Signed release R8/lintVital and public-tree/diff checks passed. The final APK was installed only on
  emulator-5554; cold start completed without AndroidRuntime/WHFIN errors.
  SHA-256: `c9c5db70584e3c9e9e097d1bc05e5dd4ce81821d5732a5f3ff9e0e28e6176d53`.

Commands: `:app:testDebugUnitTest`, `:core-ui:validateDebugScreenshotTest`,
`:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:assembleRelease`, and explicitly targeted
`adb -s emulator-5554 shell am instrument` runs. No aggregate connected-device test command was used.

Logs: `/tmp/whfin-polish-final-build.log`, `/tmp/whfin-polish-drive-final.log`,
`/tmp/whfin-polish-delivery.log`, `/tmp/whfin-polish-final-ui.log`, `/tmp/whfin-polish-last-ui.log`.
Screens: `/tmp/whfin-polish-0.3.67-setup`, `/tmp/whfin-polish-0.3.67-home`.

## Remaining owner-side work

The phone was neither updated nor cleared. Live bank login/OTP, real Drive recovery and device transfer
remain unverified. Initial bank sync still loads full available history; a current-balances-only start
is not implemented. The broader preceding audit is in `readiness-audit-2026-09-21.md`.
