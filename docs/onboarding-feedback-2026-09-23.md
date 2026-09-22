# First personal-setup feedback — 2026-09-23

## Verified changes

- Credo's initial history is a full bank history walk and may take minutes. Its work is owned by
  the application, so setup now offers **Continue setup while history loads** once sync starts.
  The bank page still shows progress when reopened; no history is silently shortened.
- A connected TBC page now has **Done**, returning to the bank step without a system Back gesture.
- The bank step names Credo SMS and TBC SMS separately, shows their opt-in/permission state, and
  opens the older-SMS check. Bank sign-in alone does not enable SMS monitoring.
- The account step opens the cash editor directly, including today's optional opening amount, and
  prompts a review of physical/virtual cards, the primary card, bank product and fund role. Continue
  remains available without entering cash.

## Follow-up grounded in the owner's examples

The 90-day SMS dry-run currently previews **counts only** before writing. The next interaction should
show proposed operations grouped by outcome and allow the owner to inspect a message before confirming
the batch. The default should import supported financial operations; messages that cannot be classified
must stay visible as parser diagnostics and must never change a balance. An explicit **Not an operation**
decision could collapse a known irrelevant format without deleting its evidence. That decision needs
a reversible, bank-scoped rule so later parser improvements can revisit other unrecognized messages.

The reported `Canceled operation` and fee-return formats need redacted exact SMS text before changing
the parser. The existing Credo cancellation parser accepts a payment body with amount, card, merchant and
timestamp; the importer can also refuse a cancellation when no unambiguous original payment is found.
Those are different failure modes. Likewise, **waiting for bank statement** can be an intentional
reconciliation state, not an import failure. Inspect the diagnostic reason and the related statement
period before changing that behavior.

The single Data health finding needs its code and affected row before repair. The checker includes
duplicate SMS/statement rows as well as broken ledger invariants; their remedies differ. No personal
database was read or changed for this report.

## Verification and limits

The TBC connected-button regression was red before the change and green afterwards. Host tests for
TBC, Credo connected UI, and setup passed. Emulator-only journeys passed in EN light and RU dark at
font scale 1.5; a synthetic TBC connected result passed in EN dark. Screenshots were visually reviewed.
No live bank authentication, phone install, or personal-data migration was performed.
