# Bank message formats: Credo coverage audit

2026-09-09 baseline, before SMS expansion. Compared all **32 Credo formats / 40
examples** from zenmoney/sms-formats commit
`beba5557781f3e8cbae7705665eb22ea3b83a8c5` with
`app/src/main/java/dev/whekin/whfin/data/sms/CredoSmsParser.kt` (schema 2).
Source: [pinned Credo format directory](https://github.com/zenmoney/sms-formats/tree/beba5557781f3e8cbae7705665eb22ea3b83a8c5/src/Credo%20Bank-ge_15880/formats).
These community regexes document observed examples, not guaranteed bank behavior.

Verification: invoked the existing debug-compiled parser from a temporary Java
harness against the public examples. Results: **8 parsed, 17 unrecognized, 15
ignored**. Six parsed examples retain the usable fields; two lose supplied data.
Of the ignored examples, one is OTP, one is an unsuccessful cancellation parse,
and thirteen are financial messages ignored as unrelated. The upstream examples
are single-line strings; this is a representation issue worth supporting, not
proof that all delivered messages are single-line. Public examples and harness
remain under `/tmp/whfin-credo-sms-research/`; no original sample is copied here.

## Coverage by upstream format ID

IDs are the numeric suffixes of files in the linked directory. Grouped rows
exhaust all 32 formats. “Missing” distinguishes parser rejection from correctly
ignoring a non-transaction.

| Format IDs | Kind | Baseline result / cause |
| --- | --- | --- |
| 1590 | Older outgoing, US date with AM/PM | Unrecognized; unsupported opening. |
| 5624 | Transliteration cashback | Ignored unrelated; unsupported opening. |
| 6015 | Single-line cancellation + card payment | Ignored rejected; payment reader requires merchant on next line. |
| 11543 | Transliteration P2P income with account IBAN | Ignored unrelated. |
| 5772 | Transliteration card refund | Ignored unrelated. |
| 4767 | Interest prefixed `Credo Bank:` | Unrecognized; prefix hides supported interest opening. |
| 5543 | Older English card operation; placeholder date | Unrecognized; unsupported structure. |
| 3609 | Older outgoing, day-first date; two languages | Transliteration ignored; English unrecognized. |
| 5270 | Older transliteration card payment | Ignored unrelated. |
| 5271 | Older transliteration ATM withdrawal | Ignored unrelated. |
| 5313 | Older own-account transfer, no IBANs | Unrecognized; cannot construct confirmed own-account pair. |
| 5364 | Older income/conversion/cash deposit, five examples | Four missing; cash deposit parses amount but drops date and integer balance. |
| 6446 | FX with `Exchanged amount:`, two examples | Unrecognized; parser requires case-sensitive `Amount:` and `Date:`. |
| 5703 | English `Deposit:` card refund | Unrecognized; only `Incoming transfer` refund supported. |
| 11567 | Current deposit top-up | Parsed with amount, balance, US date. |
| 5823 | Transliteration one-time code | Safely ignored unrelated; should explicitly classify OTP. |
| 5391 | Older card payment, two languages | Transliteration ignored; English one-line unrecognized. |
| 9089, 8933 | Transliteration card payments without balance | Ignored unrelated. |
| 5841 | Transliteration utility bill | Ignored unrelated. |
| 5402 | ATM cash-out, two languages | Transliteration ignored; English unrecognized. |
| 6485 | Older English incoming with AM/PM | Parses money, drops supplied sender/date/available balance. |
| 11538 | Current incoming with `From sender:` | Parsed. |
| 11544 | Incoming card refund, no date | Parsed; delivery timestamp fallback appropriate for absent date. |
| 11460 | Current outgoing | Parsed. |
| 9201, 9082, 9115, 8925 | English card payments without balance | Unrecognized solely because merchant requires next physical line. |
| 11530 | Current utility bill, unresolved date placeholder | Parsed; absent usable date remains null. |
| 11822 | Own transfer with full From/To IBANs | Parsed and retains both IBANs. |
| 4982 | Older English utility bill | Unrecognized; unsupported opening. |

The upstream sender list contains `91000`, `credo bank`, and `credobank`.
Normalize case/spacing inside the bank boundary; these sender values are not
permission to route arbitrary financial-looking bodies from another bank through
Credo's parser.
Source: [senders](https://github.com/zenmoney/sms-formats/blob/beba5557781f3e8cbae7705665eb22ea3b83a8c5/src/Credo%20Bank-ge_15880/senders.txt).

## High-confidence structural extensions

The shapes below are authored placeholders, not source SMS samples. Brackets
mark optional segments. Preserve the existing multiline versions as well.

```text
Payment: <money> <currency> Card N ****<last4> <merchant>><location> [Balance: <money> <account-currency>] <dd/MM/yyyy HH:mm:ss> [Details: <url>]
Canceled operation Payment: <money> <currency> Card N ****<last4> <merchant>><location> <dd/MM/yyyy HH:mm:ss>
Credo Bank: Accrued interest on your <deposit-number> deposit, <date>, amount <money> <currency>; Available Balance: <money> <currency>.
Incoming transfer <M/d/yyyy h:mm:ss AM/PM>; Amount <money> <currency>; Sender: <name>; Available Balance <money> <currency>
Credo Bank: Incoming transfer <dd/MM/yyyy HH:mm>; Amount <money> <currency>; Available Balance <money> <currency>
Currency exchange: <M/d/yyyy h:mm:ss AM/PM> Exchanged amount: <sold-money> <sold-currency> Received amount: <received-money> <received-currency> Balance: <money> <balance-currency>
```

Merchant extraction should stop before the balance/date/footer, not depend on
newline count. Strip only an anchored recognized bank prefix, then dispatch.
Cancellation must remain a retraction of an earlier draft, not a new income
(WHFIN deliberately differs from the upstream regex's `income` column).
Older incoming messages need optional colons, `Sender:` as well as `From sender:`,
and a timestamp outside `Date:`. Presence of AM/PM selects month-first parsing;
older 24-hour examples use day-first. If a stated date is invalid, do not silently
replace it with delivery time.
Source: [format definitions](https://github.com/zenmoney/sms-formats/tree/beba5557781f3e8cbae7705665eb22ea3b83a8c5/src/Credo%20Bank-ge_15880/formats).

## Hazards and priorities

**P1 — fix recognized families without losing data.** Support one-line payments
and reversals, the anchored bank prefix, older incoming dates/labels, and the FX
labels above. Treat integers as valid monetary values and preserve signed
balances. Current `amountRegex` requires a decimal point and excludes minus:
searching inside a negative decimal can incorrectly return its positive value.
Validate grouping and reject overflow; operation amount and balance need
separate sign rules.

**P1 — preserve financial meaning.** Most older upstream templates explicitly
label balance `av_balance`; newer ones sometimes label it `balance`. Those labels
are community metadata, not a bank guarantee. A card refund example explicitly
says availability precedes account posting. Available money must not be assumed
to equal booked ledger balance. WHFIN's importer uses declared balances for
account-routing arithmetic and stores them as `balanceAfterMinor`; adding new
available-balance formats without identifying the balance kind risks incorrect
routing/reconciliation. Preserve the distinction or withhold booked-balance
claims. FX purchase currency may differ from account/balance currency; cash
withdrawal is movement to cash, not ordinary spending. Older transfers that omit
own-account IBANs cannot become a confirmed internal pair from wording alone.
Sources: [upstream columns/examples](https://github.com/zenmoney/sms-formats/tree/beba5557781f3e8cbae7705665eb22ea3b83a8c5/src/Credo%20Bank-ge_15880/formats),
local `SmsTransactionImporter.accountAtDeclaredBalance` and grouped insertion.

**P2 — add deliberate language and older-format coverage.** Transliteration
payments, refunds, cashback, P2P income, utility bills, and ATM forms currently
vanish as unrelated. Add explicit family matching and synthetic regression
fixtures; merchant-specific upstream regexes should become general structural
parsing, not hardcoded merchants. Older single-sided conversion notifications
must not fabricate the missing currency leg.

**P1 negative control — OTP and failed payments.** Explicitly recognize
`Ertjeradi kodi:` as a code message before any monetary matching; it includes an
amount and masked card. It is currently safely ignored, but broadening card
recognition could turn it into a false transaction. Keep existing English OTP,
rejected-payment and cancellation behavior covered separately. Never classify an
operation from amount/card tokens alone.
Source: [OTP format 5823 and cancellation format 6015](https://github.com/zenmoney/sms-formats/tree/beba5557781f3e8cbae7705665eb22ea3b83a8c5/src/Credo%20Bank-ge_15880/formats).

## Verification after parser expansion

Re-ran the temporary probes against the newly compiled debug classes after the
bank-message types moved to `BankSmsMessage` (Credo schema 3), then refreshed
after the cash-out/prefix fixes, stricter money labels and calendar parsing.
The final counts below were computed from the probe outputs. These are direct
parser checks, not end-to-end importer or live-phone checks. The tables above
remain the **pre-change baseline**.

| Corpus | Examples | Parsed | Cancellation | Ignored | Unrecognized |
| --- | ---: | ---: | ---: | ---: | ---: |
| Credo before | 40 | 8 | 0 | 15 | 17 |
| Credo final | 40 | 21 | 1 | 1 | 17 |
| TBC final | 46 | 37 | 0 | 1 | 8 |

Credo's ignored example is now explicitly OTP. Previously silent financial
transliteration forms now become unrecognized diagnostics. One-line payments,
reversal, supported transliteration payment variants, prefixed interest, older
incoming timestamps/sender, cash-deposit date/integer balance, card refund and
both FX examples now parse. Integer and negative balance parsing was broadened;
the old unsigned-decimal limitation described above is historical.

**Credo remaining gaps:** older outgoing 1590/3609; cashback 5624; P2P income
11543; transliteration refund 5772; legacy card operations 5543/5270/5271;
IBAN-less own transfer 5313; one-sided conversions and transliteration deposits
in 5364; old utility payments 5841/4982; both cash-out languages in 5402. Legacy incoming
available balances remain null, even though supplied by the message. Bare dates
on interest still deliberately use delivery-time fallback. These are partial
coverage results, not complete support for every Credo template.

**Semantic finding fixed during verification:** the intermediate implementation
rewrote transliteration cash-out 5402 as `CardPayment`. The final probe confirms
that this mapping was removed: it remains unrecognized rather than creating an
ordinary expense. A dedicated cash-withdrawal model is still needed.

TBC has **23 formats but 46 examples**, at the same pinned source commit:
[TBC format directory](https://github.com/zenmoney/sms-formats/tree/beba5557781f3e8cbae7705665eb22ea3b83a8c5/src/TBC%20Bank-ge_15622/formats).
Remaining unrecognized cases are three conversion
formats (5229/5982/6055), an own-account transfer without stable account
identifiers (5230), a payout confirmation lacking date (6490), and three
legacy card notifications without card digits (3621: Georgian, English and
transliteration). The sole ignored example is a foreign-country UZS deposit
notice (6491, intentionally excluded). The English `Card transaction:` opening
with masked digits (5319) now parses. Older
reversals 3360 parse amount/date but do not retain a merchant when no masked card
is present. These distinctions matter beyond the aggregate parsed count.

**Balance caveat remains:** TBC's new parser withholds SMS balances from ledger
anchors. Credo still returns some explicitly available balances (deposit top-up,
cash deposit, interest), and some labels remain semantically ambiguous. Parser
recognition does not prove a booked balance or justify cross-bank matching by
balance alone. Neither these community examples nor this probe verify actual
posting/availability behavior in the owner's accounts.


## WHFIN integration, 0.3.27

`BankSmsMessage` carries neutral parsed operations. `BankSmsBank` selects Credo or
TBC from an allowlisted SMS sender; multipart receiver messages must agree on one
recognized bank. Historical scans retain sender identity. Unknown senders cannot
claim bank provenance by imitating a message body. OTP and promotional messages
are excluded from the runtime transaction scan.

Existing Credo `sms|<hash>` keys are unchanged. TBC uses `sms|tbc|<hash>`, so no
Room migration is necessary; the diagnostic key retains the bank through routing,
backup and later statement matching. Card suffix lookup, account candidates,
statement evidence, cancellation targets and queued card resolution are bank
scoped. The UI filters candidate accounts and creates an unresolved TBC account
in the TBC group. Legacy unbound account handling remains compatible with Credo.

TBC purchases with card digits, refunds, deposits, money transfers and utility
payments use the common import and review pipeline. Available-balance numbers
are deliberately not persisted as ledger anchors; their currency can still help
route FX. Informational/underspecified conversions, IBAN-less own transfers and
other unresolved structures remain diagnostics, not invented expenses or income.
Ordinary positive TBC statement rows can reconcile a unique same-account,
same-day, same-amount incoming SMS. Purchases retain merchant/date matching.

Synthetic tests cover same card suffix at two banks, rejection of cross-bank
manual routing, queue separation, sender spoof controls, and SMS/statement arrival
in both orders. No upstream examples are copied into committed fixtures.
NotificationListenerService and push delivery are not part of this change: the
text parser can later be reused after real push payloads and notification access
are verified.
