# The Home screen

Status: implemented 2026-08-25. No schema change.

Home answers one question: **what should I do about money today.** Everything on it either changes a
decision today or is on its way out. Net worth, month history, category shares and forecasts live one
tap away, in Accounts and Statistics.

## Reading order

| Block | Says | Silent when |
|---|---|---|
| Headline | Money that can be spent, in the display currency | never (a dash while rates load) |
| Runway | Whether spendable money covers ordinary spending and expected bills until payday | no reliable rate yet; without payday, more than 45 days |
| Expected from your history | Monthly payments this month has not seen yet, read off repetition | nothing recurring is outstanding, **or the runway above already named them** |
| You owe | Borrowed money the balances still count as the person's own | nothing is owed to anybody |
| Needs a decision | Standing conditions first, then bank messages that truly need an account choice | nothing needs routing and no condition needs attention |
| Today / Recent | Today's own spending and the last settled rows | no history at all |
| This month | Spent, received, and — when there is one — the projection to month end with its comparison | never; extra lines only when they exist |

The order is the answer to "how am I doing for money", built in the order a person builds it: what
there is, how far it goes, what is already owed out of it, what still needs them, what just happened,
and only then the month.

A confirmed bank posting without a category is already part of the ledger. It remains visible in
History and can be categorized later through Smart categories; import does not ask for a category
decision. Only unresolved account routing blocks an SMS from becoming a ledger operation.

The month used to open the screen with its net result as the hero. Before payday that number is
negative for structural reasons — the salary has not landed and the rent has — so the largest figure
on the first screenful announced as alarming a month that was going normally. It now closes Home with
the two facts that are readable mid-month, spent and received, and the derived net is left to
Statistics, where a whole month is read.

The projection moved into that block from a section of its own: it was a reading about this month
standing apart from this month. It names the day it projects to and the full month it is compared
against, because a part-month total and a whole-month total are not the same measurement.

Expected payments are named once. The runway block lists the same bills with their dates inside it,
so the standalone row speaks only when no forecast has spoken for them. Its title says the evidence:
these are payments read off a history of repetition, not obligations anybody confirmed.

## Two headlines, two questions

Home leads with **spendable money**; Accounts leads with **net worth**. The two pages of the pager
used to print the same number, which answered neither question: in this app everyday money is
deliberately small, because the rest sits in deposits, so "what is owned" never changes a decision on
a Tuesday.

Spendable is the person's own classification, not a guess: a ledger counts when its `FundRole` is
`AVAILABLE`. Two facts are applied regardless, because they are not opinions:

- a watch-only chain ledger is read, never spent;
- a term deposit can only be spent by breaking its term, which is a decision and not a purchase.

Both headlines rotate the display currency on tap and both name what could not be converted, so
neither can claim to be the whole picture.

## Runway

Runway is a forward cash reading, not the current month's expense average. Statistics still projects
the whole calendar month and therefore keeps a phone, trip or other large purchase as realised once.
`CashRunway.cashForecast` derives its own forward rate from the same normalized own-expense amounts
as Statistics. It first excludes proven recurring payees, then uses the shared large-purchase rule
(over max(5 × median transaction, 500 GEL)) on the remaining current-month expenses. Those remaining
ordinary expenses are divided by elapsed calendar days. A realised large purchase already reduced
today's spendable balance and is never spread back into future days.

Proven monthly obligations are scheduled separately on their usual dates. The same strict recurrence
evidence as `Still due` is used, but the horizon crosses a calendar boundary through the end of the
next declared payday. A payment within the detector's 40% variation band settles that occurrence;
a smaller partial payment leaves the typical amount minus what was paid. An unpaid
one remains due after its expected day. Home walks those dates and ordinary days in between to find the
first day the balance cannot cover. This preserves both sides of the distinction:

- several one-off purchases stay realised facts rather than a new daily habit;
- rent, subscriptions and stable bills remain in the future cash requirement when they fall before payday.

Consequences of keeping the monthly projection and the forward cash reading separate:

- a month younger than five days, fewer than five non-recurring expense observations, or an unvalued
  current expense (including an FX SMS awaiting settlement) cannot establish a reliable rate;
