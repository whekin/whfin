Current pending-charge support (0.3.42): [TBC pending purchases](tbc-pending.md).

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

## Zero booked balances

A booked balance can be zero or negative. Its field uses exact minor-unit parsing independently of
transaction amount validation, which rejects zero. Blank, over-precision and overflowing amounts are
invalid. Initial confirmation identifies a ledger by IBAN and currency, so four currencies sharing
one IBAN can each be confirmed at zero. Empty history still persists the opening and full-history
marker; a subsequent sync must not ask for that opening again.

## Correcting an owner-entered starting balance

Account activity → account actions → Correct starting balance opens a preview of the current ledger
balance and the desired balance after correction. This fixes an erroneous setup amount; it does not
record a new movement or use a fresh bank read. The target includes exactly the already recorded
active rows shown in the ledger, including any manual/SMS rows.

`UserOpeningCorrection` reads the account, its single USER_OPENING snapshot and all active rows in
one transaction. Saving compares that snapshot again, applies the difference to the stored opening
and rebuilds `OpeningAnchor` in the same Room transaction. No transaction amounts, links, allocations,
categories or dates change except the opening anchor. Its transfer semantics exclude it from income
and expenses. Zero can remove the anchor row while retaining USER_OPENING. Other currency ledgers
are untouched. The existing backup representation preserves the corrected opening without a schema
change. A later authoritative statement still supersedes the owner-entered amount.

The action is available only for an owner-entered opening with no authoritative opening evidence.
A changed ledger or a statement arriving while the form is open invalidates the preview; the form
asks to reopen instead of silently applying a difference to a new balance. Ordinary balance
adjustments remain a separate action for a new unexplained difference.

## Diagnosing a zero-result sync (0.3.40)

The owner reported a confirmed sign-in with zero new/matched rows and two unchanged accounts.
This summary alone cannot distinguish empty bank history from rows already present in WHFIN.
The cause of the missing expected transactions has not yet been established.

Read details now exposes per-ledger read counts, duplicates, full/recent window selection, pages,
parsed rows, skipped holds and an explicitly empty first response. Reports live only in the current
ViewModel/gateway session. The UI uses masked labels; no raw response, credentials or identifiers
are added to Logcat, disk or backup. API request selection and money import behavior are unchanged.

Regression: `:app:testDebugUnitTest --tests '*TbcHistorySyncTest.reportDistinguishesEmptyBankHistoryFromAlreadyImportedRows'`
failed before reports were populated (expected one report, received zero). All 50 history/login
checks passed after implementation, including an empty first response versus older rows filtered
out by the requested date window. Release assembly/R8/lintVital and two native rendering cases
passed. The diagnostic release was installed with data preserved; the next evidence is an owner-run
sync with Read details expanded. No live bank request was initiated by the agent for this stage.

The owner-run 0.3.40 read established that booked GEL history was being downloaded: four received
rows were already in the ledger. Holds were separate. Follow-up journal inspection found a purchase
rejected because of its Ertguli loyalty footer; 0.3.41 fixes that push template (see tbc-push.md).
No API history selector or blocked-movement posting rule was changed for this finding.

## Deposits, including My Safe (0.3.55)

The owner opened a My Safe product and WHFIN showed nothing at all: account discovery read only
`GET /products/api/v1/cards` and `GET /dashboard/api/v1/cards-and-accounts`, and TBC keeps savings
and term products in a third listing. Those two calls cannot return a product they do not carry, so
no error appeared either — the deposit simply did not exist for the app.

`GET /deposits/api/v1/deposits` now lists them and `GET /deposits/api/v1/statements/{id}` reads one
product's movements. Both are mobile endpoints on the same authenticated session, documented in
[tbc-connector-research.md](tbc-connector-research.md). The details route is not called: it carries
interest rates and dates, not money WHFIN books. A second listing page is refused rather than
silently dropping products, and the deposit id must be a bank number before it reaches the URL path.

