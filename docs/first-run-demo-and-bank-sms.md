# First run, Demo workspace, and Bank SMS

Status: SMS model, Bank SMS, Welcome choice, Personal setup, and Demo workspace implemented,
2026-07-29. The remaining physical-device step is the bounded OnePlus SMS dry-run. Canonical terms live in
[`CONTEXT.md`](../CONTEXT.md).

## Product intent

The first experience should let a person understand a populated WHFIN before committing data, then lead
them into the bank-specific setup that provides the most automation. Demo is a temporary isolated
workspace, not a prominent global preference. SMS setup should begin working after explicit consent even
when account routing is still incomplete.

## First run

A fresh untouched installation shows one full-screen **Welcome choice** before the application shell:

- primary: `Set up my finances`;
- secondary: `Explore demo`;
- no carousel and no permission prompts;
- system Back closes the application.

Either choice permanently completes Welcome choice for that installation. Choosing Personal setup and
then adding nothing must not make the gate reappear. Data-bearing existing installations must never see a
new first-run gate after upgrade. Runtime choice flags are deliberately excluded from backup.

The signal for "already welcomed" is the presence of a ledger, read before the database is opened.
It must not be an install timestamp: `lastUpdateTime > firstInstallTime` is true for every sideloaded
build after the first and survives a data wipe, so the gate became permanently unreachable on any
device that had once been updated. An installation whose database file already exists is adopted
silently; an empty one — however many times its package was updated — still shows Welcome choice.

Choosing Personal setup records a resumable pending setup surface. Process restarts return there until the
person deliberately continues to Feed or Accounts. Choosing Demo completes Welcome only after the isolated
fixture has been installed successfully.

## Personal setup

Personal setup is a resumable eight-stage wizard. Each stage offers its working forms, keeps
changes immediately, and allows continuing without configuring optional features. Only the final
`Start` action completes setup. Closing a form returns to its caller inside setup; connecting one bank
never prevents adding another.

1. **Banks**: Credo and TBC sign-in, lock code for remembered credentials, statement files and backup
   restore. Bank sign-in does not require SMS monitoring consent. The initial sync still reads all
   available history; a current-balances-only import is not implemented.
2. **Bank SMS**: one opt-in enables Credo and TBC SMS together; individual bank overrides remain in
   Connections. Optional TBC push and an automatic card-link check after
   bank history finishes, and a review of unresolved messages. With existing SMS permission, entering
   Bank SMS previews the past 90 days; importing those operations still requires confirmation.
3. **Accounts**: imported bank ledgers, cash and watch-only crypto wallets, names, currencies, available
   money versus reserve, opening balances and card mappings. Multiple accounts can be added.
4. **Categories**: evidence-based suggestions, editable categories and remembered counterparty rules.
5. **Income**: any number of regular income sources with currency, receiving account and expected payday.
   Wallet history setup stays outside this onboarding step.
6. **Savings and debts**: savings pace and goals for reserves, existing debts including declarations
   without a new money movement.
7. **Preferences**: appearance, quick entry and widget choices, PIN/biometrics and portable backup.
8. **Ready**: per-currency balance review and a collapsed return to any setup stage; no invented completion marks
   for banks or permissions. Skipped features remain accessible in the normal application.

The final review counts a dated bank closing balance that exactly matches the ledger as verified
without an owner tap. A manually reviewed mismatch remains a known difference, not a repaired
balance. The untouched zero-balance Cash account seeded by the app has no balance to review;
once cash transactions exist, it appears. Starting waits for the first complete local snapshot,
and the action names any remaining review, bank attention, or missing account setup.

After a fresh Credo or TBC sign-in has started its data read, setup automatically moves to Bank SMS.
The process-owned read continues while the owner configures other stages. Bank rows share a progress
vocabulary, including account progress, balance review, interruption, and attention. A previous
statement import proves that transactions exist, but does not prove that the current full history
read completed. The other bank stays accessible on Bank SMS, and later stages surface bank work
that still needs action. Category suggestions are based on unfiled transactions; optional interest
packs can be selected and added in one action. While a bank read is unfinished, the category step
states that more suggestions may arrive.

The current stage is stored with installation-local runtime flags and excluded from backup. Activity
recreation also restores the nested caller stack; a fresh process can resume at the saved stage without
persisting credentials, codes or a bank session. Re-entering setup from Home starts at Banks.

Back from the first stage exits (or returns to Home when setup was resumed there). Other stages return
one step. Secondary forms have their own caller stack, including Credo → PIN → Credo and TBC → statement
→ TBC. Appearance and bank settings return to the setup caller at their entry page.

## Demo workspace

In the Personal workspace, Settings shows a compact `Explore demo` row near About instead of a large
notice or switch. Tapping it opens a short explanation that synthetic and personal data are isolated,
then an explicit `Open demo` action.

While Demo workspace is active:

- every application screen shows a compact, non-dismissible workspace strip with `Use my data`;
- the strip remains above primary and secondary destinations as well as full-screen working dialogs;
- a fresh user exits to Personal setup;
- an established user exits directly to their Personal Feed;
- an unsaved Demo form closes without a discard confirmation;
- `Reset demo now` exists only in Demo Settings.

