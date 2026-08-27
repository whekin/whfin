# Savings plan and pace

Savings opens from the Reserve amount in Accounts. It reads accounts currently marked `RESERVE`,
independently from their bank product, and keeps currencies separate. Watch-only crypto is excluded.

## Two readings

- **Balance** includes all active ledger rows on the selected currency's Reserve accounts: opening
  anchors, interest, income, spending, transfers, and adjustments. The chart starts at the first
  recorded evidence, never an invented year of zeroes. The current month is a balance so far.
- **Pace** counts grouped Available→Reserve transfers positively, Reserve→Available negatively, and
  direct Reserve spending negatively. Reserve→Reserve movements are zero, including conversions.
  Interest, external income, adjustments, voided rows, and transfers whose other side cannot be
  established do not satisfy the plan. Group classification sees all fiat currencies, while amounts
  are read only from the selected currency's Reserve legs.

The average uses up to three preceding completed calendar months, including recorded zero months;
the current partial month is shown separately. Consistency evaluates only completed months covered
by a plan, at most the latest twelve, against each month's own target.

## Declaration, not ledger

Room v2 adds `savings_plans` through the tested data-preserving `MIGRATION_1_2`. A plan contains its
currency, positive monthly amount, optional desired balance/date, and inclusive effective month range.
An edit in a later month closes the preceding version; an edit within the current month replaces that
month's declaration. Pausing removes the current month's declaration but retains completed periods.
No operation is created, no money is moved, and reaching a goal has no automatic side effect.

Portable v2 backups include every plan version. A v1 backup may omit the new table; a v2 backup may
not silently omit it. Integrity checks report non-positive amounts, invalid month boundaries, and
overlapping plan periods.

## Presentation and limits

The screen shows the current Reserve balance, optional goal/remaining amount, monthly plan progress,
average, and consistency. Pace has signed monthly columns and a clearly labelled **current** target
reference; historical comparisons retain their own target. Balance has a solid line and optional goal
reference. Year means the latest twelve recorded months; All time includes the complete recorded
series. Bar selection and previous/next controls expose exact amounts; the all-time balance line fits
the viewport rather than requiring a horizontal scan of years.

Fund-role history is not recorded by the account model. These are the histories of **today's Reserve
accounts**, not a claim about which role the owner gave each account years ago. The UI says this
explicitly. Multiple named envelopes, allocations within one bank balance, historical cross-currency
valuation, scheduled transfers, and reminders are outside this slice.
The bank remains the place to move money. Savings does not need a separate “top up reserve” action;
WHFIN observes the resulting transfer through its existing bank-data channels.

## Verification

- Pure kernel tests: transfers, same-role movements, direct spending, adjustments, voids, malformed
  peers, month gaps, year boundaries, time zones, and rolling averages.
- Screen-model and Compose tests: per-month plan versions, native currencies, no invented history,
  editor entry, chart semantics, and shell navigation.
- Disposable Pixel instrumentation: v1→v2 migration, plan revision/pause, current/legacy backup rules,
  deterministic export/restore, and demo isolation.
- Shared chart references cover light, dark, signed/missing observations, and font scale 1.5.
- Device QA: disposable Pixel 9 Pro API 36.1, EN light/dark at font 1.0, RU dark at font 1.5,
  plus a 426×640dp override. Creating a plan/goal/date, real numeric/text IME, chart modes/ranges,
  and scroll access to exact monthly values were exercised. No physical phone was installed or modified.
