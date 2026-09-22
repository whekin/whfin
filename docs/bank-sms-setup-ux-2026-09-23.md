# Bank SMS and setup UX — 2026-09-23

## Changed

- A completed downward pull in Settings now opens the search field, focuses it and shows the IME.
  Tapping the search icon retains the same behavior; reversing an unfinished pull still closes it.
- Bank SMS places Cards and accounts immediately after decisions that need attention. Recent activity
  shows three results initially and expands to the existing 20-result limit on request. Routine status
  and waiting rows use shorter copy. A statement-covered bill without a card no longer offers an
  account choice that cannot change the result.
- Personal setup has an optional Bank SMS stage between bank connections and accounts. It shows
  independent Credo/TBC SMS states, TBC push, and a route to review messages and cards. With existing
  `READ_SMS` permission, the stage checks old messages for evidence that links cards to bank accounts.
  It waits for active bank history work to finish and checks again after returning from a secondary
  page. Opening Bank SMS during this stage starts the 90-day dry-run; importing old operations still
  requires the owner's explicit confirmation. No permission prompt or SMS history import happens
  merely because the owner advances to the stage.
- Home's cash-horizon card no longer calls itself “Waiting for the payment” after the estimated payday.
  It shows near-term days left, opens Accounts directly when there is no detail to expand, and stays
  absent when it has no useful near-term reading. The calculation-method paragraph was removed.

## Verification

The settings swipe test first failed on the absent focus and then passed with the real IME. The full
host suite passed: 1214 tests, 0 failures/errors, 5 skipped. Disposable emulator routes passed
in EN light and RU dark/font 1.5, including a compact-height RU SMS step. Synthetic Bank SMS used
20 recent events; before the change a full swipe still showed only the journal, afterwards Cards and
accounts appeared before it. Release R8 and lintVital passed. Screenshots and layout were visually inspected.
No instrumentation ran on the physical Samsung.

## Samsung installation — 2026-09-23

At the owner's request, the signed 0.3.69 (81) release replaced the existing package with `install -r`.
The phone already reported 0.3.69 (81), but its prior APK hash differed. Both APKs used the same release
certificate (`af6009…fae92`). After installation, the installed `base.apk` SHA-256 matched the built
release (`6b6d7e52…14cf6`). The original install time (2026-08-16) and granted READ_SMS, RECEIVE_SMS,
and POST_NOTIFICATIONS permissions remained. No AndroidRuntime crash appeared. WHFIN logged the existing
`duplicate_statement_row` finding for the previously identified 1 GEL Silknet duplicate; this update
does not alter that saved row. WHFIN was not manually opened over the notification shade, no device
instrumentation or data clearing was performed, and the 23 September backup remained in Downloads.

## Remaining boundary

The historical SMS dry-run still previews aggregate counts, not each proposed operation. The new
automatic card-link trigger has not been exercised with a live bank session and the owner's inbox;
its matching path is covered by existing SMS evidence tests. Device-local diagnostics already saved
under old parser outcomes remain untouched.
