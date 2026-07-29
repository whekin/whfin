# First run, Demo workspace, and Bank SMS

Status: accepted product direction, 2026-07-29. Canonical terms live in
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
new first-run gate after upgrade; the exact treatment of a legacy but untouched empty installation can be
decided during implementation.

## Personal setup

Personal setup is a compact first-action surface, not another onboarding sequence. It is bank-centred:
the user chooses a supported bank and then sees only channels that actually work for that bank.
Unsupported TBC/BOG channels are not advertised as coming soon.

For the current Credo dogfood build, the recommended guided-but-skippable sequence is:

1. Connect MyCredo, complete OTP, and perform the initial read-only account/history sync.
2. Enable future Credo SMS monitoring with a clear disclosure and `RECEIVE_SMS`.
3. Optionally find cards immediately through a bounded recent-message dry-run and separate `READ_SMS`;
   otherwise routing can finish when the next transaction message arrives.
4. Show a summary of connected ledgers, SMS status, and any card/account decisions still waiting.

Completed steps become status rows and can be continued later from Credo Bank setup. Manual account
creation, restore from backup, and skipping into the empty Personal workspace remain available.
Statement-file import is a secondary fallback, not the recommended first action.

Future TBC and BOG support should use the same Bank setup grammar while exposing only the channels each
bank truly supports.

## Demo workspace

In the Personal workspace, Settings shows a compact `Explore demo` row near About instead of a large
notice or switch. Tapping it opens a short explanation that synthetic and personal data are isolated,
then an explicit `Open demo` action.

While Demo workspace is active:

- every application screen shows a compact, non-dismissible workspace strip with `Use my data`;
- the exact strip geometry remains provisional until rendered at light/dark and font scale 1.5;
- a fresh user exits to Personal setup;
- an established user exits directly to their Personal Feed;
- an unsaved Demo form closes without a discard confirmation;
- `Reset demo now` exists only in Demo Settings.

A **Demo visit** survives process restarts. Explicitly returning to Personal ends the visit; the next
entry starts from the canonical fixture rather than retaining old synthetic edits.

## SMS ingestion model

SMS monitoring, routing, and import are separate:

1. The user explicitly enables monitoring and grants `RECEIVE_SMS`.
2. Every future supported-bank candidate receives a local structured outcome even when no card mapping
   exists. Raw body remains memory-only.
3. A parsed message without enough account information becomes an **Unrouted operation**.
4. Resolving every missing ledger creates a pending transaction, or attaches the SMS evidence to an
   already confirmed statement transaction.

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
3. recent processing activity;
4. `Cards and accounts`, collapsed or visually secondary when healthy;
5. optional `Check recent SMS`;
6. parser details and safe failure sharing inside an individual result or troubleshooting area.

## Implementation order

1. Separate SMS monitoring/routing/import and persist Unrouted operations without mutating the ledger.
2. Project them into Feed and add the contextual Routing resolver, including grouped transfers and
   statement-first reconciliation.
3. Rebuild SMS diagnostics as Bank SMS.
4. Add Welcome choice and bank-centred Personal setup on top of the finished Credo/SMS flow.
5. Replace the Settings demo switch with the new entry, workspace strip, exit destinations, and Demo
   visit reset policy.
6. Run the bounded OnePlus SMS dry-run manually before any real-message import.

Every UI slice requires light/dark, RU/EN, font scale 1.5, compact-height, disposable-emulator behavior,
and data-preserving `install -r` only on the physical OnePlus.
