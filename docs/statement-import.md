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
  the closing balance, re-import inserting nothing, and an unknown format touching no ledger.
- `CredoStatementParserTest` (JVM, opt-in) — the same structural invariants against private files.
