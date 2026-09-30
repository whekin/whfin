# Credo SMS import contract

## Implemented state

The original physical-device diagnosis found two silent paths: missing card mappings and ambiguous
same-currency bank accounts. The clean Room v1 contract records a structured local outcome for both instead of
dropping the message.

- `RECEIVE_SMS` still observes only broadcasts delivered after permission is granted.
- In first-run setup, bank sign-in and SMS monitoring are separate choices. The bank connections page
  enables monitoring per bank and requests SMS access only after its explicit opt-in.
- The existing normal-app Credo entry enables future transaction monitoring and requests `RECEIVE_SMS` plus `READ_SMS`:
  the latter supports a bounded foreground catch-up when an OEM omits the manifest receiver. While an
  OTP challenge is actively on screen, the exact `# SMS Code: 1234` login template
  can fill the four local code dots. The code is process-only, has no replay, is never submitted
  automatically, and payment/card OTP templates are deliberately excluded.
- The manifest receiver remains the background path for transaction monitoring. During MyCredo
  `Connecting` / `AwaitingOtp`, WHFIN also context-registers an exported receiver guarded by the
  system `BROADCAST_SMS` sender permission. This foreground fallback is scoped to the live login:
  Samsung One UI was observed delivering the bank SMS broadcast without including WHFIN's manifest
  receiver even while `RECEIVE_SMS` was granted. The OTP screen explains automatic fill and explicit
  confirmation. Whenever the app returns to the foreground with monitoring and `READ_SMS` enabled, it
  idempotently scans at most 500 Credo candidates since the last successful catch-up (one day on first
  use, with a five-minute overlap); the raw bodies are never persisted.
- The explicit 90-day history action still requires a dry-run and confirmation before importing old
  messages. Viewing or sharing one original remains a separate deliberate action.
- The scan is capped at 500 Credo candidates and produces a dry-run summary before any write.
- Raw message bodies exist only in parser/importer memory. `sms_diagnostics` stores a hash and parsed,
  masked fields; the table is excluded from portable JSON backup and cleared on restore.
- Card mapping and ambiguous-account outcomes open a product sheet, persist the chosen mapping when it is
  reusable, and retry. A new card route immediately backfills every compatible queued payment from that
  card; all routed operations enter the active ledger without a separate review task.
- SMS monitoring defaults off, but can be enabled before any card-to-ledger mapping exists. The receiver
  records a structured Unrouted operation when routing is incomplete; transaction creation remains gated
  on a complete route.
- A mapping is the exact four digits printed after the masked Credo card number plus its physical or
  virtual card type. The card is linked to every currency ledger under the selected bank + IBAN;
  the currency parsed from each SMS chooses the ledger at import time. Settings → Bank SMS is
  the permanent place to add another card; resolving a “Needs card mapping” outcome saves the same rule.
- Routed SMS rows use active status immediately and show `SMS` as provenance in transaction details.
  They remain eligible for statement reconciliation because `source=SMS` records evidence independently
  of status; the statement later replaces that provenance with `STATEMENT` while enriching the same row.
- `Deposit top-up` is parsed as an incoming deposit leg. A matching `Outgoing transfer` becomes one
  `SAVINGS` transfer only when amount and currency are equal, timestamps differ by no more than two
  minutes, both ledgers belong to the same bank group, and exactly one opposite candidate exists. A unique
  savings/reserve ledger is preferred for the deposit leg; ambiguous source or destination accounts still
  require an explicit choice. The raw message does not contain enough data to guess safely beyond that.

Parser-failure sharing is explicit and local. The first editor payload contains app/parser versions and
the structured outcome, but no original message or parsed private fields. Android Sharesheet opens only
after the user reviews that payload and presses Share; there is no automatic telemetry or upload.

Monitoring, routing, and import are separate. Monitoring may retain a structured Unrouted operation
before any card mapping, while ledger mutation still waits for complete routing. Product behavior and
implementation order: `docs/first-run-demo-and-bank-sms.md`.

## Product behavior

