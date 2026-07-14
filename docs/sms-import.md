# Credo SMS import contract

## Observed prototype failure mode

On a physical test device, WHFIN was configured to receive SMS:

- `RECEIVE_SMS` is granted;
- the WHFIN SMS toggle is enabled (its absent DataStore override means the default `true` is active);
- the private database contains statement transactions but no `source=SMS` rows;
- there are no `PaymentInstrument` rows, so a card payment cannot resolve `****last4` to a ledger;
- there are multiple GEL bank accounts, so a card-less GEL transfer cannot safely use `singleOrNull()`.

The receiver only observes broadcasts delivered after permission is granted. It does not read older
messages. `CredoSmsReceiver` currently discards parser failures, and `SmsTransactionImporter` returns
`null` when account resolution fails. Those two silent paths explain both the historical gap and why a
new valid Credo message can appear to do nothing.

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
