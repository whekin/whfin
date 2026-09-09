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

The first import and products without history IDs use automatic XLSX export to establish the opening.
The existing explicit older-history scan remains XLSX based. Some foreign-card conversions are API
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
