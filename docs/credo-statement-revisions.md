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

## Verified SMS lineage and explicit repair — 0.3.45

Comparing three private backups establishes the exact sequence for the reported purchase:
SMS in the earlier backup; the same transaction ID reconciled to STATEMENT by the first sync;
a second STATEMENT row created by the later sync with a revised running balance. Current source
alone had not established this history. A test now drives the actual SMS parser/importer, first
statement and revised second statement. Temporarily disabling the revision bridge makes that
full-chain test fail with a second insert; restoring it keeps the original ID and category.

Data health offers **Review duplicate purchases**. This is an explicit one-pair decision, never a
startup repair. The preview groups Credo legacy file-backed debit rows by account, currency, both
dates, exact description and counterparty. It proposes only pairs with different creation times
and different non-null balances. Transactions in the same import, 1 GEL charges, transfers, API
identities, conflicting categories, corrections, allocations and debts are excluded. These are
candidates, not proof of duplicate purchases; the owner confirms that there was one purchase.

Confirmation checks the entire transaction/account snapshot and rechecks debt/allocation links in
one Room transaction. It keeps the original ID and category, takes the newer copy's bank balance
and external key, and marks the newer copy as merged into the survivor. No balancing adjustment
is generated; the redundant expense stops contributing to account totals. The retired row remains
in the database/backup. Old and new exports after the merge are tested not to resurrect it.

An immediate **Undo last merge** action restores both exact rows and keys atomically while the
screen's view model is alive. A changed ledger/account snapshot or new financial links prevents
undo. It is not a persistent, unrestricted historical unmerge feature. No schema change is needed.

Private backups and review artifacts remain outside the repository. The owner's phone is never
used for instrumentation or direct database edits; actual repairs use the reviewed app interface.

Validation: 1072 tests, zero failures/errors, four skipped. EN/light and RU/dark/font 1.5 native
selection/confirmation passed; frames inspected in /tmp/whfin-duplicate-ui. Full release succeeded.
Version 0.3.45/57 installed on Samsung. The owner-reported purchase was merged through the ordinary
app UI; history search confirmed a single purchase with the preserved category. Other candidates
were not merged. The owner subsequently requested checkboxes and a single batch action.

## Batch review — 0.3.46

The owner requested replacing repeated one-pair actions with checkboxes and a single apply button.
The preview now supports independent selection, Select all/Clear selection, a selected-pair count,
and a signed account-balance increase summed separately for each currency with checked arithmetic.
Selection starts empty. The primary action is disabled for an empty selection or an overflowing sum.

All selected pairs are checked before any row is changed and applied in one Room transaction. An
unknown selected ID, stale snapshot, or newly attached split/debt rejects the whole batch. Immediate
undo restores the entire batch from the same before/after snapshot. Existing candidate restrictions
are unchanged; the bulk action does not reinterpret similar-looking transactions as proven duplicates.
WhfinCheckList provides a single accessible checkbox target per row, shared ledger spacing, and
wrapping labels. No extra confirmation dialog follows the displayed selected count and impact.

Batch validation: 1075 app tests, zero failures/errors, four skipped; release R8/lintVital passed.
Native EN/light and RU/dark/font 1.5 checked independent toggling, Select all, and a two-pair payload.
Both tests passed; /tmp/whfin-batch-ui frames were inspected. Version 0.3.46/58 installed and verified
on Samsung. Four remaining owner candidates have not been applied as a batch.