A deposit movement is `depositAmount`, `interestedAmount` and `withdrawnDepositAmount` with the
running `balance`, not a PFM movement. Two things the response does not state are settled by that
chain instead of by assumption: which end of the list is the oldest row, and whether a withdrawal is
printed positive to subtract or already signed. The documented convention is tried first and the
alternative only when at least two rows can prove it; a list that proves neither is refused. A row
that moves nothing is not written, and the same chain check then still has to hold across it.

The opening is the bank's own arithmetic — the balance before the earliest returned row — so a
deposit never asks the owner for a booked balance the way a card ledger does. A truncated list stays
safe for the same reason: the anchor describes only the imported period. When the product's own
`currentAmount` disagrees with the movements it returned, the import still happens but the read
details say so, because that gap means the list is not the whole story.

Interest is INTEREST; top-ups and withdrawals are SAVINGS_TOPUP, which is own movement and therefore
outside income and expenses — the counter leg is on the current account. A combination the bank has
not been observed to print stays a visible OTHER row rather than a guessed classification.

The ledger is identified by the deposit IBAN and currency. An IBAN-less ledger is never adopted
here, unlike a statement import: those rows are created by card SMS, and handing one to a deposit
would move a savings history onto everyday money. An account number that is not a TBC IBAN is a
reported question, not an invented identity. A product already read as a currency ledger is skipped.
On creation the product type follows the bank's own `addAmountPossibility`; Available/Reserve stays
the owner's statement. Movements are deduplicated by the ordinary statement identity, so a repeated
sync is a no-op, and the first import is recorded as TBC_HISTORY.

Failure is scoped: an unreadable deposit listing costs the card accounts nothing, and one bad
deposit does not stop the others. Session, protection and rate-limit failures still stop the run.

Host tests cover the listing, a newest-first chain reversed into an oldest-first statement with its
derived opening, zero rows, a broken chain, the signed-withdrawal alternative and its refusal on a
single row, path safety, ledger creation with the bank's product type, repeat sync, the balance gap,
a non-IBAN account number, a failed listing beside a working card account, and an SMS ledger that is
not adopted. What they cannot establish is the shape of the owner's real My Safe response: an
owner-run sync is the next evidence, and an unfamiliar shape surfaces as DEPOSIT_FORMAT,
DEPOSIT_ACCOUNT or DEPOSIT_CHAIN in the read details rather than as silence.

## Source priority and the offered opening (0.3.56)

The first owner-run sync after 0.3.55 showed the product as an ordinary ledger: TBC lists My Safe in
`cards-and-accounts` too, its PFM history returned a single row, and WHFIN asked for a booked
balance the way it does for any first card-account import. Two products in one run would also have
been read twice, so the deposit listing now decides the family: a key it names is skipped by the
card-history pass entirely, and the deposit endpoint — the only one printing a running balance —
supplies both the movements and the opening. That is what removes the balance question for it.

Switching source is only safe on a ledger the card history has not already written. A ledger holding
rows with mobile movement IDs is therefore reported as DEPOSIT_MIXED and left alone: the deposit
statement cannot name those IDs, so importing the same money from the other side would duplicate it
instead of recognizing it. Choosing by the bank's own listing, rather than by what WHFIN imported
first, keeps the choice stable across runs.

For the accounts that do still ask, the field is now filled in with the figure the bank prints for
that account (`balance` on the card product, `amount` on the dashboard) instead of starting empty.
This is an offer to check, not an anchor: the response never states whether that figure is booked or
already net of pending charges, so the note beside it says so and the owner still confirms
explicitly. An account the bank said nothing about keeps an empty field and a disabled action.

## Confirming a balance finishes the account (2026-09-15)

Applying an owner-entered balance used to drop the account from `needsStatement` and nothing else.
Its report still said it was waiting, so the row disappeared from the attention section without
appearing among the accounts: a finished import looked like nothing had happened, and the only
visible thing left to try was running the sync again. The result now moves that account from asking
to reporting in one step — `afterInitialBalance` clears the wait and writes what the import actually
did — so confirming the balance is the last thing a new account needs. The rows it imports are the
ones already read during that sync; no second pass fetches them.
