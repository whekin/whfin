# Credo SMS import contract

## Implemented state

The original physical-device diagnosis found two silent paths: missing card mappings and ambiguous
same-currency bank accounts. Room DB v3 now records a structured local outcome for both instead of
dropping the message.

- `RECEIVE_SMS` still observes only broadcasts delivered after permission is granted.
- `READ_SMS` is requested only from the explicit 90-day history action.
- The scan is capped at 500 Credo candidates and produces a dry-run summary before any write.
- Raw message bodies exist only in parser/importer memory. `sms_diagnostics` stores a hash and parsed,
  masked fields; the table is excluded from portable JSON backup and cleared on restore.
- Card mapping and ambiguous-account outcomes open a product sheet, persist the chosen mapping, and retry.

The remaining product gap is explicit parser-failure sharing. It must use a user-initiated Android
Sharesheet with an editable redacted payload; there is still no automatic telemetry or upload.

## Product behavior

SMS diagnostics is a local audit surface, not an inbox replacement and not analytics.

Each candidate message gets exactly one visible outcome:

| Outcome | Meaning | User action |
|---|---|---|
| Imported | A pending transaction was created | Open transaction |
| Already imported | Stable key matches an existing row | Open transaction |
| Needs card mapping | Card last four is known to the SMS but not WHFIN | Choose/create instrument and ledger |
| Choose account | More than one ledger can receive a transfer | Select once and optionally remember rule |
| Ignored | OTP, rejected payment, or unrelated message | None |
| Not recognized | Credo-like message does not match the parser | Share explicitly or copy |
| Error | Storage/platform failure | Retry; preserve diagnostic reason |

Raw SMS bodies are processed in memory. WHFIN persists only the resulting transaction and minimal
diagnostic metadata needed to explain the outcome; it never exports raw messages, OTPs, or parser
samples in JSON backup.

## Historical scan

`RECEIVE_SMS` remains sufficient for future broadcasts. Reading messages already on the phone requires
the separate restricted `READ_SMS` permission. The app may request it only from an explicit
“Scan existing Credo messages” action after a prominent disclosure that explains local processing,
scope, and retention.

The query should be bounded by likely Credo sender plus a user-visible time range/count. Before writing,
show a dry-run summary: importable, duplicate, needs mapping, ignored, and unrecognized. The user can
resolve mappings and then choose “Import recognized”; opening diagnostics must not mutate the ledger.

Google Play lists SMS-based money-management/budgeting as a possible permitted exception, but release
still requires the restricted-permissions declaration and review. Policy reference:
<https://support.google.com/googleplay/android-developer/answer/10208820>.

## Sharing a parser failure

There is no automatic upload. “Share parsing problem” opens the Android Sharesheet with app/parser
version and an editable redacted message. Amount, card mask, balance, names, and identifiers are removed
by default. A separate confirmation may include the raw body when the user deliberately decides the
parser needs it; WHFIN must preview the exact payload first.

## Verification order

1. Unit-test structured outcomes, account ambiguity, card mapping, duplicate handling, and all golden
   parser samples.
2. On the disposable emulator, create explicit accounts/instruments and inject sanitized messages with
   `adb emu sms send`; assert receiver → outcome → pending row → duplicate behavior.
3. On a physical test device, run historical scan in dry-run mode first and compare outcome counts without writing.
4. Fix the private card/account mappings, import a deliberately selected batch, and verify balances are not
   double-counted after statement reconciliation.
5. Send one new real transaction message through the broadcast path and verify its visible diagnostic
   outcome. Physical-phone work stays manual/read-only until the user explicitly confirms an import.
