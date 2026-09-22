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

## Phone evidence and follow-up

The 90-day SMS dry-run currently previews **counts only** before writing. The next interaction should
show proposed operations grouped by outcome and allow the owner to inspect a message before confirming
the batch. The default should import supported financial operations; messages that cannot be classified
must stay visible as parser diagnostics and must never change a balance. An explicit **Not an operation**
decision could collapse a known irrelevant format without deleting its evidence. That decision needs
a reversible, bank-scoped rule so later parser improvements can revisit other unrecognized messages.

The owner supplied read-only access to the connected Samsung on 2026-09-23. The original Credo payment
and `Canceled operation` messages arrived one second apart on 8 August. A synthetic replay of that exact
shape cancels correctly, and the portable backup has no active ledger row for either message. A saved
device-local diagnostic can still be stale; diagnostics are deliberately absent from portable backup.

TBC's 13 September notice says a 2 GEL transfer was not accepted and its unspecified fee was returned.
The portable ledger already has the 2 GEL debit and credit from bank history. The SMS must not invent a
third movement. It now classifies as an ignored declined-transfer notice rather than a parser failure.
TBC's `Mobile Balance Recharge` message of 12 September contains a 40 GEL payment, provider and date;
it was previously ignored. The parser now treats it as a bill, keeping the provider for categorization
and statement matching.

The one Data health finding is a 1 GEL Silknet bill on 24 August: the SMS remained active beside a
statement row for the same bill. The statement file covers 15 August–15 September. The statement has
two 1 GEL rows on adjacent days, and the old SMS path discarded the provider name, so amount-only
matching refused to choose. A later explicit account choice bypassed the statement-coverage guard and
wrote the duplicate. Matching now narrows same-amount bill candidates by a cross-script provider name,
and manual routing respects statement coverage. The existing Data health duplicate-folding action can
repair the already saved row; a synthetic regression confirmed this specific old unnamed-SMS shape.
No repair or installation was performed on the phone.

**Waiting for bank statement** remains a deliberate reconciliation state. Its individual diagnostics
cannot be identified from a portable backup because `sms_diagnostics` is device-local; the owner-facing
list or a diagnostic export is needed to review those exact rows.

## Verification and limits

The TBC connected-button regression was red before the change and green afterwards. Host tests for
TBC, Credo connected UI, and setup passed. Emulator-only journeys passed in EN light and RU dark at
font scale 1.5; a synthetic TBC connected result passed in EN dark. Screenshots were visually reviewed.
No live bank authentication, phone install, or personal-data migration was performed.
