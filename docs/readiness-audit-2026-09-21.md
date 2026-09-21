# Readiness audit — 2026-09-21

Scope: actual application behavior, preservation of the ledger, monetary input, imports/reconciliation,
backup/restore, navigation, Russian/English copy and real rendered screens. Personal-phone data is not
used for destructive testing. This file records evidence and remaining work, not a guarantee that all
possible defects are absent.

## Confirmed and fixed

- Income declaration editing used SQLite REPLACE, deleting confirmed salary links through CASCADE.
  Two Room regression tests reproduced loss on editing and changing receiving account; both pass with
  Upsert. Commit `e1f95fb`. No schema change.

- Monetary input now rejects fractional minor units and overflow; account creation distinguishes blank,
  zero, negative and invalid openings. Composer direction comes from the selected operation type.
- Debt writes reject negative repayments, account/currency mismatch and invalid partial credits atomically.
  Four regression tests reproduced the old bugs; all now pass, with account-form and feed tests.

## In progress
- Restore coordination: investigate overlapping restores and in-flight bank/crypto writes against
  replaced account IDs. The safety snapshot and replacement currently use separate transactions.

## Remaining audit coverage

- Real SQLite backup round-trip, malformed/old backups, restore rollback and schema migrations.
- Bank/SMS/push reconciliation, repeat imports, transfer/debt/allocation invariants and cancellation.
- Main navigation, first run, accounts, transaction editor/details, categories, income, savings/debts,
  analytics, connections/settings, backup, app lock and widget paths in representative UI configurations.
- RU/EN text consistency, hard-coded runtime messages, empty/loading/error states.
- Full host tests, selected emulator database and UI suites, screenshot validation, release R8/lint,
  startup logs and public-tree checks. Record precise unverified external-bank scenarios at completion.