A **Demo visit** survives process restarts. Explicitly returning to Personal ends the visit; the next
entry starts from the canonical fixture rather than retaining old synthetic edits.

The implemented entry lives beside About rather than at the top of Settings. The explanation sheet keeps
its action pinned while its copy scrolls at large font scales. Demo Settings alone exposes the destructive
reset row. The shared workspace frame owns the status-bar inset and is reused by composer, category filter,
debt ledger, and account-details dialogs so `Use my data` remains available even with an unsaved form.
Verified at EN/light/font 1.0 and RU/dark/font 1.5 on a disposable emulator, including first-run and
established exit destinations, dirty-composer exit without confirmation, and canonical fixture restore
after a saved synthetic edit.

## SMS ingestion model

SMS monitoring, routing, and import are separate:

1. The user explicitly enables monitoring and grants `RECEIVE_SMS`.
2. Every future supported-bank candidate receives a local structured outcome even when no card mapping
   exists. Raw body remains memory-only.
3. A parsed message without enough account information becomes an **Unrouted operation**.
4. Resolving every missing ledger confirms the operation the user explicitly reviewed, or attaches the
   SMS evidence to an already confirmed statement transaction. A remembered card route also imports
   compatible queued messages automatically; those unreviewed rows remain pending.

The current setup gate that refuses to enable monitoring before the first card mapping is removed.
Nothing is guessed: incomplete routing delays ledger mutation, not monitoring.

### Feed projection

A parsed Unrouted operation appears at its actual date among Feed rows:

- merchant/counterparty, amount, currency, and an explicit `Choose account`/`Choose accounts` status;
- muted treatment distinct from a routed pending transaction;
- excluded from balance, day/month totals, category distribution, and statistics;
- tap opens its Routing resolver.

Unrecognized formats, OTP, rejected messages, and technical errors do not masquerade as financial rows.
They remain in Bank SMS.

Transfers and conversions use the existing grouped-operation grammar. Before routing they appear as one
provisional grouped row; the resolver selects every missing `from`/`to` ledger, then creates the normal
`TransferGroup` and its legs atomically. If a compatible ledger is missing, account creation or Bank
setup returns to the same resolver rather than abandoning context in Accounts.

## Bank SMS surface

The user-facing destination is `Bank SMS`, not `SMS diagnostics`.

Its hierarchy is:

1. monitoring status and one dominant next action;
2. `Needs attention` for unresolved card/account routing;
3. `Cards and accounts`, before the journal so routes stay findable;
4. messages waiting for bank evidence, without a false owner decision;
5. recent processing activity, three rows by default with an explicit expansion;
6. optional `Check recent SMS`;
7. parser details and safe failure sharing inside an individual result or troubleshooting area.

## Implementation order

1. Separate SMS monitoring/routing/import and persist Unrouted operations without mutating the ledger.
2. Project them into Feed and add the contextual Routing resolver, including grouped transfers and
   statement-first reconciliation.
3. Rebuild SMS diagnostics as Bank SMS.
4. Add Welcome choice and bank-centred Personal setup on top of the finished Credo/SMS flow. **Done.**
5. Replace the Settings demo switch with the new entry, workspace strip, exit destinations, and Demo
   visit reset policy. **Done.**
6. Run the bounded OnePlus SMS dry-run manually before any real-message import.

Every UI slice requires light/dark, RU/EN, font scale 1.5, compact-height, disposable-emulator behavior,
and data-preserving `install -r` only on the physical OnePlus.


## Compact setup and balance review — 0.3.67

Stage rows report saved facts (account/income/plan counts, imported history, configured PIN). A bank
ledger alone is not called a successful connection. Routine explanations are one sentence; the final
review omits the introductory paragraph and keeps the step list collapsed.

The final review reads related data in one Room transaction, with explicit loading and failure states.
Each currency ledger has its current total and dated bank evidence. The comparison uses the latest
imported closing balance at that day's cutoff, excludes bank holds, and never infers discrepancies
from ordering within a bank day. A user-created opening is not independent bank evidence. Crypto
uses the last observed on-chain balance and observation time, not the sum of imported transfers.

“Checked by me” is an explicit owner action in the expanded account row. A fingerprint of financial
rows and evidence invalidates that mark when amounts or provenance change; categorising the same
payment does not. Marks live in runtime flags outside backups and are cleared on restore. Unchecked
balances can be skipped explicitly; this never certifies a connection or silently changes money.

## First personal-setup feedback — 2026-09-23

The full initial Credo history walk continues in the application while the owner advances through
setup. The connected TBC page offers an explicit Done action. Bank sign-in and SMS monitoring
are separate decisions on consecutive setup steps; the SMS step now turns on both supported banks
with one action and keeps a door to the older-SMS check.
The account step presents cash amount entry directly and names the imported-account facts to verify:
card type, primary card, bank product, and Available/Reserve fund role. Cash remains optional.
The SMS dry-run still summarizes counts; [the feedback report](onboarding-feedback-2026-09-23.md)
records the per-message preview and parser examples that remain to be resolved.
