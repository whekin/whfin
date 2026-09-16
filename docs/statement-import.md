# Statement import: the bank-neutral boundary

Status: Credo and TBC XLSX adapters are available (2026-09-09). Both use the same import workflow.

## Why a boundary

“Statement is the source of truth” is a product invariant, not a Credo feature. Deduplication,
reconciliation with SMS drafts, transfer/conversion pairing, coverage, import history and the review
queue are the same for every bank. Only the file format differs. Keeping the pipeline generic is what
makes a second bank a parser, not a second import workflow.

## The contract

`dev.whekin.whfin.data.statement`

- `BankProfile` — stable `provider` key stored as `FinancialGroup.provider`, plus a display name.
  The importer creates the bank group and names new ledgers from this, so no bank name is hard-coded
  in the pipeline.
- `StatementOperation` — bank-neutral row semantics. `isOwnMovement` (own transfer, currency
  exchange, savings top-up) excludes bank-declared own movements from income and expense. Reciprocal
  cross-bank evidence and explicit owner links can also establish an internal transfer.
- `BankStatement` / `StatementRow` — one parsed statement for exactly one currency ledger: IBAN,
  currency, period, opening/closing balance, signed minor-unit rows, and optional bank transaction IDs.
- `StatementFile` — the file buffered in memory so every adapter can probe the same bytes and the
  chosen adapter can then read them again.
- `StatementParser` — one adapter per bank: `bank`, `canParse`, `parse`, `conversionNoteMarkers`,
  and optional `originAccountFromNote` for bank-specific reciprocal transfer evidence.
- `StatementParsers` — the registry. Adding a bank means adding an adapter to `all`, nothing else.
- `UnsupportedStatementException` — no adapter claimed the file. The UI shows
  `statements_unsupported` instead of a raw parser message.

`StatementImporter` never imports a bank-specific type.

## What a batch import asks before writing

Loading several XLSX files is routine, so the flow stays one gesture. Every picked file is read first
through `StatementImporter.preview()`, which writes nothing — not even the ledger the file describes —
and the batch is then decided once by `planStatementBatch`:

- A file that would change nothing (`Preview.changesNothing`) is dropped instead of imported into a
  `0 inserted` history row. The result names them together: "N files were already imported".
- A file that would bring a ledger into existence (`Preview.createsAccount`) stops the batch with one
  question naming every such ledger once. This is the only interruption, because it is the only
  mistake the result screen cannot undo: a wrong file leaves an account behind.
- Everything else imports exactly as before, with no extra tap.

`BankLedgerResolver` decides and writes separately for this reason. Adopting an IBAN-less ledger that
SMS routing created is `LedgerEffect.ADOPTED`, not a new account, and is never asked about. A
statement with no rows for a missing ledger still creates it and anchors its opening balance, so
`changesNothing` requires `LedgerEffect.UNCHANGED` and not merely "no rows to add".

MyCredo sync never asks the account question. Its files are not picked by hand: the accounts come
from the bank's own list after an authenticated login, so a wrong-file mistake cannot happen and a
ledger per remote currency account is the point of syncing.

It does use the "changed nothing" half. The bank re-serves the same period on every run, so an
account with no activity used to file a `0 added` record each time until the history was full of
them. The downloaded bytes are previewed, a statement that would add nothing is skipped, and the run
reports the count once at the end.

## How much history a sync asks for

`CredoSyncWindow` decides per account, because coverage is per account:

- nothing known about the account — the full twelve months, as the bank's own web export sends,
  down to the same instant;
- otherwise the account's own latest `periodTo`, never less than a month back, never more than
  twelve;
- a gap in the coverage pulls the window back to its start, so a sync repairs holes instead of
  stepping over them.

The month of overlap is deliberate: a card payment reaches the statement days after the purchase, so
a window starting exactly where coverage ended would keep missing the tail of every run. Repeated
rows cost nothing — `StatementIdentity` inserts them once.

## Reaching further back

`CredoHistoryScan` walks an account backwards a year at a time, behind a separate, explicit action.
One huge range would be worse: the workbook is held in memory whole while it is unzipped and parsed,
the request stops looking like the site's own, and one failure costs the entire history.

Nobody tells us where an account begins — the bank's account list carries no opening date, and
asking for one would risk the `accounts` query that gates the whole sync. The bottom is recognised
from the statements instead: the bank narrowing the period we asked for, a chunk with rows that
opens at zero, or a chunk that is empty and stands at zero throughout. An empty chunk alone is not a
signal — an account can sit untouched for a year with money on it. `MAX_CHUNKS` is a guard against a
protocol change turning the walk into a loop, not a stop condition.

Years of foreign rows arrive at once, and each *day* of them needs its own historical rate — one
request per day, not per row, and GEL rows need none. `backfill` caps a pass at forty days so
ordinary paths never burst into hundreds of requests, which would leave a deep load trickling its
numbers in over as many visits to statistics as it takes. The history walk therefore ends with
`backfillAll`, which repeats passes until one values nothing, and reports the days as it goes.

