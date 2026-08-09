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

MyCredo sync deliberately does not preview. Its files are not picked by hand: the accounts come from
the bank's own list after an authenticated login, so a wrong-file mistake cannot happen and a ledger
per remote currency account is the desired outcome. A daily sync also re-downloads twelve months and
usually inserts nothing, so a "nothing changed" question would fire almost every run.

## Rules for a new adapter

1. Keep every bank-specific string, date format, column layout and operation vocabulary inside the
   adapter. Nothing bank-specific may leak into `StatementImporter`, Room entities, or UI.
2. `canParse` is a structural probe over the real bytes and must not throw. A broken probe must not
   block the adapters after it. Do not identify a bank by file name alone.
3. Map every known operation. An unmapped one degrades to `OTHER` while `operationRaw` keeps the raw
   bank name, so the gap is visible instead of silently mis-categorized.
4. Amounts are signed minor units: debit negative, credit positive. The balance chain
   (`previous + amount == balanceAfter`) is the correctness check that catches column mistakes.
5. `conversionNoteMarkers` exists because transfer pairing runs over stored transactions long after
   the file is gone. Give the adapter's own conversion wording, do not extend the importer.
6. Real bank files, IBANs, names and amounts never enter the repository. Cover the adapter with
   generated fixtures; private files stay behind `WHFIN_REAL_STATEMENT` /
   `WHFIN_REAL_STATEMENTS_DIR`.

## Test harness

- `app/src/sharedTest/.../SyntheticCredoWorkbook.kt` generates a Credo-shaped xlsx from synthetic
  data and is shared by JVM and instrumented tests. A TBC generator belongs next to it.
- `CredoSyntheticStatementTest` (JVM) — metadata, period, signs, merchant/purchase date, own
  movement, unmapped operation, balance chain, registry routing, and refusal of a foreign workbook.
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
- `CredoStatementParserTest` (JVM, opt-in) — the same structural invariants against private files.
