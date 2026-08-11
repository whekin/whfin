# Statement import: the bank-neutral boundary

Status: boundary extracted 2026-07-30, Credo is the first adapter. TBC is the next adapter and must
not need any change in the shared pipeline.

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
  exchange, savings top-up) is the single place that decides what never counts as income or expense.
- `BankStatement` / `StatementRow` — one parsed statement for exactly one currency ledger: IBAN,
  currency, period, opening/closing balance, and signed minor-unit rows.
- `StatementFile` — the file buffered in memory so every adapter can probe the same bytes and the
  chosen adapter can then read them again.
- `StatementParser` — one adapter per bank: `bank`, `canParse`, `parse`, and `conversionNoteMarkers`.
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

Credo additionally treats punctuation, whitespace, case and column order as presentation rather than
schema. Sheet and metadata labels are normalized, and transaction columns are resolved from their
headers instead of fixed Excel letters. This tolerance is deliberately bounded: IBAN, currency,
period, both balance-summary values and all financial columns remain required. An unrecognized rename
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
