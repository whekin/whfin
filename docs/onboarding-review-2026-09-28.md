# Follow-up onboarding review — 2026-09-28

Reviewed the eight-stage Personal setup against `SPEC.md`, the current Compose routes, and
synthetic EN/light and RU/dark/font 1.5 emulator journeys. The review did not sign into a real
bank, import SMS, reset the owner's S25, or change its ledger data.

## Fixed in this pass

- A ledger whose dated closing balance exactly matches the bank counts as verified without an
  owner tap. A difference can be marked reviewed, but remains explicitly named on the final
  Start action. The final action waits for the first complete balance snapshot; a read failure
  or omitted account setup gets an honest label.
- The app's untouched, zero-balance seeded Cash account is excluded from balance review. Cash
  with actual transactions remains in the review. The empty state no longer asks the owner to
  confirm a placeholder zero.
- The Income step no longer advertises Tron wallet setup. Wallet history remains available in
  the regular Income screen, pending a dedicated placement in Accounts/Wallets.
- The Categories step distinguishes new category suggestions, editing the category list, and
  categorizing past expenses. It says when bank history is incomplete and offers no forced
  categorization during import. The Savings empty-state action now says that it opens Accounts.

## Next product work

1. **Resume deferred work after bank history finishes.** Someone can pass Categories while
   Credo/TBC is still loading, then never see later proposals. Persist a per-bank completion
   result and surface a single, dismissible follow-up in Home and setup Ready. Offer a review;
   never create categories or import old SMS without the owner's choice.
2. **Make bank progress durable.** `BankSyncRuntime` persists only in-flight bank names. After a
   process restart, setup falls back to the existence of any imported statement, which cannot
   prove a complete history read. Store per-account coverage and a finished/attention outcome
   so Credo and TBC keep accurate status after restart.
3. **Unify the owner-facing bank review.** Credo and TBC now share status language, but their
   account confirmation screens still use different layouts and action order. Present imported
   accounts, proven balances, missing evidence, and one next action in the same structure.
   TBC booked balances must remain explicit where the bank's value is ambiguous.
4. **Give Savings a direct reserve route.** Its empty-state action opens the general Accounts
   list, then requires another edit to mark a fund Reserve. A dedicated account choice with
   the role explained would remove that detour.
5. **Revisit wizard length as a product decision.** All eight stages are optional, but Income,
   Plans, and Preferences can feel like a tour before first use. The existing forms could stay
   available through a short setup checklist after entry; this would change the current
   full-setup contract and should be decided deliberately.

Verification: 1254 host tests, 0 failures/errors, 5 skipped; EN/light and RU/dark/font 1.5
setup journeys, plus EN/RU balance review scenarios on the Pixel 9 Pro emulator passed.
Screenshots of empty, matched, and reviewed-difference states were inspected. Release R8,
`lintVitalRelease`, `git diff --check`, and public-tree privacy checks passed.

The signed 0.3.76 (88) APK was installed on S25 with `install -r` over 0.3.75. Its certificate
matched the prior install, and the installed base.apk SHA-256 matched the build
(`2bb6ca17…86b83fc`). The pre-existing backup remained in Downloads with its previously
verified hash. `firstInstallTime` and the READ_SMS, RECEIVE_SMS, and POST_NOTIFICATIONS grants
were preserved. BankSyncService was inactive before and after install. The app was not opened
after installation; no personal data was cleared or imported.
