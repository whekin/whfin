# Credo SMS balance routing — 0.3.43

Two transfers the owner initially suspected were duplicates had different SMS timestamps and distinct
source/destination accounts. The owner confirmed both were real. No transfer was deleted. Long seeded
account names repeated the IBAN tail and obscured the destination; feed labels now show compact bank,
source and destination tails, currencies for conversions, and allow two lines.

The own-transfer importer previously put an unqualified Balance on the debit leg. Observed message
chains demonstrated a receiving-account balance instead. New same-currency transfers now associate
that reading only when exactly one side reaches it from a prior declared bank balance and intervening
recorded movements. Neither or both matches leave it unassigned. Checked arithmetic rejects overflow.
For conversions, balance currency identifies the relevant currency leg. A unique destination balance
can narrow an otherwise ambiguous conversion, but both accounts must still be uniquely determined.
Conversion is not constrained to a single IBAN: the owner confirmed a cross-account currency exchange.

## Existing history

Data health offers Review Credo SMS balances. Preview reads existing Credo SMS transfer pairs and
proposes only balanceAfterMinor changes. It does not move transactions, change amounts/groups, delete
rows or change account totals. It removes unproven misplaced balance metadata rather than inventing
another owner. Confirmation applies only the exact preview and rejects changed transaction/account
snapshots. There is no startup, background, or automatic repair hook.

An earlier proposal to automatically reroute historical operations was rejected by automatic approval
review and was not executed. This explicit metadata-only review is the implemented alternative.
No historical metadata changes have been applied to the owner's phone by this task. Remaining
Choose account items still require bank evidence or explicit routing; this release does not claim
that every existing unresolved message has been answered.

## Verification

Both initial regressions failed before changes: repeated tail and wrong own-transfer balance side.
Full app unit/Compose suite: 1060 tests, zero failures/errors, four skipped. Release R8/lintVital passed.
Room tests verify read-only preview, metadata-only confirmation, idempotence, unchanged totals,
unknown ownership and stale preview rejection. Native EN/light and RU/dark/system font 1.5 checks
passed for transfer labels and explicit preview confirmation using synthetic callbacks.
Screenshots inspected: /tmp/whfin-credo-review-ui. Logs: /tmp/whfin-credo-routing-shipping.log and
/tmp/whfin-credo-review-native.log. Version 0.3.43 (55) installed with install -r and confirmed by
package manager. All committed fixtures are synthetic; the owner's backup remains outside the repo.