Bank SMS is a local operations workspace, not an inbox replacement and not analytics. Its hierarchy is
monitoring status, items that need action, recent processing activity, card/account routes, and the
optional bounded history scan. Parser diagnostics remain available as details of an individual result,
without defining the user-facing destination.

Each candidate message gets exactly one visible outcome:

| Outcome | Meaning | User action |
|---|---|---|
| Imported | An active transaction was added from SMS | Open transaction |
| Matched to statement | The SMS was attached as evidence to an existing confirmed row | Open transaction |
| Transfers matched as a group | The complete receipt set matches equal multiplicity of complete bank transfer pairs; no individual row identity is guessed | None; original receipts remain available |
| Already imported | Stable key matches an existing row | Open transaction |
| Needs card mapping | Card last four is known to the SMS but not WHFIN | Choose/create instrument and ledger |
| Choose account | More than one ledger can receive a transfer | Tap one derived route |
| Awaiting bank match | Every possible ledger is already covered by bank history, but no unique row has been matched yet | None; keep the SMS as evidence and retry reconciliation after bank data changes |
| Ignored | OTP, rejected payment, or unrelated message | None |
| Not recognized | Credo-like message does not match the parser | Share explicitly or copy |
| Error | Storage/platform failure | Retry; preserve diagnostic reason |

For card payments, routing is deliberately strict: `bank + card last4 → one physical/virtual card`, then
`card + balance currency → one active ledger`. Four digits are required. One card may be connected to
GEL, USD, EUR, and other ledgers under the same IBAN, so the card mapping is not duplicated per currency.
WHFIN never guesses from a bank name alone because one bank can contain several cards and several IBANs.
For reconciliation against existing bank rows, the purchase currency is also checked inside the known
card's contract: an available GEL balance in a message does not prove the purchase settled in GEL.
A row in the purchase currency requires the exact amount and merchant. A purchase can settle two
calendar days after its message; that additional day also requires an exact comparable amount.
Transfers retain their narrower date window and use both members of an existing bank transfer group
to distinguish equal withdrawals to different own IBANs.

Historical batch reconciliation also handles a bank-consolidated card charge when the bank arrived
first: the full day's unresolved cohort must share one card, merchant, currency and mapped ledger,
sum exactly to the sole bank purchase at that merchant on that day, and find no previously attached
evidence on that row. All messages then attach to the existing transaction without changing money.
Subsets, repeated bank purchases and mixed cards remain ambiguous. This is the explicit exception to
the ordinary one-message-to-one-bank-row rule.

Credo card descriptions can carry original purchase money even when the debit is converted into a
different currency. Existing known card contracts may be searched in their other currencies only
when that printed amount/currency supplies exact evidence. A narrow Amazon descriptor rule separates
marketplace and retail names and requires exact money, the known contract and a unique candidate;
it does not rewrite merchant/category memory or collapse arbitrary brand prefixes.

Indistinguishable own-transfer receipts can be matched as a complete cohort: equal count of bank
withdrawals, each with one receiving member of a permitted automatic bank group, on the same day,
with the same currency, amount and both IBANs. Occupied source rows and owner-created OWN_LINK groups
are excluded. MATCHED_GROUP has no transactionId; the original structured fields identify the cohort.
Its bank source rows remain reserved against individual matching while the proof holds. Every batch
rechecks the proof; changed multiplicity returns all receipts to quiet bank reconciliation without
redistributing them to remaining rows or writing money. This device-local outcome adds no columns,
Room migration or portable-backup data. See ADR 0003.

An account-free outgoing transfer is not a routing question when statements already cover **every**
eligible same-bank, same-currency ledger on its date. WHFIN leaves that SMS outside the ledger for
later bank matching; choosing one account cannot justify another transaction in a covered period.
Saving that mapping processes all already queued card payments that match one of those ledgers. Those
payments become active automatically; routing is not a review or approval decision.

