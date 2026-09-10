# TBC pending card charges — 0.3.42

The explicit foreground sync now includes supported `BlockedTransaction` entries as active,
provisional spending. They reduce the locally available balance and participate in spending totals,
like an already routed push, but carry `BANK_HOLD` source and `PENDING` status. The UI says Pending
bank charge / Ожидает списания. They do not enter the manual review filter and have no one-tap
confirmation: only bank posting confirms settlement.

## Retrieval and identity

Holds are taken from the existing history response, not from dashboard balances. The observed mobile
endpoint returns them even with its existing `showBlockedTransactions: false` request. No new login,
background OTP or speculative payment endpoint is introduced. The hold's own IBAN and currency choose
its ledger: the API can repeat GEL holds in USD/EUR/GBP responses. Blocked cursors continue paging even
when a returned hold belongs to another currency. Missing/malformed identity, unknown status or
unsupported credit shape aborts that account rather than producing invented money.

Ordinary movement IDs are absent on holds. A bank/account/card/timestamp fingerprint identifies the
hold; money is deliberately excluded so a changed amount can update the same row. Changed duplicate
pages are rejected. Card suffixes come from the product's card-id mapping when available. The parser
uses explicit blocked timestamp, negative amount, currency, title, card ID and blocked IBAN. It never
uses `isDebit` to invert an already signed amount or available balance as an opening anchor.

Primary protocol evidence: the public connector's
[blocked-transaction fixture](https://github.com/zenmoney/ZenPlugins/blob/master/src/plugins/tbc-ge/__tests__/convertersV2/transactions/blockedTransaction.test.ts)
and [model](https://github.com/zenmoney/ZenPlugins/blob/master/src/plugins/tbc-ge/models.ts).
These are third-party implementation evidence, not a bank guarantee. Published WHFIN fixtures are
synthetic. Live future bank changes remain an explicit format error, not a fallback guess.

## One purchase across sources

`bank_holds` stores a durable fingerprint → transaction ID alias plus the financial matching fields.
No raw HTTP response or secret is retained. A hold matches an existing routed push/SMS using ledger,
currency, exact amount, merchant and minute, checking card suffix when known. Matching is one-to-one
in both directions for the entire hold batch. Two indistinguishable holds cannot arbitrarily consume
one push. Unrouted push evidence can attach after the API supplies its ledger; later manual card
routing also checks hold evidence before writing a new expense.

A new hold creates one pending transaction. A matched push keeps its ID and category and becomes
pending bank evidence. Later API/XLSX posting uses the shared reconciliation path and retains that ID,
user category, allocations and explicit own-transfer links. Receipt purchase time disambiguates
same-day purchases when supplied; ambiguity stops import. A changed amount attached to allocations
or a debt is refused. A posted row is never reverted by a stale hold response. Alias links survive
posting, duplicate merge and portable restore, so late pushes or old hold pages cannot recreate it.

The initial owner-entered amount remains the **booked** balance: opening is calculated from booked
history only, then active holds reduce the local balance. Each account's booked changes, holds and
message attachment run in one Room transaction. An error rolls back that account's entire change.

## Disappearance and owner decisions

Absence from one response is not cancellation evidence. An unmatched disappearing hold stays pending
until matched with posting or explicitly withdrawn by the owner. The existing correction/restore
workflow supports BANK_HOLD; it does not hard-delete bank evidence. A stale hold does not resurrect a
withdrawn row. Unambiguous later settlement preserves withdrawal by attaching its identity; a conflict
with the owner's withdrawn evidence stops import.

## Persistence and checks

Room 6→7 adds only `bank_holds`, with foreign keys and indices for account/transaction. Portable backup
includes this financial identity table and accepts the BANK_HOLD source. Older version-6 backups need
not contain the new table. SMS diagnostics and raw push journal retain their previous private scopes.

Tests cover first balance initialization with holds, push first, hold first, late routing, repeat sync,
settlement/category/ID preservation, different purchases at different minutes, identical holds,
changed allocated money, explicit withdrawal, stale pages and missing holds. Native tests validate
migration preserving an existing balance, pending/settled aliases through backup restore, and EN/light
plus RU/dark/font 1.5 status rendering without a confirmation button.

Validation logs: `/tmp/whfin-tbc-holds-final.log` (full app suite: 1052 tests, zero failures/errors,
four skipped), followed by the additional changed-amount reporting regression and focused run in
`/tmp/whfin-tbc-holds-shipping.log`. Native migration, backup and two status-render cases passed
(`/tmp/whfin-tbc-holds-native.log`, four tests). UI frames inspected at `/tmp/whfin-tbc-holds-ui`.
The emulator had to be restarted before native QA; no tests ran on the owner's phone.
