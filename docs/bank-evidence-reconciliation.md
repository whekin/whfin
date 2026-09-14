# Bank evidence reconciliation

One transaction is the counted movement. SMS diagnostics and bank identities are evidence for it,
not additional money. Diagnostics retain the printed and received timestamps independently and
their transaction link survives settlement, merging, rereading SMS and portable backup. A merge
retains the retired row with `mergedIntoTransactionId`; it does not manufacture a balance adjustment.

## Credo matching

- A legacy file key containing the running balance is only a locator. A conflicting description,
  counterparty, amount or date is not an exact identity match. Equal-sized purchases reordered by
  the bank keep their original transaction IDs and merchants.
- Revised identical file cohorts with equal multiplicity retain every ID. A change in multiplicity
  without exact identities remains ambiguous. This rule never removes an existing statement row.
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
does not automatically collapse legacy statement-only duplicates or repair historical account routing.
Those continue to use explicit review. An older export does not carry a reliable generation timestamp;
reimporting it may update running-balance evidence, but must not create another movement. We do not
claim a complete versioned bank-snapshot store or automatic inference for arbitrary consolidated charges.
Unrecognized push formats and ambiguous matches still require evidence or review.

## Verification

Room regressions cover consolidated charges, reread SMS links, both orders of the receipt-date
transfer case, repeated own-transfer sides, revised identical small charges, equal-amount merchant
reordering, cutoff auditing and preservation of all existing importer tests. Optional local XLSX
round-trip validation uses `WHFIN_STATEMENT_CHECK`; the private file stays outside the repository.
Native checks cover the Credo balance warning in English/light and Russian/dark/large text.