A message that names no card and no IBAN still names the balance it left behind, and that figure
belonged to exactly one ledger. Routing therefore checks it before asking: for each candidate of the
message's currency, WHFIN takes the last balance the bank itself declared on that ledger at or before
the message — statement rows and earlier messages both carry one — adds whatever the ledger recorded
since, and adds this operation. Exactly one ledger reaching the printed figure decides; none and
several both stay a question, because a wrong route writes a real operation into an account it never
touched. The starting figure has to be the bank's own: our sum of rows would only assert that nothing
is missing, so a ledger with no declared balance behind it does not answer at all. Foreign-currency
card payments are excluded — the ledger moves by an amount the message never prints — and cards are
excluded on purpose: their message is asked about once in order to learn which ledger the card belongs
to, and a silent guess would trade that answer for one routed message and keep asking forever.

Credo deposit notifications omit both IBANs. WHFIN may use the paired outgoing/deposit notifications to
identify a single internal transfer, but account resolution remains a separate decision: it automatically
uses a unique reserve and a unique remaining source inside one bank group, otherwise diagnostics asks the
user. Matching is deliberately much narrower than statement transfer matching because equal recurring
amounts must not be silently merged.

Raw SMS bodies are processed in memory. From a diagnostic, the user can view the matching original body:
WHFIN hashes messages from a narrow time window in Android's SMS provider and displays the match on demand.
The body is never copied into Room and is explicitly unavailable if Android Messages no longer has it.
WHFIN persists only the resulting transaction and minimal diagnostic metadata needed to explain the outcome;
it never exports raw messages, OTPs, or parser samples in JSON backup.

## Historical scan

`RECEIVE_SMS` is the live-delivery path. `READ_SMS` is also requested when monitoring is enabled because
Samsung One UI has demonstrably omitted the manifest receiver; it powers the bounded foreground catch-up.
The separate 90-day historical import still starts only from the explicit “Scan existing Credo messages”
action after a prominent disclosure that explains local processing, scope, and retention.

The query should be bounded by likely Credo sender plus a user-visible time range/count. Before writing,
show a dry-run summary: importable, duplicate, needs mapping, ignored, and unrecognized. The user can
resolve mappings and then choose “Import recognized”; opening diagnostics must not mutate the ledger.

Google Play lists SMS-based money-management/budgeting as a possible permitted exception, but release
still requires the restricted-permissions declaration and review. Policy reference:
<https://support.google.com/googleplay/android-developer/answer/10208820>.

## Sharing a parser failure

There is no automatic upload. “Share parsing problem” first opens a WHFIN editor containing the app/parser
version, outcome/reason, and a redacted placeholder. The default report is built without reading or
reconstructing the SMS body, so amount, card mask, balance, names, account numbers, message fingerprint,
timestamps, and other identifiers cannot leak from diagnostic metadata.

“Include original SMS” is a separate action. It uses the existing narrow inbox lookup, previews the exact
raw body in a second confirmation, and then returns to the same editor instead of sharing immediately.
Only a final explicit Share opens Android Sharesheet with the exact text visible in the editor. Dismissing
the editor or permission flow clears the pending raw-message state; neither redacted nor raw report text
is written to Room or backup.

## Unrouted operations

Monitoring no longer waits for a card mapping. A parsed message whose ledger is unknown remains in
`sms_diagnostics` with `NEEDS_CARD_MAPPING` or `CHOOSE_ACCOUNT` and is projected into the Feed at
`occurredAt`. Because it is not a `Transaction`, it cannot affect balances, day/month totals, categories,
or statistics. Its contextual resolver can choose an existing currency-matching ledger or create a Credo
ledger in place, then calls the normal importer resolution path and remembers a card mapping when present.
If a matching confirmed statement row already exists, resolution records `ATTACHED` and points the
diagnostic at that row instead of creating a duplicate.

## Bill evidence after the first personal setup — 2026-09-23

A TBC `Mobile Balance Recharge` message with amount, provider and date is a bill. The provider is kept
in its diagnostic and ledger row, as for a Credo utility bill. A notice that a recipient declined a
transfer and the unspecified fee was returned is informational: TBC history supplies the booked debit
and credit, while the SMS supplies no separate amount to add.

