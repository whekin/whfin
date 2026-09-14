# Foreground bank synchronization

Version 0.3.29 adds a persistent Sync banks action to Home. It opens Credo/TBC rows with the age of
the last completed run, including successful no-op runs. The action is available at any time. Seven
days is a freshness hint, not a scheduler: WHFIN never sends an OTP in the background. The existing
Credo Home reminder now uses seven days. TBC's saved session can resume through the existing sensitive
action gate; otherwise the native login remains explicit.

Choosing a supported bank in Add account first offers its connection. Name/currency/opening/product
fields appear only after the owner chooses Create manually. Selecting another bank resets that choice.
Cash and wallet creation keep their existing paths. Successful native Credo login initiated by the form
now starts the routine sync; opening the screen alone does not initiate a bank login.

## First synchronization (0.3.30)

The first foreground sync now loads all available history automatically, per account. Credo first
loads its recent window, then lists all history pages with an explicit dateFrom in 1970 (avoiding a server-default recent window) to find the oldest booked
operation. It walks yearly XLSX windows down to that boundary. Zero openings and empty middle years
do not stop the walk. Partial failures retain imported rows and counts but do not mark history complete;
a later sync resumes. Previous completion flags derived from zero balances are invalidated once.
Newly discovered accounts take this full path even when the other accounts are already complete.

TBC's first read uses no local lower-date cutoff: pagination continues to the bank's empty terminal
page. TBC_HISTORY records the completed read in the database and portable backup. Existing one-year
imports are extended on their next sync; known openings are walked back across new older API rows
without changing the current book balance. Subsequent reads overlap the last month and resume at the
last successful period if that is older. The page/chunk guards raise errors rather than truncating
history silently. Available history is not automatically proof of account-opening history: TBC still
needs a statement or the owner's booked balance when no opening evidence exists.

## Credo direct history

The existing authentication remains unchanged. Routine sync uses GraphQL transactionPagingList with
accountIdList, dateFrom and numbered pages (30 rows). pageCount and totalItemCount must remain stable;
repeated IDs, truncated pages or inconsistent totals abort before applying that account. Posted rows
are identified by stmtEntryId; transactionId is not assumed equivalent to an XLSX identity.

Each posted row is checked against customer.transactions(stmtEntryId): account number, currency,
signed debit/credit, transaction ID and calendar date must agree. Details supply counterparties and
the opposite transfer account. isCardBlock rows are excluded. Money converts exactly to minor units;
unknown bank labels stay OTHER. Only explicit bank movement labels imply own transfers. Reciprocal
Credo/TBC transfer pairing and separate fee rows retain the existing shared rules.

The first import uses automatic XLSX export to establish the opening. The automatic older-history
walk also uses XLSX, with its extent determined by API. A product without a history ID cannot establish
that extent and reports the limitation instead of declaring its history complete. Some foreign-card conversions are API
presentation pairs rather than independent statement movements; these detected groups also use the
existing automatic export instead of guessing a net amount. Normal subsequent runs fetch JSON.
Ambiguous API/file correspondence stops the account, leaving its ledger unchanged.

Credo API identities have a separate namespace. ApiSourceBridge shares the uniqueness checks with
TBC while preserving legacy file keys (including Credo ordinal/balance keys). API after a file attaches
its ID without erasing richer balances/categories; file after API upgrades the same row and retains
both identities. Repeated syncs remain no-ops. CREDO_API is a separate import origin, supported in
portable backup. It is never negative evidence that an unmatched SMS did not happen.

Primary protocol references inspected for this implementation:
- [Credo requests](https://github.com/zenmoney/ZenPlugins/blob/master/src/plugins/credo-ge/fetchApi.ts)
- [Response types](https://github.com/zenmoney/ZenPlugins/blob/master/src/plugins/credo-ge/models.ts)
- [Conversion representation](https://github.com/zenmoney/ZenPlugins/blob/master/src/plugins/credo-ge/converters.ts)

## TBC without an initial file

The owner can enter the current **booked** balance for each newly read currency ledger. Available
balance must not substitute for booked balance because holds can already have reduced it. The screen
explains the distinction and offers XLSX as an alternative. No dashboard amount is silently accepted.

The exact fetched history stays in memory while the owner enters the amount. Initial opening equals
the entered balance minus the net of those rows, using checked integer arithmetic. Confirmation creates
or uniquely adopts the ledger, records USER_OPENING and imports the rows in one transaction. A read
older than fifteen minutes must be refreshed. A second confirmation after initialization is refused.
The owner should refresh first if bank transactions changed during entry. This is explicitly an
owner-supplied estimate, not bank-proven balance evidence.

USER_OPENING survives portable backup and cannot be removed as an ineffective import. It does not
supply negative SMS coverage. Later bank statements supersede that provisional opening. If the bank
statement starts later, its opening is walked back across the retained earlier booked rows so the
existing API history is neither discarded nor counted twice. No fabricated per-row balances are added.

Mobile auto-XLSX remains unverified; [research](tbc-initial-sync-research.md) records that boundary.
The manual balance path removes the mandatory file step without assuming mobile cookies authorize web
export. Actual bank protocol behavior still needs owner-driven device verification; synthetic transport,
Room and emulator tests establish application behavior, not availability of the private endpoints.

## Validation

Full host suite: 977 tests, no failures/errors, four skipped. A later 20-test importer pass includes
one additional same-period opening-priority regression. Release assembly with R8 and lintVital passed.
The disposable Pixel emulator exercised RU/EN, light/dark, system font scale 1.5, account connection
and explicit manual refusal, bank switching, the actual Home-to-TBC route, TBC balance entry with a
visible software keyboard, and backup/restore of USER_OPENING plus linked API/file identities.
Twelve UI cases passed; six affected cases including backup were repeated after the final QA updates.
No owner data or live bank login was used for these checks. The physical phone was disconnected.

0.3.30 validation: full host unit/Compose suite passed, then 46 history tests passed after the added
multi-year cases. The four-year Credo scenario crosses a zero opening and a completely empty middle
year; TBC reads older-than-year rows and switches to an incremental window after the first completed
read. Existing openings retain the current balance when older API rows arrive. Release R8/lintVital
and public-tree checks passed. A disposable-emulator portable backup/restore test preserves
TBC_HISTORY and repeated sync identity. No live bank history was requested; phone was disconnected.

## Local route diagnostics (0.3.47)

Credo emits fixed events under `WHFIN_CREDO_SYNC`: account position, API page/row counts,
API apply counts, missing opening/history ID, conversion-group fallback, XLSX download and
account error. The logging API accepts only an enum and two nonnegative integer counters.
It cannot accept server messages, account identifiers, credentials or raw responses. These are
local Logcat diagnostics, not persisted bank evidence and not included in portable backup.
They do not change routing, filtering or financial writes. A prior XLSX import does not prove
which fallback caused it; diagnosis needs a fresh foreground run with these events.