- a large purchase already made affects Home once, through the lower balance;
- Statistics can still state the honest projected total for the whole month;
- a short runway names the expected gap and the first recurring payment behind it.

Payday is one estimate, not a window. `expectedDayFrom` is the day money usually lands;
`weekendRule` says what the payer does when that day is not a working one — `EARLIER` (paid before
the weekend), `LATER` (paid after it) or `ANY_DAY`. There is no declared outer bound: an app holding
a "latest by" date starts calling a real payment late on a day the owner never agreed to. Public
holidays are not inferred — they need a per-country calendar the app does not have — and an earlier
arrival remains upside rather than a promise. A day the month does not have is clamped to the last
day it does; a weekend shift may cross into the previous or the following month, and that date is
kept as it falls.

The main row answers `Should last until D` or `May be X short by D`. Once the estimate has passed
with the money still outstanding, the Home cash card names a near days-left reading if there is one;
otherwise it stays hidden. The income source continues to say it is waiting. There are no negative
days, no accusation, and no jump to next month, because the next payday is not known until this one
lands. Nothing is forecast past a date already behind us, and the timeline draws nothing — a passed
estimate cannot anchor a rule. Bills keep arriving during that wait, so their horizon falls back to
the same 45-day quiet window used when no payday is declared at all.

Without a payday the row reports at most 45 days, using bills scheduled over that same horizon. It
never extrapolates a reassuring number of days beyond a payday for which later bills have not been
scheduled. Zero and negative available balances remain warnings, not missing input. Bills due on the
payday itself are included conservatively. No incoming salary is added to the current balance.

The row expands to show every expected bill, the total needed until payday, any remaining balance and
the limits of the estimate. New one-off purchases cannot be predicted. Monthly recurrence and the
large-purchase threshold are heuristics, not a user-confirmed schedule or a guarantee. All inputs must
have answered before a forecast appears; the ViewModel publishes the forecast and `Still due` together,
off the UI thread, and re-evaluates when the calendar date changes.

Payday timing comes from declared `income_sources`. A month whose declared amount has been received in
full is skipped and the next covered month becomes the answer. A source that starts after its usual
payday does not invent a special first payment, and a source whose era ended promises nothing.

Only money the owner confirmed as that source's pay can settle a month — a link in
`income_source_payments`, or a credit from a counterparty they confirmed earlier. Candidates are
chosen by the receiving account, not by the declared currency: an agreement in dollars can be paid in
USDT, and filtering on the declaration would offer nothing at all. Where the two currencies differ no
remainder is shown or computed — this iteration invents no historical rate — and a confirmed payment
settles the month rather than waiting on a comparison that cannot be made. An unanswered
credit, even one for exactly the declared amount, leaves the payday where it is: a refund and a
salary are the same shape in a ledger, and moving the forecast a month forward on a guess tells
somebody they have been paid when they have not. Transfers, balance adjustments, debt allocations and
system-category rows are never even candidates.

## Still due

Monthly obligations are detected from history, never declared. Rent dominates a month and either
already happened, making the month look expensive, or has not happened yet, making the remaining days
look affordable; neither reading is true on its own.

Detection is deliberately strict, because a wrong recurrence would promise money that is not owed:

- identity is the merchant or the receiving account, never the spelling the bank printed;
- the payee must appear in at least three of the last four complete months;
- at most two payments in any of those months — a shop visited weekly is a habit, not a bill;
- every monthly total must sit within 40% of its own median, which is what separates rent and a
  subscription from a popular merchant;
- the payment must land on its day: each month's first payment within about four days of their
  median, counting the turn of the month as one step. A bill has a date — rent on the third, a
  subscription on the day it was taken out — while a shop is visited whenever there is a reason to,
  and three purchases a month apart for similar sums are a coincidence rather than an obligation;
- amounts are compared in lari at each row's own booked value, so an unpriced day is skipped rather
  than guessed.

A charge sufficiently paid this month is finished business. One whose usual day has passed with nothing
recorded still counts: either it is late or the statement has not arrived, and both mean the money
should still be treated as owed.