When two same-amount bill rows lie on nearby statement days, a provider named by the SMS may narrow
them through the shared Georgian/Latin merchant comparison. If it does not make a single candidate,
the row waits. Choosing an account for a queued message settles routing only; it cannot create another
transaction inside a period that account's statement already covers. The 24 August Silknet duplicate
that exposed the bypass remains in the owner's database until the existing Data health repair is run.

## The statement answers first

A message only becomes a question after the imported statements have been asked. `SmsStatementEvidence`
searches every non-archived BANK/SAVINGS ledger of the message's currency for a confirmed `STATEMENT`
row within a day either side: a card is carried by normalized merchant plus amount, a conversion needs
both legs on file, and everything else needs the exact signed amount. The balance the bank stated breaks
ties that the amount cannot, because only one ledger stood at that figure. A row that another diagnostic
already points at is never taken, and an ambiguous set is left alone — the interactive resolver is the
answer to ambiguity, not a coin toss. Nothing is written to the ledger: the transaction is already bank
truth, so the diagnostic becomes `ATTACHED` evidence on it.

An exact card match also links the card: neither source knows the pair alone, since a statement never
prints a card number and a message never names an account. `linkForAccounts` then covers every currency
ledger of that IBAN, so later messages from the same card route without a statement. An existing mapping
is never overruled, and the instrument is created as `PHYSICAL_CARD` — the type is editable in Bank SMS
and affects only how the card is labelled.

Inside a period an import already covers, a message never writes to the ledger: finding no row there
means the bank printed it differently or WHFIN failed to read it, not that new money appeared. The
diagnostic keeps `STATEMENT_COVERS_PERIOD`, says so on the Bank SMS row, and the resolver warns
before a manual choice writes what the automatic paths declined to — overruling stays possible
because a bank does sometimes file the same money under another name.

`attachUnroutedToStatements()` runs after every statement import and when Bank SMS opens, so the two
layers meet in either order — including the common one where a bank connection back-fills a year of
statements and the phone's inbox is scanned afterwards.

Grouped own-account transfers and currency conversions stay one provisional Feed row. Their resolver
derives valid `from → to` pairs in the same bank group; tapping a pair creates a normal `TRANSFER` or
`CONVERSION` group plus both active signed legs inside one Room transaction. Currency exchange excludes
demand/term deposits and legacy savings ledgers. Pairs are computed from current accounts and are never
saved as scenarios. The generic one-account resolution path rejects grouped diagnostics, so
neither a legacy one-account route nor a partial choice can create a one-legged transfer. Bank SMS sends
grouped unresolved items back to their contextual Feed resolver.

## Interest names its deposit

Interest is the bank paying on a deposit, and the notice carries three facts: the deposit's own number,
the amount, and the balance left afterwards. Two of them were being thrown away.

The balance was not: `accountAtDeclaredBalance` already answers by it, taking the last balance the bank
itself declared on a ledger, adding everything recorded since, and adding this operation. That works,
and it works least well exactly where interest is paid. A demand deposit paying on each day's balance is
an account its owner keeps available and transfers out of constantly, so one unrecorded or duplicated
row since the last declared balance is enough for the arithmetic to miss — and it is right to miss
rather than guess. A quiet term deposit answers first time; the busy one never does.

So the number answers first. It is printed every time, means the same thing every time, and does not
depend on the ledger being complete. It is not derived from the IBAN: an interest notice never prints
one, and inferring the number from it would be a guess about how this bank numbers accounts, which is
wrong once and then routes real money into the wrong deposit forever. It is learnt the way a card is —
asked once, kept on `accounts.depositNumber`, and every later notice about that deposit, plus any
already queued, is placed without asking. An account already claiming a different number is never
overwritten; reaching the question at all means none claimed this one.

The question narrowed too. Which accounts are deposits is already stated, so both interest and a deposit
top-up ask only those, in routing and in the resolver sheet. It is asked of the **bank product** and
never of the fund role: available-or-reserve is the owner's statement about their own money, and the
account paying daily interest is precisely one they mark available. "No deposit in this currency" is
therefore a different answer from "several accounts", and the sheet offers adding one instead of
listing the current accounts that cannot be right.

## What the bank called it reaches both doors

