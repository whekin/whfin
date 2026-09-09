# TBC foreground transaction synchronization

Added in 0.3.28 (40). Native TBC sign-in/session restoration now starts a foreground history sync;
the connected screen also has a Sync transactions action. It does not schedule background logins or
reuse a password. The same optional encrypted cookie session and WHFIN action gate remain in place.

Version 0.3.29 also accepts an owner-entered booked balance instead of the first XLSX. See
[bank API sync](bank-api-sync.md) for the full updated contract.

## Initial balance

Each currency ledger needs either a bank statement opening or an explicit owner-entered booked
balance before API rows are written. The latter is stored as USER_OPENING and is superseded by later
bank evidence. No dashboard/available balance is silently treated as an opening. The owner confirms
each ledger, including empty ledgers, or uses the optional statement-import action.

After the initial import, history is downloaded through the authenticated mobile API. The first read now walks all available pages; its completion is recorded as TBC_HISTORY.
Subsequent reads overlap a month, reaching further back after a missed run. Existing one-year imports
are extended once, adjusting the opening backwards across earlier booked rows to preserve the current
balance. Existing older history is retained. Neither an empty page nor a missing bank row is interpreted as cancellation or permission
to delete a transaction.

## Retrieval

GET /products/api/v1/cards expands product IBANs into currency ledgers using account.id, not card
or dashboard IDs. Non-card dashboard entries add savings accounts. Credit/child card products are
not included in this adapter. The data comes from the mobile host rmbgw.tbconline.ge; no mobile-cookie
authorization for web XLSX export is assumed.

POST /pfm/api/v1/transactions/history is paginated using transactionId and, if unexpectedly returned,
blocked-movement cursors. Repeated days do not end paging. Empty top-level arrays or rows genuinely
before the requested window end the read; repeated cursors, changed movement IDs during the read,
invalid order, malformed money/currency/status or a page limit abort that account. Blocked rows are
not imported. Signed decimal amounts are converted exactly to minor units.

The mobile listing date may differ from an XLSX posting date. Explicit POS invoice dates supply the
purchase day. Matching can use either independently supplied day, while an existing file retains its
posting date. A nearby unmatched opposite-source row with compatible money/description stops import
rather than becoming a possible duplicate.

## Identity and reconciliation

movementId is the mobile row identity. transactionId is retained during retrieval for cursors and
explicit own-transfer grouping; its equality to an XLSX Transaction ID is not assumed.

Mobile-only external keys use a namespaced, encoded movement ID. Once an API row and an XLSX row
match uniquely, the same transaction carries both source IDs in its external key. No new Room column
or migration is needed, and the ordinary backup preserves this association. Numeric file IDs remain
unchanged; linked keys retain a mobile suffix.

Cross-source matching is account/currency scoped and requires matching signed money and compatible
dates. Exact normalized receipt descriptions take precedence over merchant/counterparty matching,
so repeated purchases at one merchant can be distinguished by their full receipts. Correspondence
must be unique in both directions. Ambiguity aborts the account before mutation.

API after XLSX attaches an ID without overwriting richer file balances, counterparties, categories,
void/correction decisions or transfer links. XLSX after API upgrades the same row and retains its
mobile identity. Mobile-only changes use the existing guarded reconciliation seam; amounts linked
to splits/debts cannot silently change. Changed API money conflicting with a file-backed row requires
fresh file evidence. Unexpected file renumbering cannot silently duplicate a linked movement.

Each account applies in one Room transaction. Counts advance only after commit; other successfully
read accounts may still import when one account fails. Cancellation/auth failures during collection
stop before the apply phase. TBC_SYNC is recorded in import history and supported by portable backup.
Mobile sync does not claim a balance chain it was not given and is not negative evidence against an
unmatched SMS.

## Transfers

Generic INCOME/PAYMENTS/BANK_INSURE_TAX labels do not imply own movement. Explicit internal-transfer
or conversion descriptions plus a unique two-leg transaction group can name peer IBANs for the shared
pairing logic. Cross-bank Credo/TBC pairing still requires reciprocal account evidence. Existing XLSX
links are preserved; absent recipient evidence is not replaced by an amount-only guess. Fees remain
separate ordinary expenses. Manual OWN_LINK groups remain the owner's decision.

## Validation and remaining live check

Host tests exercise multiple pages on one day, cursor failures, changed pages, exact money, currency
and account scope, file/API arrival in both orders, repeated sync, same-merchant receipts, ambiguous
matches, file-ID changes, opening balances and transfers/fees. Emulator checks cover portable
backup/restore of linked IDs and TBC_SYNC, plus the initial-file and sync-result screens.

The owner has verified real sign-in and an account list. Actual mobile history retrieval and its
correspondence to the owner's file still need the first on-device sync after initialization with a statement or owner-entered balance.
The implementation uses primary-source protocol evidence documented in
[tbc-history-research.md](tbc-history-research.md) and never embeds private samples or credentials.