The sum stays **out** of the Statistics pace and out of Home's ordinary daily rate. It does participate
in Home's forward cash runway as a dated event: folding an obligation into a rate would quietly repeat
rent every day, while ignoring it would promise money that is already spoken for. Naming the payees is
what makes the forecast checkable.

## Money that is not the person's own

Two lines exist because a balance can be true and still mislead.

**Yours to return** names what is still owed on open `I_OWE_THEM` debt cases, one row per currency,
because a debt in dollars and a debt in lari are two promises and adding them would need a rate to say
something that needs none. Money owed *to* the person needs no line: it already left their accounts.
The claim is named rather than quietly subtracted from the headline — a borrowed sum may already be
spent, and a headline that moved without saying why is worse than one that needs a second line to be
read correctly.

**Not in the total yet** names foreign spending whose own day has no quote. Those rows are excluded
from the month result rather than counted as zero, which is the rule everywhere in this app; without
the line, the month total would silently be smaller than the month.

## Today's number

The `Today` heading carries the day's own spending, counted over the whole day rather than the five
rows shown, so the total can never be the sum of a truncated list. It reads against the ordinary daily
rate the runway row already states, which is why the row does not repeat that rate. A day that only
earned has nothing to report.

## Waiting, and nothing recorded

Two different states used to look the same, and both looked like facts.

While the ledger is still answering, Home shows a **skeleton** in its own rhythm — the month block and
the first rows without numbers — instead of printing the `0.00` a `StateFlow` starts on. The readiness
signal comes from the query pipeline that produces the month totals (`monthFlow`, null until the first
real answer), not from shared feed state: a combined `StateFlow` hands its placeholder downstream, so a
total built on it exists as zero before any query has returned. Transaction history and Accounts use
the same silhouettes.

A restore is the other half of the rule. `LedgerRestoreState.active` is raised inside
`WhfinBackupManager.restore`, because a restore empties every table before writing the new rows while
the screens are alive: a demo workspace being installed, a backup being brought back. During it Room
truthfully answers "nothing", and without the flag Home would state a month result of zero over the
person's own data mid-restore.

"Your financial picture will appear here" is therefore a claim with three conditions: the ledger has
answered, no restore is running, and no money-bearing block above it — obligations, debts, unrouted
messages — already names something. A screen cannot both list money and offer to help the person get
started.

## Notice triage

Standing conditions are ranked, not stacked:

1. `CARD_BALANCE` — money that cannot be spent right now
2. `SETUP` — the app is not finished being set up
3. `INTEGRITY` — the ledger contradicts itself
4. `CREDO_SYNC` — the data is merely old
5. `SMS_ONBOARDING` — an offer

Two render fully; the rest collapse into one quiet row that says how many are hidden. A single
overflowing notice is shown instead of folded: hiding one block behind a row that says one block is
hidden costs the same glance and adds a tap.

The low-card notice leads with the bank/card identity and balance on the same row, with a short risk
label underneath. The row opens Accounts regardless of notification permission. A separate action
opens the installed official app for that card ledger's bank group; another explicitly requests
low-balance alerts when needed. Neither action writes a transfer or prefills a bank operation.
Launch failure is shown inline. Demo suppresses both real bank launch and permission actions.
Home and Savings share the same provider catalog and plain Android launcher implementation.

## Where the work happens

Month totals, the attention queue, the recent rows and recurrence are computed in `FeedViewModel` on
`Dispatchers.Default`, not in the screen body. They used to be derived during composition, so every
unrelated state change — a currency rotation, a sheet opening — walked the feed window again while
laying out the first screenful.

Pure logic and its tests:

- `ui/feed/HomeBoard.kt` — notice triage, month flow, attention queue, recent rows
- `ui/feed/CashRunway.kt` — normalized own spending, recurring separation and complete Home cash reading
- `ui/feed/HomeRunway.kt` — dated cash consumption and the next declared payday
- `data/rates/SpendableSource.kt` — spendable money in the display currency
- `data/recurring/RecurringCharges.kt` — monthly obligations from history
