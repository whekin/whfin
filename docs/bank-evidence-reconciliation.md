# Bank evidence reconciliation

One transaction is the counted movement. SMS diagnostics and bank identities are evidence for it,
not additional money. On the device, diagnostics retain the printed and received timestamps
independently and their transaction link survives settlement, merging and rereading SMS. Portable
backups currently exclude SMS diagnostics, so a restored-backup replay cannot verify those links;
the merged transaction rows themselves are included. A merge
retains the retired row with `mergedIntoTransactionId`; it does not manufacture a balance adjustment.

## Credo matching

- A legacy file key containing the running balance is only a locator. A conflicting description,
  counterparty, amount or date is not an exact identity match. Equal-sized purchases reordered by
  the bank keep their original transaction IDs and merchants.
- Revised identical file cohorts with equal multiplicity retain every ID. Complete Credo files also
  reconcile excess legacy copies under the multiplicity contract below. API pages cannot do this.
- File/API linking does not bypass reconciliation of an active SMS copy. API replay preserves the
  richer file fields while attaching the message to the same movement.
- Several same-day card SMS may explain one booked card charge only when the complete merchant
  cohort sums exactly to it, there is just one corresponding charge in the import, and every message
  uses the same known card and currency. There is no subset-sum search or generic amount heuristic.
  Conflicting categories, allocations, debts and transfer links stop consolidation.
- External transfers require the matching SMS kind, sign, currency and amount, unique in both
  directions. The narrow posting window is one day. If the SMS prints a future day relative to its
  receipt, the received day is an additional candidate; the original evidence is not rewritten.
- Repeated own transfers require both account sides, the exact peer IBAN, date, amount, currency and
  equal cohort size. They remain distinct movements. Different owner categories or linked financial
  decisions are not assigned to indistinguishable rows by order. Explicit OWN_LINK groups are not
  reinterpreted by this rule.

Categories survive settlement. Changed amounts with allocations/debt are rejected. Retiring an
existing statement duplicate also rejects conflicting categories or links on the retired row and
relinks diagnostic/hold evidence. Production callers apply the whole plan in a Room transaction.

## Balance audit

After a statement import, compare active ledger amounts at the statement-end cutoff with its closing
balance using checked integer arithmetic. Use posting date when present. Later activity and BANK_HOLD
are excluded. Remaining SMS may explain a difference due to settlement lag; a mismatch is a warning,
not proof that any particular row is a duplicate. Credo's file-sync result displays that distinction.
The audit is read-only and never overwrites the balance or adds an adjustment. API responses without
a closing balance cannot establish this check.

## Boundaries

This iteration prevents and reconciles the supported source combinations during normal imports. It
repairs supported legacy statement-only duplicates during normal file import. Historical account
routing, protected financial links and unsupported ambiguous cohorts still require explicit review. An older export does not carry a reliable generation timestamp;
reimporting it may update running-balance evidence, but must not create another movement. We do not
claim a complete versioned bank-snapshot store or automatic inference for arbitrary consolidated charges.
Unrecognized push formats and ambiguous matches still require evidence or review.

## Verification

Room regressions cover consolidated charges, reread SMS links, both orders of the receipt-date
transfer case, repeated own-transfer sides, revised identical small charges, equal-amount merchant
reordering, cutoff auditing and preservation of all existing importer tests. Optional local XLSX
round-trip validation uses `WHFIN_STATEMENT_CHECK`; the private file stays outside the repository.
Native checks cover the Credo balance warning in English/light and Russian/dark/large text.

## Compatibility fix — 0.3.51

A live sync exposed older settled transfers with a peer IBAN but no `rawCounterparty`. The stricter
0.3.50 file-key check rejected the later beneficiary name even though dates, amount, description,
running-balance key and peer IBAN agreed. Missing non-card display text is now compatible only with
an identical nonblank peer IBAN; a different peer or a conflicting parsed card merchant is not exempted.
This changes identity recognition, not amounts, routing or historical duplicate deletion.

`WHFIN_RESTORE_CHECK` optionally restores a private backup into the isolated Room test before the
local statement replay. This covers upgrade state, including legacy metadata, rather than only an
empty database. Repeated imports check every financial field and transfer membership independently
of regenerated transfer-group IDs. Real fixtures stay outside the public tree.

## Complete-file multiplicity — 0.3.53

A validated Credo file with opening, closing, period and every running-balance step is evidence for
how many booked movements exist in that period. Before matching balance-based keys, the planner
compares full description, purchase/posting dates, amount, currency and peer identity. Excess legacy
file copies with distinct balance revisions can be retired into the earliest retained IDs. A cohort
of two real identical fares retains two movements; a one-lari amount is neither excluded nor proof.
This is not deletion of every ledger row absent from a file, nor an amount-only duplicate heuristic.

The repair excludes bank API identities, overlapping candidate sets, withdrawals/correction history,
transfer groups, allocations, debts, confirmed income-source links and conflicting categories or
original-currency evidence. For repeated cohorts, differing category assignments or message/hold
links are also ambiguous. Unlinked bank conversions retain their transfer classification. Protected
rows stay in the ledger, subject to the existing balance audit and review path.

Retiring and reconciling run in the same Room transaction. Original transaction IDs and categories
survive, retired rows keep `mergedIntoTransactionId`, and singleton diagnostic/hold links follow the
survivor. No opening or balance adjustment is manufactured to hide the discrepancy. Existing opening
anchor rules continue independently. The ordinary revision bridge also recognizes old file keys by
full row evidence after a merge, so replay does not recreate the retired copy.

Room 8 adds nullable `statement_imports.rowMultiplicity`: SHA-256 row fingerprints and counts for
complete files. It contains no raw descriptions. The maximum previously proven count is a lower
bound for automatic repair: an older file with the same cutoff cannot erase a newly confirmed repeat.
A file with an earlier cutoff than a stored complete file cannot retire rows either. Exports do not
supply trustworthy generation timestamps, so automatic decreases below known multiplicity remain
unsupported. This is bounded evidence storage, not a general versioned bank snapshot replacement.
The 7→8 migration leaves historical money and evidence unchanged; portable backups include the new
column, while versions 1–7 restore with null evidence. Legacy counts are learned only on a complete
successful import, never inferred from a possibly duplicated ledger.

Regression coverage includes actual planner/applier repairs, repeated fares and transfers, old/new
file replay, same-cutoff count protection after backup restore, category/alias/withdrawal/allocation
protection, invalid-chain rejection and transactional rollback with diagnostics. The optional private
replay accepts `WHFIN_REQUIRE_BALANCE=1` and `WHFIN_EXPECT_MERGES` to require a balanced restored ledger
and an exact number of retired copies, in addition to repeated-import idempotency.
