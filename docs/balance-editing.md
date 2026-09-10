# Balance editing and transaction details — 0.3.39

A normal account activity balance opens the target-balance sheet. Its amount is a single accessible
click target; account metadata retains its pencil and the overflow has icons for both correction
paths. Chain readings remain read-only. Available/Reserve has a 16 dp content gap below its toolbar.

## Local balance adjustments

The sheet loads a complete account/active-rows snapshot. Exact decimal conversion accepts zero and
negative values, rejecting fractional minor units and overflow. Saving runs in one Room transaction:
compare the account and all active rows, subtract the displayed balance from the target, and create
one ordinary ADJUSTMENT with the Unaccounted category. An intervening ledger/profile change or write
failure leaves the form open with a reopen instruction; repeat taps are disabled while saving.
Dismissing before Save writes nothing.

After Save a snackbar offers Undo. It removes only that created delta, retaining later transactions.
The same local delta is deletable from its transaction overflow with confirmation. Eligibility is
shared between UI and the mutation boundary: ADJUSTMENT, MANUAL status, no transfer/group, no external
key, no void/merge/correction metadata. Debt-linked rows retain the existing deletion protection.
Manual opening balances, imported opening anchors and correction audit records are not deletable
through this path. Removing an adjustment recalculates the balance; it does not rewrite bank history.
USER_OPENING still has its separate correction editor; this Undo does not restore old USER_OPENING
edits. No schema or backup format changes.

## Transaction details

Category is an explicit action before status, with its existing colored icon and an Edit affordance.
Uncategorized transactions say Assign a category. The picker filters by name, retains the selected
category and offers category creation. It handles IME and vertical overflow. Dismissal returns the
receipt unchanged; successful assignment returns it with the chosen category. Ordinary actions come
before optional bank details, so the new category action does not push them out of the compact sheet.
Merchant category learning remains the existing behavior.

## Verification

Room tests cover save, undo after another movement, deleting a local delta, opening protection and
rejecting a stale snapshot. UI tests cover default-hidden settings search and pulling down without
focus. The full app run found two large-font action-visibility failures after adding the category
block; moving secondary bank details after actions and reducing the block fixed both regressions.
Native journeys use disposable-emulator synthetic callbacks, never the owner's database or bank API.

Final host suite: 1035 tests, zero failures/errors, four skipped (`/tmp/whfin-polish-final-suite.log`).
Release R8/lintVital passed (`/tmp/whfin-polish-shipping.log`). Native EN/light and RU/dark/system
font 1.5 covered hidden/pull search, balance tap/cancel/zero and category search/selection/back.
Screenshots inspected: `/tmp/whfin-polish-settings`, `/tmp/whfin-polish-final-frames`, and the actual
Accounts destination at `/tmp/whfin-polish-accounts.png`. Native fixtures use synthetic callbacks;
Room tests verify the persisted ledger behavior separately. The initial native form test clicked Save
before recomposition enabled it; it now waits for the enabled action. Modal category selection uses
visible text because the parent activity's resource-tag semantics do not cross the modal window.

Both native journeys also passed with the real IME forced on; form actions and category results
remain visible at font 1.5. Frames inspected: `/tmp/whfin-polish-ime-frames`; log `/tmp/whfin-polish-ime.log`.