## Rules for a new adapter

1. Keep every bank-specific string, date format, column layout and operation vocabulary inside the
   adapter. Nothing bank-specific may leak into `StatementImporter`, Room entities, or UI.
2. `canParse` is a structural probe over the real bytes and must not throw. A broken probe must not
   block the adapters after it. Do not identify a bank by file name alone.
3. Map every known operation. An unmapped one degrades to `OTHER` while `operationRaw` keeps the raw
   bank name. Preview and import results surface the number of such labels; the row is imported as an
   ordinary non-transfer only after the same balance-chain proof as every known operation.
4. Amounts are signed minor units: debit negative, credit positive. The balance chain
   (`previous + amount == balanceAfter`) is the correctness check that catches column mistakes.
5. `conversionNoteMarkers` exists because transfer pairing runs over stored transactions long after
   the file is gone. Give the adapter's own conversion wording, do not extend the importer.
6. Real bank files, IBANs, names and amounts never enter the repository. Cover the adapter with
   generated fixtures; private files stay behind `WHFIN_REAL_STATEMENT` /
   `WHFIN_REAL_STATEMENTS_DIR`.

Credo own movements may cross the midnight bookkeeping boundary. Reconciliation therefore uses a
±1-day window only for an exact, unique SMS transfer on the same ledger and with the same amount;
ordinary purchases keep their date/merchant rule. A repeated import can repair a statement row that
an older version inserted beside that SMS: the SMS row is upgraded in place and the redundant
statement row is retired, preserving the original SMS transfer group.

Credo additionally treats punctuation, whitespace, case and column order as presentation rather than
schema. Sheet and metadata labels are normalized, and transaction columns are resolved from their
headers instead of fixed Excel letters. This tolerance is deliberately bounded: IBAN, currency,
period, both balance-summary values and all financial columns remain required. Both the legacy
`dd.MM.yyyy - dd.MM.yyyy` and current `dd/MM/yyyy : dd/MM/yyyy` period shapes are accepted. A zero-row
export is valid only when its opening and closing balances agree. An unrecognized rename
there fails before Room is touched instead of guessing GEL or importing without a balance proof.

## Test harness

- `app/src/sharedTest/.../SyntheticCredoWorkbook.kt` generates a Credo-shaped xlsx from synthetic
  data and is shared by JVM and instrumented tests. A TBC generator belongs next to it.
- `CredoSyntheticStatementTest` (JVM) — normalized metadata/sheet labels, reordered header-driven
  columns, required balance summary, period, signs, merchant/purchase date, own movement, unmapped
  operation, balance chain, registry routing, and refusal of a foreign workbook.
- `StatementParsersTest` (JVM) — routing to the first accepting adapter, unsupported format,
  a throwing probe, repeated reads of the same bytes, conversion vocabulary.
- `StatementImporterInstrumentedTest` (emulator, Room) — the shared pipeline: account and bank group
  created from the adapter profile, statement provenance, own-movement flags, ledger balance equal to
  the closing balance, re-import inserting nothing, and an unknown format touching no ledger. Also
  `preview()`: what it promises matches what the import then does, an already-imported file promises
  no change, and a statement adopting an SMS ledger does not promise a new account.
- `StatementBatchTest` (JVM) — the batch rule: unchanged files dropped, a draft-confirming file kept,
  one question per missing ledger however many files need it, adoption never asked about, and an
  unreadable file left for the import to report.
- `CredoSyncWindowTest` / `CredoHistoryScanTest` (JVM) — the two window rules on their own.
- `CredoSyncWindowWiringTest`, `CredoSyncSkipTest`, `CredoHistoryLoadTest`, `CredoConnectedScreenTest`
  (Robolectric) — that each account is asked for its own range, that a quiet account files no record,
  that the history walk abuts its chunks and stops, and what the connected screen says. A scripted
  gateway stands in for the bank: the real login needs the owner's own device and credentials.
- `CredoStatementParserTest` (JVM, opt-in) — the same structural invariants against private files.


## TBC XLSX and cross-bank transfers

TBC Online exports one `Summary` and an `IBAN-currency` sheet. Summary supplies the account,
currency, Excel-serial period endpoints, opening/closing balances and debit/credit totals. English
headers on the second transaction row identify columns; the Georgian header above is presentation.
One account and currency per file is supported. Extra sheets, missing metadata, duplicate IDs,
invalid money/dates, ambiguous debit/credit, reversed row order or inconsistent turnover stop the
file before writing. The shared validator also proves the running balance chain. CSV lacks account
metadata; PDF lacks transaction IDs. Neither is accepted by this adapter.

`POS -` and `POS wallet -` descriptions are card payments even when Type says
`Transfer Out And Cash Withdrawal`. Their merchant and English purchase timestamp are read from the
description; posting date remains separate. The observed `*TPC*` debit is a bank package fee. Unknown
operation types remain ordinary rows with a visible unmapped-label result. No SMS support is implied.

