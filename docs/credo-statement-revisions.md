# Credo XLSX revisions

Credo's fallback statement key includes posting date, signed amount, running balance and ordinal.
Overlapping exports can report the same purchase with a different running balance. Treating every
changed key as new money produces two confirmed purchases even though description, purchase date,
posting date and amount are identical. This was reproduced in the real ImportPlanner/ImportApplier
path with synthetic data; the regression inserted a second row before the fix.

The Credo source bridge now handles unmatched file rows before API/file matching. It first reserves
all exact identities in the incoming document. A remaining file row can revise a file-backed ledger
row only if the following are equal:

- signed amount, purchase date and posting date;
- full nonempty description, merchant/counterparty and beneficiary account;
- account/currency scope, enforced by the planner's ledger query.

The candidate must be unique in both directions. Multiple possible matches fail planning before
money is written. Exact identities take priority, so a second genuine identical purchase alongside
an already known row remains a separate purchase. No amount-only or fuzzy merchant matching is added.

A revision keeps the transaction ID, original external key and any API identity attached to it.
This makes the original export and repeated revised exports idempotent. It updates the running
balance and preserves the owner's category; an explicitly withdrawn transaction stays withdrawn.
This does not establish which of two contradictory exports is more recent: re-importing a different
revision can change balance evidence again. It must never add a second expense solely for that reason.

Existing pairs of statement rows are not merged by this change. Their existence may be legitimate
(e.g. two identical public transport charges), and the backup alone does not prove multiplicity.
No startup repair or owner database edits are performed. The reported pair and nearby candidates
were recorded in a private review outside the repository; adjacent candidates await owner confirmation.

## Verification

Synthetic Room tests cover the original failing repro, changed ordering of two merchants, repeated
old/new files, category preservation, retained API aliases, withdrawn rows, genuine repeated purchases
and ambiguous twins. The original red log is /tmp/whfin-jysk-red.log; full test/release output is
/tmp/whfin-jysk-shipping.log. No bank credentials or private financial fixtures are committed.

Full app suite: 1065 tests, zero failures/errors, four skipped. Release R8/lintVital passed;
the existing Compose stack mapping warning for the widget remains non-blocking. Version 0.3.44
(56) installed using install -r and verified with package manager. Owner financial rows unchanged.