A statement row carries a label read back into `StatementOperation`, and `ImportApplier` files a fee or
deposit interest by it before anything is known about a counterparty. A message about the same event
said the same thing and was never asked: SMS rows took a category only from a merchant, and interest has
none, so the identical operation arrived categorised through one door and blank through the other. Blank
is also why such a row showed "No description" — the feed falls back to the category name, and there was
none.

One mapping now serves both, keyed on the stored `SmsDiagnosticKind` rather than a parser's own types,
so it outlives the message text. It stays as narrow as the statement rule and for the same reason: a fee
is the bank's own charge and interest its own payment, while cash and transfers are left alone because
no template can say whether arriving money is income or the owner's own coming back.

Categories are still never seeded behind the owner, so the row stays blank until the category exists —
which is why the evidence that offers it now counts messages as well as statement rows. An operation
somebody only ever hears about by SMS, interest on a deposit no statement covers, previously produced no
evidence at all: the category it belonged in was never proposed, so the row could never stop being
blank.

## Verification order

1. Unit-test structured outcomes, monitoring without prior routing, account ambiguity, card mapping, duplicate handling,
   deposit-pair safeguards, and all golden parser samples.
2. On the disposable emulator, create explicit accounts/instruments and inject sanitized messages with
   `adb emu sms send`; assert receiver → outcome → active SMS row → duplicate behavior.
   Parser-failure report → redacted editor → exact-raw confirmation → editable report → Android
   Sharesheet has also been verified at dark theme + RU + font scale 1.5.
3. On a physical test device, run historical scan in dry-run mode first and compare outcome counts without writing.
4. Fix the private card/account mappings, import a deliberately selected batch, and verify balances are not
   double-counted after statement reconciliation.
5. Send one new real transaction message through the broadcast path and verify its visible diagnostic
   outcome. Physical-phone work stays manual/read-only until the user explicitly confirms an import.

## Repeated fares are repeated payments (2026-09-15)

Three bus rides of the same price on one day are three payments, and the ledger holds each of them
twice while both channels describe it — once from the message, once from the statement line. The
`duplicate_statement_row` check finds a suspect by account, amount and day, which is how it catches
a pair the two channels named differently; it is not proof that two payments are one payment.

Two things follow, and neither was true before. The check now reports as many doubled operations as
there are statement lines to be doubled by: with three messages and one printed line, exactly one
message is a copy and the other two are rides the statement has not printed yet. Calling all three
doubled would claim two payments that happened did not.

And folding gives every message a statement line **of its own** (`DuplicateFolding`). Which of two
indistinguishable lines a message folds into does not matter; how many lines get claimed does. The
previous action folded only when exactly one line existed, so it did nothing for repeated fares —
but nothing stopped the underlying merge from letting three messages retire into the same line, and
two real rides would have disappeared into the first. The merge itself now refuses a line that has
already absorbed a copy, so no caller can lose a payment that way, and a line naming a different
merchant is not a place to fold into at all. When the lines run out the remaining messages stay
listed, and the next sync that prints them makes them foldable.


## A bill is paid out of money that can be spent (2026-09-17)

Routing narrows the candidate accounts by what the message is about. Interest and a deposit top-up
ask only about deposits; a utility or service bill asks only about the accounts it could have been
paid from, which is the same statement read the other way round. Deposits are recognised by their
bank product and never by the fund role — a demand deposit paying on each day's balance is money its
owner keeps available, and is still not what a utility bill is paid from.

Before this, a bill payment matched no branch at all and fell through to every bank ledger of its
currency. On the owner's ledger that meant four accounts offered for one payment, two of them
deposits: rows that could only be wrong, in a list long enough to hide the two that could be right.

Emptiness is answered in the same terms as the question: when there is no spendable account of that
currency the sheet says so, rather than falling back to offering the deposits.

The stated balance still gets the first word, and now runs over the narrowed pool, so an account it
can prove is chosen without asking at all. It needs three things at once — a balance printed in the
message, a balance the bank itself declared on the candidate, and exactly one candidate whose
recorded movements since that declaration land on the printed figure. When any of them is missing
the question is asked, which is the honest outcome rather than a guess.