TBC's Transaction ID is scoped by IBAN and currency in the external key. Reimporting identical rows
is a no-op. A changed amount, date, balance or counterparty under that ID updates the existing row and
invalidates its old valuation, preserving an existing category and explicit OWN_LINK. A changed amount
linked to allocations or debt stops the file rather than invalidating those decisions. Credo's existing
keys remain unchanged. The latest imported file is authoritative; the export does not provide a row
revision timestamp. Missing rows are not inferred to be cancellations. The bank warns that exports may
contain unsettled transactions; cancellation/removal reconciliation remains unverified.

`CrossBankTransfers` joins only two imported statement rows from distinct active bank groups when
currency and opposite signed amounts agree, posting dates differ by at most three days, the debit
names the destination IBAN, and the credit names the source IBAN. For TBC processor credits, the
adapter may supply the origin printed after `ა/ნ:` while preserving the processor counterparty.
Both directions must have exactly one candidate. Matching amounts or owner names alone are insufficient.
Fee-category rows, allocations, debt-linked rows, voided rows, and existing manual groups are excluded.
An ambiguous pair remains available for the existing manual Own transfer flow. Separate fee rows
remain expenses; the amount difference never invents a fee, and no tariff is hard-coded.

Derived cross-bank groups are rebuilt after import and during transfer repair, so later evidence can
remove an ambiguous or obsolete pair. Explicit OWN_LINK groups are never rebuilt. This extends the
bank-derived transfer mechanism; it does not replace ADR-0005's manual links where reciprocal evidence
is absent. No database migration is needed.

`SyntheticTbcWorkbook`, `TbcStatementParserTest`, and `TbcImportTest` cover parsing, balance proof,
identity, correction, fees, opposite import orders, delayed posting, ambiguity, explicit links and
allocation protection. Optional private checks use the existing fixture environment variables and an
isolated Room database. `TbcStatementImportInstrumentedTest` checks Android XML/SQLite on an emulator.


TBC foreground API synchronization is described in [history sync](tbc-history-sync.md). It uses the
same planner/applier after a one-time XLSX opening, records TBC_SYNC provenance, and preserves both
mobile/file identities on one row. Missing API row balances are never manufactured.


## One counterparty, two alphabets (2026-09-16)

The alphabet a counterparty's name arrives in does not belong to the statement. It is whatever their
own bank holds on file: Credo registers its clients in Georgian, TBC and Bank of Georgia in Latin.
So a ledger fed by one bank still prints the same person both ways — once for their account at a
Georgian-writing bank and once for their account at a Latin-writing one — and the two spellings
share no character at all. Direction adds a second wobble: an incoming payment carries the sender's
name as their bank registered it, while an outgoing one can carry what the payer's form recorded.

The dictionary was keyed by exactly what the bank wrote, so it learned each spelling separately: a
category taught on one never reached the other, the same person appeared twice under "who was paid",
and the category queues asked about them twice.

The key stays what the bank wrote. `MerchantNormalizer.normalize` is what merchant memory is keyed
by, and rewriting it to a coarser form would orphan every category the owner has taught. Instead
`GeorgianLatin.skeleton` — the same coarse form already used to reconcile a statement line against
the message announcing it — is recorded in `merchant_aliases`, a table that has existed since the
first schema and that nothing wrote until now. Its unique `pattern` index then states the invariant
in the schema itself: one skeleton, one counterparty. No migration is involved, and portable backups
already carry the table.

`MerchantCategorizer.resolve` asks for the exact key first, then for the skeleton, and records the
skeleton for every merchant it creates.

Only cross-script pairs are joined, on both the live path and the repair pass. Within one alphabet a
bank is consistent, so two keys that collapse onto the same skeleton are far more likely two names
than one name written twice: the skeleton exists to drop what romanization cannot carry — `თ` and
`ტ` both become `t`, `კ` and `ქ` both become `k` — and dropping those inside Georgian would marry
strangers. Prefix equivalence is deliberately not applied here either: matching `ANTHROPIC` to
`ANTHROPIC* CLAUDE.AI` is right when one row is being weighed against one candidate on one account
on one day, under the other guards that path keeps, but as a dictionary rule it would collapse every
name that starts alike.

`repairCounterpartySpellings` joins what is already stored, once at startup. The survivor is the row
carrying a category, because that is the owner's own teaching, then the one with more operations
behind it, then the lower id. Operations move to the survivor and those still without a category
inherit the survivor's, which is the rule `categorizeUnassignedForMerchant` already applies when a
merchant becomes recognizable; a category set by hand is never overwritten.

Two spellings filed under different categories are **not** joined, and deliberately raise no
finding. They are evidence that the skeleton was too coarse here, not evidence of a contradiction,
and merging them would move money between categories on a guess. Agreeing the two categories by hand
is how the owner approves such a merge: the next pass then has nothing to weigh and joins them.
