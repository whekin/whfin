# The Home screen

Status: implemented 2026-08-25. No schema change.

Home answers one question: **what should I do about money today.** Everything on it either changes a
decision today or is on its way out. Net worth, month history, category shares and forecasts live one
tap away, in Accounts and Statistics.

## Reading order

| Block | Says | Silent when |
|---|---|---|
| Headline | Money that can be spent, in the display currency | never (a dash while rates load) |
| This month | Result of the running month, income and expenses under it, and what is not in the total yet | never; the last line only when a row's day has no quote |
| Runway | How long the spendable money lasts, and whether that reaches payday | comfortable, or no honest daily rate yet |
| Still due | Monthly obligations this month has not seen yet | nothing recurring is outstanding |
| Yours to return | Borrowed money the balances still count as the person's own | nothing is owed to anybody |
| Notices | At most two standing conditions, the rest behind one fold row | nothing is wrong |
| Needs attention | Drafts and unrouted messages waiting on a decision | the queue is empty |
| Outlook | Up to two insights: pace and its largest driver | early month, or normal variation |
| Today / Recent | Today's own spending and the last settled rows | no history at all |

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

`daysLeft = spendable ÷ ordinary spend of one day`.

The daily rate is `pace.projectedExpenseMinor ÷ daysTotal` — the same projection Statistics shows,
which already treats a purchase far above the person's typical transaction as realised once instead of
repeating it every remaining day. Consequences of reusing it rather than inventing a second rate:

- a month younger than five days has no rate at all, and the row says nothing;
- a large purchase already made shortens the runway, which is the safe direction to be wrong in;
- the number cannot disagree with the forecast on the Statistics screen.

The row appears only while it is news: at most 45 days of runway, or whenever the money runs out
before a declared payday window closes. A short runway carries the consequence in words as well as
colour (`Only N days left`), because an accent alone is not readable by everyone.

Paydays come from declared `income_sources`. A window that has not closed yet is still the answer — a
payment inside its own window is not late. A source whose era has ended promises nothing.

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
- amounts are compared in lari at each row's own booked value, so an unpriced day is skipped rather
  than guessed.

A charge already recorded this month is finished business. One whose usual day has passed with nothing
recorded still counts: either it is late or the statement has not arrived, and both mean the money
should still be treated as owed.

The sum stays **out** of the pace insight. A projection the person can also read in Statistics must
mean the same thing on both screens; folding an obligation into a rate would quietly change a number
that has its own screen. Naming the payees is what makes the sum checkable.

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

The low-card notice carries its own action. The answer to an empty card is always to move money onto
it, so the row opens the composer as a transfer with both sides already chosen: the same bank first,
because an internal top-up is instant; then anything not locked by a term; then money set aside as
reserve; then whatever holds the most. An empty ledger is never proposed. A prefilled form the person
closes without typing has nothing to discard.

## Where the work happens

Month totals, the attention queue, the recent rows and recurrence are computed in `FeedViewModel` on
`Dispatchers.Default`, not in the screen body. They used to be derived during composition, so every
unrelated state change — a currency rotation, a sheet opening — walked the feed window again while
laying out the first screenful.

Pure logic and its tests:

- `ui/feed/HomeBoard.kt` — notice triage, month flow, attention queue, recent rows
- `ui/feed/HomeRunway.kt` — runway and the next declared payday
- `ui/feed/CardTopUp.kt` — which ledger funds a top-up
- `data/rates/SpendableSource.kt` — spendable money in the display currency
- `data/recurring/RecurringCharges.kt` — monthly obligations from history
